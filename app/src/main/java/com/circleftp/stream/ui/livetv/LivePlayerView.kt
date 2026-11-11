package com.circleftp.stream.ui.livetv

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.circleftp.stream.player.CircleVideoPlayerFactory
import com.circleftp.stream.ui.theme.CircleRed
import kotlinx.coroutines.flow.collectLatest

/**
 * Android TV Persistent Low-Latency Live Player View.
 *
 * Architecture Highlights:
 * 1. Single persistent ExoPlayer instance across all channel hops (zero reallocation overhead).
 * 2. Tuned 1000ms - 2500ms buffering via [CircleVideoPlayerFactory.createLivePlayer].
 * 3. Surface frame retention ([PlayerView.setKeepContentOnPlayerReset]) to eliminate black flashes.
 * 4. Composite TV OSD: Real-time numeric dial overlay, transient 3.5s info banner, and quick-list drawer.
 */
@OptIn(UnstableApi::class)
@Composable
fun LivePlayerView(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Single persistent ExoPlayer instance for live channel switching
    val livePlayer = remember {
        CircleVideoPlayerFactory.createLivePlayer(context)
    }

    var isPlayerBuffering by remember { mutableStateOf(false) }

    // Synchronize player lifecycle & key interceptor state
    DisposableEffect(Unit) {
        LiveTvManager.isLivePlayerActive = true

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isPlayerBuffering = (playbackState == Player.STATE_BUFFERING)
            }

            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("LivePlayerView", "Playback exception: ${error.message}", error)
            }
        }
        livePlayer.addListener(listener)

        // Directly tune into the broadcast like an analog television
        val targetChannel = LiveTvManager.activeChannel
        if (targetChannel != null) {
            LiveTvManager.playChannel(targetChannel)
        } else {
            LiveTvManager.resumeLastChannelOrDefault(context)
        }

        onDispose {
            LiveTvManager.isLivePlayerActive = false
            LiveTvManager.closeDrawer()
            LiveTvManager.hideOsd()
            livePlayer.removeListener(listener)
            livePlayer.stop()
            livePlayer.release()
        }
    }

    // React to committed channel switches from LiveTvManager
    LaunchedEffect(livePlayer) {
        LiveTvManager.playbackRequests.collectLatest { request ->
            val mediaItem = CircleVideoPlayerFactory.buildMediaItem(
                title = request.channel.name,
                rawStreamUrl = request.streamUrl
            )
            // Re-use persistent player without teardown; resetPosition prepares the next live segment
            livePlayer.setMediaItem(mediaItem, /* resetPosition = */ true)
            livePlayer.prepare()
            livePlayer.play()
        }
    }

    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    // Intercept Back button: close drawer -> hide OSD -> double-click Back to return to Home
    BackHandler {
        if (LiveTvManager.isDrawerOpen) {
            LiveTvManager.closeDrawer()
        } else if (LiveTvManager.isOsdVisible) {
            LiveTvManager.hideOsd()
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < 2000L) {
                // Double-click Back detected: Return to Home Dashboard!
                onBack()
            } else {
                lastBackPressTime = currentTime
                LiveTvManager.showNotice("Press BACK again to return to Home")
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Persistent PlayerView surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    // Retain the last decoded video frame to avoid black screen flicker during channel hops
                    setKeepContentOnPlayerReset(true)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    this.player = livePlayer
                }
            },
            update = { view ->
                view.player = livePlayer
            },
            modifier = Modifier.fillMaxSize()
        )

        // Subtle buffering spinner in the center
        AnimatedVisibility(
            visible = LiveTvManager.isBuffering || isPlayerBuffering,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = CircleRed,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        // Top-right Numeric buffer overlay (e.g. "CH 14")
        NumericBufferOverlay(
            buffer = LiveTvManager.numericBuffer,
            modifier = Modifier.align(Alignment.TopEnd)
        )

        // Top center Notice pill (e.g. "CH 999 Not Found")
        NoticePillOverlay(
            notice = LiveTvManager.temporaryNotice,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Transient On-Screen Display (OSD Banner - 3.5s auto-hide)
        LiveTvOsdBanner(
            channel = LiveTvManager.previewChannel ?: LiveTvManager.activeChannel,
            visible = LiveTvManager.isOsdVisible,
            modifier = Modifier.align(Alignment.BottomStart)
        )

        // Slide-in Channel Quick-List Drawer (DPAD_CENTER / ENTER)
        ChannelQuickListDrawer(
            isOpen = LiveTvManager.isDrawerOpen,
            channels = LiveTvManager.channelList,
            currentChannel = LiveTvManager.activeChannel,
            onSelectChannel = { channel ->
                LiveTvManager.playChannel(channel)
                LiveTvManager.closeDrawer()
            },
            onClose = {
                LiveTvManager.closeDrawer()
            },
            modifier = Modifier.align(Alignment.CenterStart)
        )
    }
}

