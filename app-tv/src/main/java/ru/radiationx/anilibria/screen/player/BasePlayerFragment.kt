@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package ru.radiationx.anilibria.screen.player

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.app.VideoSupportFragmentGlueHost
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.ListRow
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.leanback.LeanbackPlayerAdapter
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.ui.presenter.cust.CustomListRowPresenter
import ru.radiationx.data.datasource.holders.PreferencesHolder
import ru.radiationx.data.player.PlayerDataSourceProvider
import ru.radiationx.quill.get

open class BasePlayerFragment : VideoSupportFragment() {

    private enum class VideoResizeMode {
        FIT,
        ZOOM,
        STRETCH,
    }

    private var videoResizeMode = VideoResizeMode.FIT
    private var lastVideoWidth: Int = 0
    private var lastVideoHeight: Int = 0

        protected var playerGlue: VideoPlayerGlue? = null
        private set

    protected var player: ExoPlayer? = null
        private set

    protected var skipsPart: PlayerSkipsPart? = null
        private set

    @SuppressLint("RestrictedApi")
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        videoResizeMode = loadResizeMode()
        initializePlayer()
        initializeRows()

        skipsPart = PlayerSkipsPart(
            parent = view as FrameLayout,
            skipButtonText = getString(R.string.player_skip),
            coroutineScope = viewLifecycleOwner.lifecycleScope,
            playerSkipsTimer = get<PreferencesHolder>().playerSkipsTimer,
            onSeek = {
                player?.seekTo(it)
            },
            onSkipShow = {
                isShowOrHideControlsOverlayOnUserInteraction = false
                hideControlsOverlay(false)
            },
            onSkipHide = {
                isShowOrHideControlsOverlayOnUserInteraction = true
            }
        )

        playerGlue?.playbackListener = object : VideoPlayerGlue.PlaybackListener {
                        override fun onUpdateProgress() {
                skipsPart?.update(player?.currentPosition ?: 0)
            }
        }

