package com.circleftp.stream.ui.livetv

import android.content.Context
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.circleftp.stream.data.iptv.IptvCatalog
import com.circleftp.stream.data.iptv.IptvStreamResolver
import com.circleftp.stream.data.iptv.TvChannel
import com.circleftp.stream.data.settings.AppSettings
import com.circleftp.stream.player.ActivePlayerSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Data payload dispatched when a channel stream is resolved and ready for playback.
 */
data class StreamPlaybackRequest(
    val channel: TvChannel,
    val streamUrl: String,
    val headers: Map<String, String> = emptyMap()
)

/**
 * High-performance, persistent Android TV IPTV Channel Manager.
 * Orchestrates analog-style debounced channel hopping (350ms), numeric channel buffers (800ms),
 * quick-list drawer overlays, and transient OSD visibility timers.
 */
object LiveTvManager {

    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Channel Roster
    var channelList: List<TvChannel> by mutableStateOf(emptyList())
        private set

    var currentIndex: Int by mutableIntStateOf(0)
    var lastIndex: Int by mutableIntStateOf(0)

    // Current visual preview (updates instantaneously for 0-latency OSD feedback)
    var previewChannel: TvChannel? by mutableStateOf(null)
        private set

    // Current active streaming channel
    var activeChannel: TvChannel? by mutableStateOf(null)
        private set

    // Screen and Overlay states
    var isLivePlayerActive: Boolean by mutableStateOf(false)
    var isDrawerOpen: Boolean by mutableStateOf(false)
    var isOsdVisible: Boolean by mutableStateOf(false)
        private set

    // Numeric buffer for direct digit dial (e.g. "1" -> "2" displays "CH 12")
    var numericBuffer: String by mutableStateOf("")
        private set

    // Stream status states
    var isBuffering: Boolean by mutableStateOf(false)
    var streamError: String? by mutableStateOf(null)
    var temporaryNotice: String? by mutableStateOf(null)

    // Stream dispatch flow to persistent ExoPlayer
    private val _playbackRequests = MutableSharedFlow<StreamPlaybackRequest>(replay = 1)
    val playbackRequests = _playbackRequests.asSharedFlow()

    // Coroutine Jobs for debouncing and timers
    private var switchDebounceJob: Job? = null
    private var numericCommitJob: Job? = null
    private var osdTimerJob: Job? = null
    private var noticeTimerJob: Job? = null
    private var appContext: Context? = null

    init {
        initializeChannels()
    }

    /**
     * Directly tunes to the last viewed channel (or channel 1 by default),
     * providing an analog TV experience where the broadcast starts immediately.
     */
    fun resumeLastChannelOrDefault(context: Context) {
        appContext = context.applicationContext
        if (channelList.isEmpty()) {
            initializeChannels()
        }
        if (channelList.isEmpty()) return

        val savedId = AppSettings.getInstance(context).lastWatchedChannelId
        val targetIdx = if (!savedId.isNullOrBlank()) {
            channelList.indexOfFirst { it.id.equals(savedId, ignoreCase = true) }.takeIf { it != -1 } ?: 0
        } else {
            0
        }
        currentIndex = targetIdx
        commitChannel(targetIdx)
    }

    /**
     * Initializes the TV channel lineup from the IPTV catalog with 1-based channel numbering.
     */
    fun initializeChannels() {
        val active = IptvCatalog.ACTIVE_CHANNELS
        val mapped = active.mapIndexed { index, iptvChannel ->
            TvChannel.fromIptvChannel(number = index + 1, iptv = iptvChannel)
        }
        channelList = mapped
        if (mapped.isNotEmpty()) {
            if (previewChannel == null) previewChannel = mapped[0]
            if (activeChannel == null) activeChannel = mapped[0]
        }
    }

    /**
     * Initiates immediate playback of a specific channel (e.g. from catalog grid).
     */
    fun playChannel(channel: TvChannel) {
        val idx = channelList.indexOfFirst { it.id == channel.id }
        if (idx != -1) {
            currentIndex = idx
            commitChannel(idx)
        } else {
            activeChannel = channel
            previewChannel = channel
            showOsd()
            resolveAndDispatch(channel)
        }
    }

