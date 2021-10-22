package com.plugin.drm_video

import android.content.Context
import android.net.Uri
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.Player.REPEAT_MODE_ALL
import com.google.android.exoplayer2.Player.REPEAT_MODE_OFF

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.EventListener;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager
import com.google.android.exoplayer2.drm.DrmSessionManager
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.util.Util
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.EventChannel.EventSink
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView
import java.util.*
import kotlin.collections.HashMap
import com.google.android.exoplayer2.RendererCapabilities

import com.google.android.exoplayer2.ui.DefaultTrackNameProvider

import com.google.android.exoplayer2.ui.TrackNameProvider

import com.google.android.exoplayer2.source.TrackGroup

import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.ParametersBuilder
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride

import java.util.ArrayList

import com.google.android.exoplayer2.trackselection.MappingTrackSelector
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ext.ffmpeg.FfmpegLibrary
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.extractor.mp3.Mp3Extractor

import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
import com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
import com.google.android.exoplayer2.source.hls.DefaultHlsExtractorFactory


internal class DrmVideoPlayer(
        private val context: Context,
        private val messenger: BinaryMessenger,
        private val id: Int,
        private var params: Map<String, Any>) : PlatformView, MethodChannel.MethodCallHandler {


    private val FORMAT_SS = "ss"
    private val FORMAT_DASH = "dash"
    private val FORMAT_HLS = "hls"
    private val FORMAT_OTHER = "other"
    lateinit var trackSelector: DefaultTrackSelector

    private var view: View? = null

    private var methodChannel: MethodChannel?

    private var isInitialized = false


    private var player: SimpleExoPlayer? = null
    private var playerView: StyledPlayerView?

    private var eventChannel: EventChannel?

    private val eventSink: QueuingEventSink = QueuingEventSink()


    override fun getView(): View {
        return view!!
    }

    init {
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.activity_main, null)
        }
        playerView = view!!.findViewById(R.id.video_view)
        eventChannel = EventChannel(messenger, "drmvideo_events$id")
        methodChannel = MethodChannel(messenger, "drmvideo_$id")
        methodChannel?.setMethodCallHandler(this)

        initializePlayer();

    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "seekTo" -> {
                seekTo(call, result)
            }
            "getAudios" -> {
                val audios  = getAudios()
                result.success(audios)
            }
            "getSubtitles" -> {
                val subtitles = getSubtitles()
                result.success(subtitles)
            }
            "changeFontSize" -> {
                val fontSize = call.arguments as Double
                changeFontSize(fontSize)
            }
            "changeUrl" -> {
                val url = call.arguments as String
                val newParams = HashMap(params)
                newParams["videoUrl"] = url
                params = newParams
                isInitialized = false
                eventChannel = null
                player?.stop()
                player?.release()
                initializePlayer()
            }
            "play" -> {
                play(result);
            }
            "pause" -> {
                pause(result);
            }
            "setAudio" -> {
                setAudioByIndex(call.arguments as Int)
            }
            "setSubtitle" -> {
                setSubtitleByIndex(call.arguments as Int)
            }
            "setVolume" -> {
                setVolume(call);
            }
            "setLooping" -> {
                setLooping(call);
            }
            "setPlaybackSpeed" -> {
                setPlaybackSpeed(call);
            }
            "getPosition" -> {
                getPosition(result)
            }
            "dispose" -> {
                dispose();
            }
        }
    }


    private fun setSubtitleByIndex(index: Int) {
        val mappedTrackInfo = trackSelector.currentMappedTrackInfo
        var trackCount = 0
        if (mappedTrackInfo != null) {
            trackCount = mappedTrackInfo.rendererCount
        }
        var audioIndex = 0
        for (i in 0 until trackCount) {
            if (mappedTrackInfo!!.getRendererType(i) != C.TRACK_TYPE_TEXT) {
                continue
            }
            val trackGroupArray = mappedTrackInfo.getTrackGroups(i)
            for (j in 0 until trackGroupArray.length) {
                val group = trackGroupArray[j]
                for (k in 0 until group.length) {
                    if (audioIndex == index) {
                        val builder = trackSelector.parameters
                                .buildUpon()
                        builder.clearSelectionOverrides(i).setRendererDisabled(i, false)
                        val tracks = intArrayOf(k)
                        val override = SelectionOverride(
                                j, *tracks)
                        builder.setSelectionOverride(i, mappedTrackInfo.getTrackGroups(i), override)
                        trackSelector.setParameters(builder)
                        return
                    }
                    audioIndex++
                }
            }
        }
    }

    private fun getSubtitles(): List<String> {
        val mappedTrackInfo: MappedTrackInfo = trackSelector.currentMappedTrackInfo ?: return ArrayList()
        val audios = ArrayList<String>()
        for (i in 0 until mappedTrackInfo.rendererCount) {
            if (mappedTrackInfo.getRendererType(i) != C.TRACK_TYPE_TEXT) continue
            val trackGroupArray = mappedTrackInfo.getTrackGroups(i)
            for (j in 0 until trackGroupArray.length) {
                val group = trackGroupArray[j]
                val provider: TrackNameProvider = DefaultTrackNameProvider(context.resources)
                for (k in 0 until group.length) {
                    if (mappedTrackInfo.getTrackSupport(i, j, k) and 7
                            == RendererCapabilities.FORMAT_HANDLED) {
                        //trackSelector.setParameters(builder);
                        audios.add(provider.getTrackName(group.getFormat(k)))
                    }
                }
            }
        }
        return audios
    }

    private fun getAudios() : List<String> {
        val mappedTrackInfo: MappedTrackInfo = trackSelector.currentMappedTrackInfo ?: return ArrayList()
        val audios = ArrayList<String>()
        for (i in 0 until mappedTrackInfo.rendererCount) {
            if (mappedTrackInfo.getRendererType(i) != C.TRACK_TYPE_AUDIO) continue
            val trackGroupArray = mappedTrackInfo.getTrackGroups(i)
            for (j in 0 until trackGroupArray.length) {
                val group = trackGroupArray[j]
                val provider: TrackNameProvider = DefaultTrackNameProvider(context.resources)
                for (k in 0 until group.length) {
                    if (mappedTrackInfo.getTrackSupport(i, j, k) and 7
                            == RendererCapabilities.FORMAT_HANDLED) {
                        //trackSelector.setParameters(builder);
                        audios.add(provider.getTrackName(group.getFormat(k)))
                    }
                }
            }
        }

        return audios


    }
    private fun setAudioByIndex(index: Int) {
        val mappedTrackInfo = trackSelector.currentMappedTrackInfo
        var trackCount = 0
        if (mappedTrackInfo != null) {
            trackCount = mappedTrackInfo.rendererCount
        }
        var audioIndex = 0
        for (i in 0 until trackCount) {
            if (mappedTrackInfo!!.getRendererType(i) != C.TRACK_TYPE_AUDIO) {
                continue
            }
            val trackGroupArray = mappedTrackInfo.getTrackGroups(i)
            for (j in 0 until trackGroupArray.length) {
                val group = trackGroupArray[j]
                for (k in 0 until group.length) {
                    if (audioIndex == index) {
                        val builder = trackSelector.parameters
                                .buildUpon()
                        builder.clearSelectionOverrides(i).setRendererDisabled(i, false)
                        val tracks = intArrayOf(k)
                        val override = SelectionOverride(
                                j, *tracks)
                        builder.setSelectionOverride(i, mappedTrackInfo.getTrackGroups(i), override)
                        trackSelector.setParameters(builder)
                        return
                    }
                    audioIndex++
                }
            }
        }
    }

    private fun getPosition(result: MethodChannel.Result) {
        result.success(player?.currentPosition)
    }

    private fun setPlaybackSpeed(call: MethodCall) {
        val value = call.arguments as Double
        // We do not need to consider pitch and skipSilence for now as we do not handle them and
        // therefore never diverge from the default values.
        val playbackParameters = PlaybackParameters(value.toFloat())
        player?.setPlaybackParameters(playbackParameters)
    }

    private fun setLooping(call: MethodCall) {
        val value = call.arguments as Boolean
        player?.repeatMode = if (value) REPEAT_MODE_ALL else REPEAT_MODE_OFF
    }


    private fun seekTo(call: MethodCall, result: MethodChannel.Result) {
        val position = call.arguments as Integer
        player?.seekTo(position.toLong())
        result.success(null);
    }

    private fun play(result: MethodChannel.Result) {
        player?.playWhenReady = true
        player?.play()
        result.success(null);
    }


    private fun pause(result: MethodChannel.Result) {
        player?.playWhenReady = false
        player?.pause()
        result.success(null);
    }


    private fun setVolume(call: MethodCall) {
        val volume = call.arguments as Double
        val bracketedValue = Math.max(0.0, Math.min(1.0, volume)).toFloat()
        player?.volume = bracketedValue
    }

    private fun initializePlayer() {

        var videoUrl = "";
        var drmLicenseUrl = "";
        var formatHint: String? = null
        var fontSize : Double = 20.0
        if (params.containsKey("videoUrl")) {
            videoUrl = params["videoUrl"] as String;
        }
        if(params.containsKey("initialFontSize")){
            fontSize = params["initialFontSize"] as Double
        }

        if (params.containsKey("drmLicenseUrl")) {
            drmLicenseUrl = params["drmLicenseUrl"] as String;
        }


        if (params.containsKey("formatHint")) {
            formatHint = params["formatHint"] as String;
        }

        trackSelector = DefaultTrackSelector(context)
        trackSelector.setParameters(
                trackSelector.buildUponParameters().setMaxVideoSizeSd()

        )
        val defaultRenderersFactory = DefaultRenderersFactory(context)
        defaultRenderersFactory
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        var drmSessionManager: DrmSessionManager? = null;


        if (drmLicenseUrl.isNotEmpty()) {
            val drmCallback = HttpMediaDrmCallback(drmLicenseUrl, DefaultHttpDataSourceFactory())

            drmSessionManager = DefaultDrmSessionManager.Builder().build(drmCallback)
        }
        val ffmpegAvailable = FfmpegLibrary.isAvailable()
        Log.i("isAvailable", ffmpegAvailable.toString())
        player = SimpleExoPlayer.Builder(context,defaultRenderersFactory)
                .setTrackSelector(trackSelector)
                .build()

        val uri: Uri = Uri.parse(drmLicenseUrl);

        val dataSourceFactory: DataSource.Factory = if (isHTTP(uri)) {
            DefaultHttpDataSourceFactory(
                    "ExoPlayer",
                    null,
                    DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
                    DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS,
                    true
            )
        } else {
            DefaultDataSourceFactory(context, "ExoPlayer")
        }


        val mediaSource: MediaSource = buildMediaSource(Uri.parse(videoUrl), dataSourceFactory, formatHint, context, drmSessionManager)!!

        player?.setMediaSource(mediaSource)
        playerView?.player = player

//        player?.playWhenReady = autoPlay
        player?.prepare()
        playerView?.subtitleView?.setApplyEmbeddedStyles(false)
        playerView?.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_PX,fontSize.toFloat())
        setUpVideo()
    }

    private fun changeFontSize(fontSize : Double){
        playerView?.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_PX,fontSize.toFloat())

    }

    private fun buildMediaSource(
            uri: Uri, mediaDataSourceFactory: DataSource.Factory, formatHint: String?, context: Context, drmSessionManager: DrmSessionManager?): MediaSource? {
        val type: Int = if (formatHint == null || formatHint.isEmpty()) {
            Util.inferContentType(uri.lastPathSegment!!)
        } else {
            when (formatHint) {
                FORMAT_SS -> C.TYPE_SS
                FORMAT_DASH -> C.TYPE_DASH
                FORMAT_HLS -> C.TYPE_HLS
                FORMAT_OTHER -> C.TYPE_OTHER
                else -> -1
            }
        }
        return when (type) {
            C.TYPE_SS -> SsMediaSource.Factory(
                    DefaultSsChunkSource.Factory(mediaDataSourceFactory),
                    DefaultDataSourceFactory(context, null, mediaDataSourceFactory))
                    .createMediaSource(MediaItem.fromUri(uri))
            C.TYPE_DASH -> DashMediaSource.Factory(
                    DefaultDashChunkSource.Factory(mediaDataSourceFactory),
                    DefaultDataSourceFactory(context, null, mediaDataSourceFactory))
                    .createMediaSource(MediaItem.fromUri(uri))
            C.TYPE_HLS -> HlsMediaSource.Factory(mediaDataSourceFactory).setExtractorFactory(DefaultHlsExtractorFactory(FLAG_ALLOW_NON_IDR_KEYFRAMES,true))
                    .createMediaSource(MediaItem.fromUri(uri))
            C.TYPE_OTHER -> ProgressiveMediaSource.Factory(mediaDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(uri))
            else -> {
                throw IllegalStateException("Unsupported type: $type")
            }
        }
    }


    private fun setUpVideo() {

        eventChannel?.setStreamHandler(
                object : EventChannel.StreamHandler {
                    override fun onListen(args: Any?, events: EventSink?) {
                        eventSink.setDelegate(events)
                    }

                    override fun onCancel(args: Any?) {
                        eventSink.setDelegate(null)
                    }
//                    override fun onListen(o: Any, sink: EventSink) {
//
//                        eventSink.setDelegate(sink)
//                    }
//
//                    override fun onCancel(o: Any) {
//                        eventSink.setDelegate(null)
//                    }
                })

        player?.addListener(
                object : Player.EventListener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_BUFFERING) {
                            sendBufferingUpdate()
                        } else if (playbackState == Player.STATE_READY) {
                            if (!isInitialized) {
                                isInitialized = true
                                sendInitialized()
                            }
                        } else if (playbackState == Player.STATE_ENDED) {
                            val event: HashMap<String, Any> = HashMap()
                            event["event"] = "completed"
                            eventSink.success(event)
                        }
                    }

                    override fun onPlayerError(error: ExoPlaybackException) {
                        eventSink.error("VideoError", "Video player had error $error", null)
                    }
                })
    }

    private fun isHTTP(uri: Uri?): Boolean {
        if (uri == null || uri.scheme == null) {
            return false
        }
        val scheme = uri.scheme
        return scheme == "http" || scheme == "https"
    }


    private fun sendBufferingUpdate() {
        val event: HashMap<String, Any> = HashMap()
        event["event"] = "bufferingUpdate"
        val range: List<Number?> = listOf(0, player?.bufferedPosition)
        // iOS supports a list of buffered ranges, so here is a list with a single range.
        event["values"] = Collections.singletonList(range)
        eventSink.success(event)
    }


    override fun dispose() {
        if (isInitialized) {
            player?.stop()
        }
        eventChannel?.setStreamHandler(null)

        player?.release()
    }


    private fun sendInitialized() {
        if (isInitialized) {
            val event: HashMap<String, Any> = HashMap()
            event.put("event", "initialized")
            event.put("duration", player!!.duration)
            event.put("audios",(getAudios()))
            event.put("subtitles",(getSubtitles()))
            if (player?.videoFormat != null) {
                val videoFormat: Format? = player?.videoFormat
                var width: Int = videoFormat!!.width
                var height: Int = videoFormat.height
                val rotationDegrees: Int = videoFormat.rotationDegrees
                // Switch the width/height if video was taken in portrait mode
                if (rotationDegrees == 90 || rotationDegrees == 270) {
                    width = player?.videoFormat!!.height
                    height = player?.videoFormat!!.width
                }
                event.put("width", width)
                event.put("height", height)
            }
            eventSink.success(event)
        }
    }

}