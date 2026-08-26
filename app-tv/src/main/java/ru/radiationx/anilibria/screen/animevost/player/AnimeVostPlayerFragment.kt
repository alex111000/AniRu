@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package ru.radiationx.anilibria.screen.animevost.player

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.animevost.sdk.model.AnimeEpisode
import com.animevost.sdk.model.VideoSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.animevost.AnimeVostHistoryRepository
import ru.radiationx.anilibria.animevost.AnimeVostRepository
import ru.radiationx.anilibria.common.fragment.GuidedRouter
import ru.radiationx.anilibria.screen.AnimeVostEpisodesGuidedScreen
import ru.radiationx.anilibria.screen.player.BasePlayerFragment
import ru.radiationx.anilibria.screen.player.VideoPlayerGlue
import ru.radiationx.data.datasource.holders.PreferencesHolder
import ru.radiationx.data.entity.common.PlayerQuality
import ru.radiationx.quill.get

class AnimeVostPlayerFragment : BasePlayerFragment() {

    companion object {
        private const val ARG_ANIME_URL = "animevost_anime_url"
        private const val ARG_VIDEO_ID = "animevost_video_id"
        private const val ARG_EPISODE_NAME = "animevost_episode_name"
        private const val PLAYBACK_START_TIMEOUT_MS = 12_000L
        private const val PROGRESS_SAVE_INTERVAL_MS = 10_000L
        private const val RESUME_END_GUARD_MS = 30_000L

        fun newInstance(
            animeUrl: String,
            videoId: String,
            episodeName: String,
        ) = AnimeVostPlayerFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_ANIME_URL, animeUrl)
                putString(ARG_VIDEO_ID, videoId)
                putString(ARG_EPISODE_NAME, episodeName)
            }
        }
    }

    private data class PlaybackCandidate(val source: VideoSource, val url: String)

    private val repository by lazy { get<AnimeVostRepository>() }
    private val historyRepository by lazy { get<AnimeVostHistoryRepository>() }
    private val guidedRouter by lazy { get<GuidedRouter>() }
    private val preferencesHolder by lazy { get<PreferencesHolder>() }

    private val animeUrl by lazy {
        requireArguments().getString(ARG_ANIME_URL)
            ?: error("AnimeVost anime URL is required")
    }

    private var currentVideoId = ""
    private var currentEpisodeName = ""
    private var currentEpisodeNumber: Int? = null
    private var animeTitle = "AnimeVost"
    private var animePosterUrl = ""
    private var episodes: List<AnimeEpisode> = emptyList()
    private var sources: List<VideoSource> = emptyList()
    private var candidates: List<PlaybackCandidate> = emptyList()
    private var selectedSourceIndex = 0
    private var selectedCandidateIndex = 0
    private var speedIndex = 0
    private var sourceRefreshTried = false
    private var playAttemptId = 0
    private var fallbackRequestedForAttempt = -1
    private var playbackWatchdog: Job? = null
    private var progressSaveJob: Job? = null

    private val speeds: List<Float>
        get() = preferencesHolder.availableSpeeds.value.ifEmpty { listOf(1.0f, 1.25f, 1.5f, 2.0f) }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentVideoId = requireArguments().getString(ARG_VIDEO_ID).orEmpty()
        currentEpisodeName = requireArguments().getString(ARG_EPISODE_NAME).orEmpty()
        speedIndex = speeds.indexOf(preferencesHolder.playSpeed.value).takeIf { it >= 0 }
            ?: speeds.indexOf(1.0f).coerceAtLeast(0)
        speedIndex = speedIndex.coerceIn(0, speeds.lastIndex)
        player?.playbackParameters = PlaybackParameters(speeds[speedIndex])

        playerGlue?.actionListener = object : VideoPlayerGlue.OnActionClickedListener {
            override fun onPrevious() = openRelativeEpisode(-1)
            override fun onNext() = openRelativeEpisode(1)
            override fun onQualityClick() = cycleQuality()
            override fun onSpeedClick() {
                speedIndex = (speedIndex + 1) % speeds.size
                player?.playbackParameters = PlaybackParameters(speeds[speedIndex])
                preferencesHolder.playSpeed.value = speeds[speedIndex]
            }
            override fun onEpisodesClick() = openEpisodesPicker()
        }

        progressBarManager.initialDelay = 0
        progressBarManager.show()

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { repository.getDetails(animeUrl) }
                .onSuccess { details ->
                    animeTitle = details.title
                    animePosterUrl = details.posterUrl.orEmpty()
                    episodes = details.episodes
                }
            loadVideo(currentVideoId, currentEpisodeName)
        }
    }

    override fun onPause() {
        saveProgressNow()
        super.onPause()
    }

    override fun onDestroyView() {
        saveProgressNow()
        playbackWatchdog?.cancel()
        progressSaveJob?.cancel()
        playbackWatchdog = null
        progressSaveJob = null
        super.onDestroyView()
    }

    override fun onCompletePlaying() {
        saveProgressNow(forceCompleted = true)
        openRelativeEpisode(1)
    }

    override fun onPreparePlaying() {
        super.onPreparePlaying()
        playbackWatchdog?.cancel()
        playbackWatchdog = null
        progressBarManager.hide()
        startProgressSaver()
    }

    override fun onPlaybackError(error: PlaybackException) {
        super.onPlaybackError(error)
        requestFallback(player?.currentPosition ?: 0L)
    }

    private fun openEpisodesPicker() {
        saveProgressNow()
        viewLifecycleOwner.lifecycleScope.launch {
            if (episodes.isEmpty()) {
                runCatching { repository.getDetails(animeUrl) }
                    .onSuccess { details ->
                        animeTitle = details.title
                        animePosterUrl = details.posterUrl.orEmpty()
                        episodes = details.episodes
                    }
            }
            if (episodes.isNotEmpty()) {
                guidedRouter.open(
                    AnimeVostEpisodesGuidedScreen(
                        animeUrl = animeUrl,
                        currentVideoId = currentVideoId,
                        replacePlayer = true,
                    )
                )
            }
        }
    }

    private fun openRelativeEpisode(delta: Int) {
        if (episodes.isEmpty()) return
        val currentIndex = episodes.indexOfFirst { it.videoId == currentVideoId }
        if (currentIndex < 0) return
        val nextIndex = currentIndex + delta
        if (nextIndex !in episodes.indices) return

        saveProgressNow()
        val episode = episodes[nextIndex]
        viewLifecycleOwner.lifecycleScope.launch {
            loadVideo(episode.videoId, episode.name)
        }
    }

    private suspend fun loadVideo(
        videoId: String,
        episodeName: String,
        seekPosition: Long = 0L,
    ) {
        playbackWatchdog?.cancel()
        progressSaveJob?.cancel()
        playAttemptId++
        fallbackRequestedForAttempt = -1
        progressBarManager.show()

        currentVideoId = videoId
        currentEpisodeName = episodeName
        currentEpisodeNumber = episodes.firstOrNull { it.videoId == videoId }?.number
        sourceRefreshTried = false

        val resumePosition = if (seekPosition > 0L) {
            seekPosition
        } else {
            historyRepository.getProgress(animeUrl, videoId)
                ?.let { history ->
                    if (history.durationMs > 0L && history.positionMs >= history.durationMs - RESUME_END_GUARD_MS) 0L
                    else history.positionMs
                }
                ?: 0L
        }

        runCatching {
            repository.getPlaybackSources(
                animeUrl = animeUrl,
                videoId = videoId,
                episodeNumber = currentEpisodeNumber,
            )
        }.onSuccess { loadedSources ->
            applySources(loadedSources)
            if (candidates.isNotEmpty()) {
                saveProgressNow(positionOverride = resumePosition)
                playerGlue?.title = animeTitle
                playerGlue?.subtitle = episodeName
                playCandidate(0, resumePosition)
            } else {
                showPlaybackUnavailable()
            }
        }.onFailure {
            showPlaybackUnavailable()
        }
    }

    private fun applySources(loadedSources: List<VideoSource>) {
        sources = loadedSources
            .filter { it.url.startsWith("http://") || it.url.startsWith("https://") }
            .sortedByDescending { qualityRank(it.quality) }

        candidates = buildList {
            sources.forEach { source ->
                add(PlaybackCandidate(source, source.url))
                source.downloadUrl
                    ?.takeIf { it != source.url }
                    ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                    ?.let { add(PlaybackCandidate(source, it)) }
            }
        }.distinctBy { it.url }
    }

    private fun playCandidate(index: Int, seekPosition: Long) {
        val candidate = candidates.getOrNull(index) ?: run {
            requestSourcesRefreshOrFail(seekPosition)
            return
        }
        selectedCandidateIndex = index
        selectedSourceIndex = sources.indexOf(candidate.source).coerceAtLeast(0)
        val attempt = ++playAttemptId
        fallbackRequestedForAttempt = -1

        progressBarManager.show()
        playerGlue?.setQuality(candidate.source.toPlayerQuality())
        preparePlayer(candidate.url)
        player?.seekTo(seekPosition.coerceAtLeast(0L))
        playerGlue?.play()
        startPlaybackWatchdog(attempt)
    }

    private fun startPlaybackWatchdog(attempt: Int) {
        playbackWatchdog?.cancel()
        playbackWatchdog = viewLifecycleOwner.lifecycleScope.launch {
            delay(PLAYBACK_START_TIMEOUT_MS)
            if (attempt != playAttemptId) return@launch
            if (player?.playbackState != Player.STATE_READY) {
                requestFallback(player?.currentPosition ?: 0L)
            }
        }
    }

    private fun requestFallback(seekPosition: Long) {
        val failedAttempt = playAttemptId
        if (fallbackRequestedForAttempt == failedAttempt) return
        fallbackRequestedForAttempt = failedAttempt
        playbackWatchdog?.cancel()

        viewLifecycleOwner.lifecycleScope.launch {
            if (failedAttempt != playAttemptId) return@launch
            val nextCandidate = selectedCandidateIndex + 1
            if (nextCandidate in candidates.indices) {
                playCandidate(nextCandidate, seekPosition)
            } else {
                requestSourcesRefreshOrFail(seekPosition)
            }
        }
    }

    private fun requestSourcesRefreshOrFail(seekPosition: Long) {
        if (sourceRefreshTried) {
            showPlaybackUnavailable()
            return
        }
        sourceRefreshTried = true
        val expectedVideoId = currentVideoId
        val expectedNumber = currentEpisodeNumber
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                repository.getPlaybackSources(
                    animeUrl = animeUrl,
                    videoId = expectedVideoId,
                    episodeNumber = expectedNumber,
                    forceRefresh = true,
                )
            }.onSuccess { refreshedSources ->
                if (expectedVideoId != currentVideoId) return@onSuccess
                applySources(refreshedSources)
                if (candidates.isNotEmpty()) playCandidate(0, seekPosition) else showPlaybackUnavailable()
            }.onFailure { showPlaybackUnavailable() }
        }
    }

    private fun startProgressSaver() {
        progressSaveJob?.cancel()
        progressSaveJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(PROGRESS_SAVE_INTERVAL_MS)
                saveProgressNow()
            }
        }
    }

    private fun saveProgressNow(
        forceCompleted: Boolean = false,
        positionOverride: Long? = null,
    ) {
        if (animeTitle == "AnimeVost" || currentVideoId.isBlank()) return
        val episode = episodes.firstOrNull { it.videoId == currentVideoId }
        val duration = player?.duration?.takeIf { it > 0L } ?: 0L
        val position = when {
            forceCompleted && duration > 0L -> duration
            positionOverride != null -> positionOverride
            else -> player?.currentPosition ?: 0L
        }
        historyRepository.updateProgress(
            animeUrl = animeUrl,
            animeTitle = animeTitle,
            posterUrl = animePosterUrl,
            videoId = currentVideoId,
            episodeName = currentEpisodeName,
            episodeNumber = episode?.number ?: currentEpisodeNumber,
            positionMs = position,
            durationMs = duration,
        )
    }

    private fun showPlaybackUnavailable() {
        playbackWatchdog?.cancel()
        progressSaveJob?.cancel()
        playbackWatchdog = null
        progressBarManager.hide()
        playerGlue?.pause()
        Toast.makeText(
            requireContext(),
            "Источник видео недоступен. AniRu попробовал все доступные варианты.",
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun cycleQuality() {
        if (sources.size <= 1) return
        val currentPosition = player?.currentPosition ?: 0L
        val nextSourceIndex = (selectedSourceIndex + 1) % sources.size
        val nextSource = sources[nextSourceIndex]
        val candidateIndex = candidates.indexOfFirst { it.source == nextSource && it.url == nextSource.url }
        if (candidateIndex >= 0) playCandidate(candidateIndex, currentPosition)
    }

    private fun qualityRank(value: String): Int =
        Regex("(\\d{3,4})").find(value)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

    private fun VideoSource.toPlayerQuality(): PlayerQuality {
        val rank = qualityRank(quality)
        return when {
            rank >= 1080 -> PlayerQuality.FULLHD
            rank >= 720 -> PlayerQuality.HD
            else -> PlayerQuality.SD
        }
    }
}