        fadeCompleteListener = object : OnFadeCompleteListener() {

            override fun onFadeInComplete() {
                super.onFadeInComplete()
                // workaround for hiding controls when user click "enter"
                isControlsOverlayAutoHideEnabled = false
                isControlsOverlayAutoHideEnabled = true
            }
        }
    }

    override fun onVideoSizeChanged(videoWidth: Int, videoHeight: Int) {
        if (videoWidth == 0 || videoHeight == 0) {
            return
        }
        lastVideoWidth = videoWidth
        lastVideoHeight = videoHeight
        applyVideoResizeMode()
    }

    private fun cycleVideoResizeMode() {
        videoResizeMode = when (videoResizeMode) {
            VideoResizeMode.FIT -> VideoResizeMode.ZOOM
            VideoResizeMode.ZOOM -> VideoResizeMode.STRETCH
            VideoResizeMode.STRETCH -> VideoResizeMode.FIT
        }
        saveResizeMode(videoResizeMode)
        applyVideoResizeMode()

        val label = when (videoResizeMode) {
            VideoResizeMode.FIT -> "По размеру"
            VideoResizeMode.ZOOM -> "Заполнить экран"
            VideoResizeMode.STRETCH -> "Растянуть"
        }
        Toast.makeText(requireContext(), "Масштаб: $label", Toast.LENGTH_SHORT).show()
    }

    private fun applyVideoResizeMode() {
        val videoWidth = lastVideoWidth
        val videoHeight = lastVideoHeight
        if (videoWidth <= 0 || videoHeight <= 0) return

        val root = view ?: return
        val screenWidth = root.width
        val screenHeight = root.height
        if (screenWidth <= 0 || screenHeight <= 0) return

        if (videoResizeMode == VideoResizeMode.FIT) {
            super.onVideoSizeChanged(videoWidth, videoHeight)
            return
        }

        val surface = surfaceView
        val params = surface.layoutParams

        when (videoResizeMode) {
            VideoResizeMode.FIT -> Unit
            VideoResizeMode.STRETCH -> {
                params.width = screenWidth
                params.height = screenHeight
            }
            VideoResizeMode.ZOOM -> {
                val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
                val screenAspect = screenWidth.toFloat() / screenHeight.toFloat()
                if (videoAspect > screenAspect) {
                    params.height = screenHeight
                    params.width = (screenHeight * videoAspect).toInt()
                } else {
                    params.width = screenWidth
                    params.height = (screenWidth / videoAspect).toInt()
                }
            }
        }

        surface.layoutParams = params
        surface.requestLayout()
    }


    private fun loadResizeMode(): VideoResizeMode {
        val index = requireContext()
            .getSharedPreferences(PLAYER_PREFS, android.content.Context.MODE_PRIVATE)
            .getInt(KEY_RESIZE_MODE, VideoResizeMode.FIT.ordinal)
        return VideoResizeMode.values().getOrElse(index) { VideoResizeMode.FIT }
    }

    private fun saveResizeMode(mode: VideoResizeMode) {
        requireContext()
            .getSharedPreferences(PLAYER_PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_RESIZE_MODE, mode.ordinal)
            .apply()
    }

    override fun onPause() {
        super.onPause()
        playerGlue?.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        skipsPart = null
        playerGlue?.playbackListener = null
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        releasePlayer()
    }

    protected open fun onCompletePlaying() {}
    protected open fun onPreparePlaying() {}
    protected open fun onPlaybackError(error: PlaybackException) {}

        private fun initializeRows() {
        val playerGlue = this.playerGlue ?: return
        val controlsRow = playerGlue.controlsRow ?: return

        val rowsPresenter = ClassPresenterSelector().apply {
            addClassPresenter(ListRow::class.java, CustomListRowPresenter())
            addClassPresenter(controlsRow.javaClass, playerGlue.playbackRowPresenter)
        }
        val rowsAdapter = ArrayObjectAdapter(rowsPresenter).apply {
            add(controlsRow)
        }

        adapter = rowsAdapter
    }

        private fun initializePlayer() {
        if (player != null) {
            throw RuntimeException("Player already initialized")
        }

        val dataSourceProvider = get<PlayerDataSourceProvider>()
        val dataSourceType = dataSourceProvider.get()
        val dataSourceFactory = DefaultDataSource.Factory(requireContext(), dataSourceType.factory)
        val mediaSourceFactory = DefaultMediaSourceFactory(requireContext()).apply {
            setDataSourceFactory(dataSourceFactory)
        }
        val player = ExoPlayer.Builder(requireContext())
            .setMediaSourceFactory(mediaSourceFactory)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {

            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                when (playbackState) {
                    Player.STATE_ENDED -> onCompletePlaying()
                    Player.STATE_READY -> onPreparePlaying()
                    Player.STATE_BUFFERING -> {
                    }

                    Player.STATE_IDLE -> {
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                this@BasePlayerFragment.onPlaybackError(error)
            }
        })


        val playerAdapter = LeanbackPlayerAdapter(requireContext(), player, 500)

        val playerGlue = VideoPlayerGlue(requireContext(), playerAdapter).apply {
            host = VideoSupportFragmentGlueHost(this@BasePlayerFragment)
            resizeListener = { cycleVideoResizeMode() }
        }

        this.player = player
        this.playerGlue = playerGlue
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    protected fun preparePlayer(url: String) {
        player?.setMediaItem(MediaItem.fromUri(Uri.parse(url)), false)
        player?.prepare()
    }

    /** Prepare a stream with provider-specific request headers (Referer/User-Agent). */
        protected fun preparePlayer(
        url: String,
        headers: Map<String, String>,
        mimeType: String? = null,
    ) {
        if (headers.isEmpty() && mimeType == null) {
            preparePlayer(url)
            return
        }
        val httpFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
        val dataSourceFactory = DefaultDataSource.Factory(requireContext(), httpFactory)
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(url))
            .apply { mimeType?.let(::setMimeType) }
            .build()
        val mediaSource = DefaultMediaSourceFactory(requireContext())
            .setDataSourceFactory(dataSourceFactory)
            .createMediaSource(mediaItem)
        player?.setMediaSource(mediaSource, false)
        player?.prepare()
    }

    protected fun hlsMimeType(): String = MimeTypes.APPLICATION_M3U8
    protected fun dashMimeType(): String = MimeTypes.APPLICATION_MPD
    protected fun mp4MimeType(): String = MimeTypes.VIDEO_MP4


    private companion object {
        const val PLAYER_PREFS = "aniru_player_preferences"
        const val KEY_RESIZE_MODE = "resize_mode"
    }
}