    fun playChannelById(channelId: String) {
        val idx = channelList.indexOfFirst { it.id.equals(channelId, ignoreCase = true) }
        if (idx != -1) {
            currentIndex = idx
            commitChannel(idx)
        }
    }

    // =========================================================================
    // TASK 2: Channel Switch Manager & Debouncer (350ms)
    // =========================================================================

    /**
     * Advances to the next channel (+1).
     * Updates the OSD preview immediately without restarting the stream on rapid taps.
     */
    fun channelNext() {
        if (channelList.isEmpty()) return
        clearNumericBufferInternal()

        val nextIndex = (currentIndex + 1) % channelList.size
        updatePreviewAndDebounce(nextIndex)
    }

    /**
     * Moves to the previous channel (-1).
     * Updates the OSD preview immediately without restarting the stream on rapid taps.
     */
    fun channelPrevious() {
        if (channelList.isEmpty()) return
        clearNumericBufferInternal()

        val prevIndex = if (currentIndex > 0) currentIndex - 1 else channelList.size - 1
        updatePreviewAndDebounce(prevIndex)
    }

    private fun updatePreviewAndDebounce(newIndex: Int) {
        currentIndex = newIndex
        previewChannel = channelList.getOrNull(newIndex)
        showOsd()

        switchDebounceJob?.cancel()
        switchDebounceJob = managerScope.launch {
            delay(350) // 350ms Debounced Channel Switching
            commitChannel(currentIndex)
        }
    }

    // =========================================================================
    // TASK 2: Numeric Buffer (800ms)
    // =========================================================================

    /**
     * Appends a remote control digit (0-9).
     * Displays "CH XX" in real time and automatically commits after 800ms of inactivity.
     */
    fun onDigitEntered(digit: Char) {
        if (channelList.isEmpty()) return
        switchDebounceJob?.cancel() // Cancel any pending arrow debounce

        if (numericBuffer.length >= 4) {
            numericBuffer = ""
        }
        numericBuffer += digit
        showOsd()

        numericCommitJob?.cancel()
        numericCommitJob = managerScope.launch {
            delay(800) // 800ms numeric inactivity timeout
            commitNumericBuffer()
        }
    }

    /**
     * Immediately commits the currently buffered channel number.
     */
    fun commitNumericBuffer() {
        numericCommitJob?.cancel()
        val buffer = numericBuffer
        numericBuffer = ""
        if (buffer.isBlank()) return

        val targetNumber = buffer.toIntOrNull() ?: return
        val targetIndex = channelList.indexOfFirst { it.channelNumber == targetNumber }

        if (targetIndex != -1) {
            currentIndex = targetIndex
            commitChannel(targetIndex)
        } else {
            showNotice("CH $targetNumber Not Found")
        }
    }

    private fun clearNumericBufferInternal() {
        numericCommitJob?.cancel()
        numericBuffer = ""
    }

    // =========================================================================
    // Recall Previous Channel (Blue / Last Channel Key)
    // =========================================================================

    fun jumpToLastChannel() {
        if (channelList.isEmpty()) return
        clearNumericBufferInternal()
        switchDebounceJob?.cancel()

        if (lastIndex in channelList.indices && lastIndex != currentIndex) {
            val target = lastIndex
            currentIndex = target
            commitChannel(target)
        }
    }

    // =========================================================================
    // Channel Commitment & Stream Resolution
    // =========================================================================

    /**
     * Commits switching to the channel at index [targetIndex], recording [lastIndex] for Recall.
     */
    fun commitChannel(targetIndex: Int) {
        if (targetIndex !in channelList.indices) return
        val channel = channelList[targetIndex]

        if (activeChannel != null && activeChannel?.id != channel.id) {
            val oldIdx = channelList.indexOfFirst { it.id == activeChannel?.id }
            if (oldIdx != -1) {
                lastIndex = oldIdx
            }
        }

        activeChannel = channel
        previewChannel = channel
        showOsd()

        appContext?.let { ctx ->
            AppSettings.getInstance(ctx).lastWatchedChannelId = channel.id
        }

        resolveAndDispatch(channel)
    }

