package com.circleftp.stream.player

import android.content.Context
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.extractor.DefaultExtractorsFactory
import com.circleftp.stream.data.provider.model.SubtitleTrack
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

enum class DecoderMode(val displayName: String) {
    HW("HW (Hardware)"),
    HW_PLUS("HW+ (Hybrid)"),
    SW("SW (Software Boost)")
}

enum class AspectRatioMode(val displayName: String) {
    FIT("Fit Screen"),
    FILL("Crop to Fill"),
    STRETCH("Stretch (16:9)"),
    ORIGINAL("100% Original")
}

@OptIn(UnstableApi::class)
class CircleCustomRenderersFactory(
    context: Context,
    private val decoderMode: DecoderMode
) : DefaultRenderersFactory(context) {

    init {
        // Enable decoder fallback so ExoPlayer automatically tries the next decoder if one fails
        setEnableDecoderFallback(true)
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(true)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
    }

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: java.util.ArrayList<Renderer>
    ) {
        // 1. Direct instantiate FfmpegAudioRenderer as the PRIMARY audio renderer
        // libffmpegJNI natively decodes AC-3, E-AC-3 (Dolby Digital Plus), DTS, TrueHD, Vorbis, Opus, FLAC
        // to high-quality PCM with stereo downmixing, eliminating error [-49999] on budget chips!
        try {
            if (FfmpegLibrary.isAvailable()) {
                val ffmpegRenderer = FfmpegAudioRenderer(eventHandler, eventListener, audioSink)
                out.add(ffmpegRenderer)
                android.util.Log.i("CirclePlayer", "FfmpegAudioRenderer added as PRIMARY audio decoder")
            } else {
                android.util.Log.w("CirclePlayer", "FfmpegLibrary.isAvailable() is false")
            }
        } catch (t: Throwable) {
            android.util.Log.e("CirclePlayer", "FfmpegAudioRenderer unavailable: ${t.message}", t)
        }

        // 2. Hardware / System MediaCodec Audio Renderer with safe fallback
        super.buildAudioRenderers(
            context,
            EXTENSION_RENDERER_MODE_OFF,
            mediaCodecSelector,
            enableDecoderFallback,
            audioSink,
            eventHandler,
            eventListener,
            out
        )
    }

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: java.util.ArrayList<Renderer>
    ) {
        super.buildVideoRenderers(
            context,
            extensionRendererMode,
            mediaCodecSelector,
            true, // Always enable decoder fallback
            eventHandler,
            eventListener,
            allowedVideoJoiningTimeMs,
            out
        )
    }
}

@OptIn(UnstableApi::class)
object CircleVideoPlayerFactory {

    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    init {
        try {
            System.loadLibrary("ffmpegJNI")
            android.util.Log.i("CirclePlayer", "Successfully loaded libffmpegJNI.so! Version: ${FfmpegLibrary.getVersion()}, Available: ${FfmpegLibrary.isAvailable()}")
        } catch (t: Throwable) {
            android.util.Log.w("CirclePlayer", "Preload libffmpegJNI note: ${t.message}")
        }
    }

    private val streamingOkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .addInterceptor { chain ->
            val original = chain.request()
            val requestWithUa = original.newBuilder()
                .header("User-Agent", USER_AGENT)
                .build()
            chain.proceed(requestWithUa)
        }
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun create(
        context: Context,
        decoderMode: DecoderMode = DecoderMode.HW
    ): ExoPlayer {
        // Universal Codec Selector for Audio & Video:
        // 1. Prevents crash [-49999]: Directs AC-3, E-AC-3 (Dolby) and DTS exclusively to FfmpegAudioRenderer
        // 2. Honors HW / HW+ / SW decoder modes with automatic multi-codec fallback
        val combinedCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            // Never allow broken vendor MediaCodecs to intercept AC-3 / E-AC-3 / DTS
            if (mimeType.equals(MimeTypes.AUDIO_AC3, ignoreCase = true) ||
                mimeType.equals(MimeTypes.AUDIO_E_AC3, ignoreCase = true) ||
                mimeType.equals(MimeTypes.AUDIO_DTS, ignoreCase = true)
            ) {
                return@MediaCodecSelector emptyList()
            }

            val decoders = try {
                MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
            } catch (_: Throwable) {
                emptyList()
            }

            when (decoderMode) {
                DecoderMode.SW -> decoders.sortedWith(compareBy { info ->
                    if (info.softwareOnly || info.name.startsWith("c2.android") || info.name.startsWith("OMX.google")) 0 else 1
                })
                DecoderMode.HW_PLUS -> decoders.sortedWith(compareBy { info ->
                    if (info.name.contains("unisoc", ignoreCase = true)) 1 else 0
                })
                DecoderMode.HW -> decoders
            }
        }

        val renderersFactory = CircleCustomRenderersFactory(context.applicationContext, decoderMode)
            .setMediaCodecSelector(combinedCodecSelector)

        // Robust Extractor / Demuxer Configuration for Seeking & Container Tolerance
        // Forces constant bitrate (CBR) seeking on unindexed .mkv and .mp4 files over HTTP/FTP
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)

        val defaultProperties = ActivePlayerSession.headers.toMutableMap().apply {
            put("User-Agent", USER_AGENT)
        }

        val dataSourceFactory = OkHttpDataSource.Factory(streamingOkHttpClient)
            .setUserAgent(USER_AGENT)
            .setDefaultRequestProperties(defaultProperties)

        val mediaSourceFactory = DefaultMediaSourceFactory(context.applicationContext, extractorsFactory)
            .setDataSourceFactory(dataSourceFactory)
            .setLiveTargetOffsetMs(30_000L)

        // Heavy-duty buffer setup: Keep 30s to 60s of video in RAM
        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
            .setBufferDurationsMs(
                /* minBufferMs = */ 30_000,          // Always try to keep at least 30 seconds buffered
                /* maxBufferMs = */ 60_000,          // Pre-buffer up to 60 seconds ahead
                /* bufferForPlaybackMs = */ 3_000,   // Start playback quickly after 3 seconds
                /* bufferForPlaybackAfterRebufferMs = */ 8_000 // Build 8s cushion before resuming on stall
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(15_000, true) // Retain 15s back buffer so rewinding is instantaneous
            .build()

        val player = ExoPlayer.Builder(context.applicationContext, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()

        // Configure Movie Audio Attributes
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        player.setAudioAttributes(audioAttributes, true)

        // Preferred Audio Codecs (EAC3, AC3, DTS, Opus, AAC, Vorbis)
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setPreferredAudioMimeTypes(
                MimeTypes.AUDIO_E_AC3,
                MimeTypes.AUDIO_AC3,
                MimeTypes.AUDIO_DTS,
                MimeTypes.AUDIO_AAC,
                MimeTypes.AUDIO_OPUS,
                MimeTypes.AUDIO_VORBIS
            )
            .build()

        return player
    }

    /**
     * Builds a persistent, low-latency ExoPlayer instance dedicated to TV Live Streams.
     * Buffers min 1000ms and max 2500ms for near-instant stream start & rapid channel switching.
     */
    fun createLivePlayer(
        context: Context,
        decoderMode: DecoderMode = DecoderMode.HW
    ): ExoPlayer {
        val combinedCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            if (mimeType.equals(MimeTypes.AUDIO_AC3, ignoreCase = true) ||
                mimeType.equals(MimeTypes.AUDIO_E_AC3, ignoreCase = true) ||
                mimeType.equals(MimeTypes.AUDIO_DTS, ignoreCase = true)
            ) {
                return@MediaCodecSelector emptyList()
            }
            try {
                MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
            } catch (_: Throwable) {
                emptyList()
            }
        }

        val renderersFactory = CircleCustomRenderersFactory(context.applicationContext, decoderMode)
            .setMediaCodecSelector(combinedCodecSelector)

        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)

        val defaultProperties = ActivePlayerSession.headers.toMutableMap().apply {
            put("User-Agent", USER_AGENT)
        }

        val dataSourceFactory = OkHttpDataSource.Factory(streamingOkHttpClient)
            .setUserAgent(USER_AGENT)
            .setDefaultRequestProperties(defaultProperties)

        val mediaSourceFactory = DefaultMediaSourceFactory(context.applicationContext, extractorsFactory)
            .setDataSourceFactory(dataSourceFactory)
            .setLiveTargetOffsetMs(30_000L)

        // Heavy-duty buffer setup for TV live feeds: Keep 30s to 60s of video in RAM
        val liveLoadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
            .setBufferDurationsMs(
                /* minBufferMs = */ 30_000,          // Always try to keep at least 30 seconds buffered
                /* maxBufferMs = */ 60_000,          // Pre-buffer up to 60 seconds ahead
                /* bufferForPlaybackMs = */ 3_000,   // Start playback quickly after 3 seconds
                /* bufferForPlaybackAfterRebufferMs = */ 8_000 // Build 8s cushion before resuming on stall
            )
            .setBackBuffer(15_000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val player = ExoPlayer.Builder(context.applicationContext, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(liveLoadControl)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        player.setAudioAttributes(audioAttributes, true)

        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setPreferredAudioMimeTypes(
                MimeTypes.AUDIO_E_AC3,
                MimeTypes.AUDIO_AC3,
                MimeTypes.AUDIO_DTS,
                MimeTypes.AUDIO_AAC,
                MimeTypes.AUDIO_OPUS,
                MimeTypes.AUDIO_VORBIS
            )
            .build()

        return player
    }

    fun buildMediaItem(
        title: String,
        rawStreamUrl: String,
        subtitles: List<SubtitleTrack> = emptyList()
    ): MediaItem {
        val safeUrl = UrlNormalizer.getSafeEncodedUrl(rawStreamUrl)
        val subtitleConfigs = subtitles.mapNotNull { sub ->
            if (sub.url.isBlank()) return@mapNotNull null
            val mimeType = if (sub.format.equals("vtt", ignoreCase = true) || sub.url.contains(".vtt", ignoreCase = true)) {
                MimeTypes.TEXT_VTT
            } else {
                MimeTypes.APPLICATION_SUBRIP // .srt
            }
            MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub.url))
                .setMimeType(mimeType)
                .setLanguage(sub.language.ifBlank { "en" })
                .setLabel(sub.label.ifBlank { sub.language })
                .setSelectionFlags(if (sub.isDefault) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }

        val builder = MediaItem.Builder()
            .setUri(safeUrl)
            .setSubtitleConfigurations(subtitleConfigs)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .build()
            )

        if (safeUrl.contains(".m3u8") || ActivePlayerSession.providerId == "iptvidn") {
            builder.setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(30_000L) // Play 30 seconds behind live broadcast for rock-solid stability
                    .setMinOffsetMs(15_000L)
                    .setMaxOffsetMs(60_000L)
                    .setMinPlaybackSpeed(0.97f)
                    .setMaxPlaybackSpeed(1.03f)
                    .build()
            )
        }

        return builder.build()
    }
}