    private fun resolveAndDispatch(channel: TvChannel) {
        switchDebounceJob?.cancel()
        isBuffering = true
        streamError = null

        managerScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                IptvStreamResolver.resolveStream(channel.id)
            }
            val streamUrl = resolved?.primaryStreamUrl
            if (!streamUrl.isNullOrBlank()) {
                ActivePlayerSession.set(
                    mediaId = "iptv_${channel.id}",
                    title = channel.name,
                    streamUrl = streamUrl,
                    isSeries = false,
                    providerId = "iptvidn",
                    headers = resolved.headers
                )
                _playbackRequests.emit(
                    StreamPlaybackRequest(
                        channel = channel,
                        streamUrl = streamUrl,
                        headers = resolved.headers
                    )
                )
                isBuffering = false
            } else {
                isBuffering = false
                streamError = "Channel temporarily offline"
                showNotice("Channel offline at source")
            }
        }
    }

    // =========================================================================
    // Drawer Overlay & Transient OSD (3.5s auto-hide)
    // =========================================================================

    fun toggleDrawer() {
        if (numericBuffer.isNotEmpty()) {
            commitNumericBuffer()
            return
        }
        isDrawerOpen = !isDrawerOpen
        if (isDrawerOpen) {
            hideOsd()
        } else {
            showOsd()
        }
    }

    fun closeDrawer() {
        isDrawerOpen = false
    }

    fun showOsd(durationMs: Long = 3500L) {
        isOsdVisible = true
        osdTimerJob?.cancel()
        osdTimerJob = managerScope.launch {
            delay(durationMs)
            isOsdVisible = false
        }
    }

    fun hideOsd() {
        osdTimerJob?.cancel()
        isOsdVisible = false
    }

    fun showNotice(message: String) {
        temporaryNotice = message
        showOsd(3000L)
        noticeTimerJob?.cancel()
        noticeTimerJob = managerScope.launch {
            delay(3000L)
            temporaryNotice = null
        }
    }

    // =========================================================================
    // TASK 1: TV Remote Key Handler
    // =========================================================================

    /**
     * Intercepts Android TV remote events when the live player screen is active.
     * Returns true if the key event was consumed.
     */
    fun handleKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!isLivePlayerActive) return false

        // Volume keys pass through to system audio
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
        ) {
            return false
        }

        // When the quick-list drawer is open, let DPAD events navigate list items
        if (isDrawerOpen) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                isDrawerOpen = false
                return true
            }
            return false
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                channelNext()
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                channelPrevious()
                true
            }
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> { onDigitEntered('0'); true }
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> { onDigitEntered('1'); true }
            KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> { onDigitEntered('2'); true }
            KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> { onDigitEntered('3'); true }
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> { onDigitEntered('4'); true }
            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> { onDigitEntered('5'); true }
            KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> { onDigitEntered('6'); true }
            KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> { onDigitEntered('7'); true }
            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> { onDigitEntered('8'); true }
            KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> { onDigitEntered('9'); true }

            KeyEvent.KEYCODE_PROG_BLUE, KeyEvent.KEYCODE_LAST_CHANNEL -> {
                jumpToLastChannel()
                true
            }

            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (numericBuffer.isNotEmpty()) {
                    commitNumericBuffer()
                } else {
                    toggleDrawer()
                }
                true
            }

            KeyEvent.KEYCODE_BACK -> {
                if (isDrawerOpen) {
                    isDrawerOpen = false
                    true
                } else if (isOsdVisible) {
                    hideOsd()
                    true
                } else {
                    // Let activity / NavHost pop back to home grid
                    false
                }
            }

            else -> false
        }
    }
}

