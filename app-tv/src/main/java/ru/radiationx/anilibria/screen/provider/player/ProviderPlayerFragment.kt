@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package ru.radiationx.anilibria.screen.provider.player

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.fragment.GuidedRouter
import ru.radiationx.anilibria.provider.ProviderAnimeDetails
import ru.radiationx.anilibria.provider.ProviderEpisode
import ru.radiationx.anilibria.provider.ProviderId
import ru.radiationx.anilibria.provider.ProviderLocalRepository
import ru.radiationx.anilibria.provider.ProviderRegistry
import ru.radiationx.anilibria.provider.ProviderSource
import ru.radiationx.anilibria.provider.ProviderStream
import ru.radiationx.anilibria.provider.StreamType
import ru.radiationx.anilibria.screen.ProviderEpisodesGuidedScreen
import ru.radiationx.anilibria.screen.player.BasePlayerFragment
import ru.radiationx.anilibria.screen.player.VideoPlayerGlue
import ru.radiationx.data.datasource.holders.PreferencesHolder
import ru.radiationx.data.entity.common.PlayerQuality
import ru.radiationx.quill.get

class ProviderPlayerFragment : BasePlayerFragment() {
    companion object {
        private const val ARG_PROVIDER = "provider_player_provider"
        private const val ARG_ANIME = "provider_player_anime"
        private const val ARG_EPISODE = "provider_player_episode"
        private const val ARG_SOURCE = "provider_player_source"
        private const val START_TIMEOUT_MS = 12_000L
        private const val SAVE_INTERVAL_MS = 10_000L
        private const val RESUME_END_GUARD_MS = 30_000L

        fun newInstance(providerId: String, animeId: String, episodeId: String, sourceId: String?) =
            ProviderPlayerFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PROVIDER, providerId)
                    putString(ARG_ANIME, animeId)
                    putString(ARG_EPISODE, episodeId)
                    putString(ARG_SOURCE, sourceId)
                }
            }
    }

    private data class Candidate(
        val source: ProviderSource,
        val stream: ProviderStream,
        val originProvider: ProviderId,
    )

    private val registry by lazy { get<ProviderRegistry>() }
    private val history by lazy { get<ProviderLocalRepository>() }
    private val guidedRouter by lazy { get<GuidedRouter>() }
    private val preferencesHolder by lazy { get<PreferencesHolder>() }
    private val originalProvider by lazy {
        requireNotNull(ProviderId.fromWireId(requireArguments().getString(ARG_PROVIDER).orEmpty()))
    }
    private val animeId by lazy { requireArguments().getString(ARG_ANIME).orEmpty() }
    private var currentEpisodeId = ""
    private var requestedSourceId: String? = null
    private var details: ProviderAnimeDetails? = null
    private var currentEpisode: ProviderEpisode? = null
    private var candidates: List<Candidate> = emptyList()
    private var candidateIndex = 0
    private var attemptId = 0
    private var fallbackAttempt = -1
    private var watchdog: Job? = null
    private var saveJob: Job? = null
    private var speedIndex = 0
    private val attemptedProviders = linkedSetOf<ProviderId>()
    private val speeds: List<Float>
        get() = preferencesHolder.availableSpeeds.value.ifEmpty { listOf(1.0f, 1.25f, 1.5f, 2.0f) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentEpisodeId = requireArguments().getString(ARG_EPISODE).orEmpty()
        requestedSourceId = requireArguments().getString(ARG_SOURCE)
        speedIndex = speeds.indexOf(preferencesHolder.playSpeed.value).takeIf { it >= 0 }
            ?: speeds.indexOf(1.0f).coerceAtLeast(0)
        speedIndex = speedIndex.coerceIn(0, speeds.lastIndex)
        player?.playbackParameters = PlaybackParameters(speeds[speedIndex])

        playerGlue?.actionListener = object : VideoPlayerGlue.OnActionClickedListener {
            override fun onPrevious() = openRelativeEpisode(-1)
            override fun onNext() = openRelativeEpisode(1)
            override fun onQualityClick() = cycleCandidate()
            override fun onSpeedClick() {
                speedIndex = (speedIndex + 1) % speeds.size
                player?.playbackParameters = PlaybackParameters(speeds[speedIndex])
                preferencesHolder.playSpeed.value = speeds[speedIndex]
            }
            override fun onEpisodesClick() = openEpisodes()
        }

        progressBarManager.initialDelay = 0
        progressBarManager.show()
        viewLifecycleOwner.lifecycleScope.launch { loadEpisode(currentEpisodeId, requestedSourceId) }
    }

    override fun onPause() {
        saveProgress()
        super.onPause()
    }

    override fun onDestroyView() {
        saveProgress()
        watchdog?.cancel()
        saveJob?.cancel()
        watchdog = null
        saveJob = null
        super.onDestroyView()
    }

    override fun onPreparePlaying() {
        watchdog?.cancel()
        watchdog = null
        progressBarManager.hide()
        startSaver()
    }

    override fun onPlaybackError(error: PlaybackException) {
        requestFallback(player?.currentPosition ?: 0L)
    }

    override fun onCompletePlaying() {
        saveProgress(forceCompleted = true)
        openRelativeEpisode(1)
    }

    private suspend fun loadEpisode(episodeId: String, preferredSourceId: String?, seekOverride: Long? = null) {
        watchdog?.cancel()
        saveJob?.cancel()
        progressBarManager.show()
        currentEpisodeId = episodeId
        requestedSourceId = preferredSourceId
        attemptedProviders.clear()
        attemptedProviders += originalProvider

        val loadedDetails = details ?: runCatching { registry.get(originalProvider).getDetails(animeId) }.getOrNull()
        if (loadedDetails == null) {
            showUnavailable("Не удалось загрузить описание")
            return
        }
        details = loadedDetails
        val episode = loadedDetails.episodes.firstOrNull { it.id == episodeId }
        if (episode == null) {
            showUnavailable("Серия не найдена")
            return
        }
        currentEpisode = episode

        val resume = seekOverride ?: history.getProgress(originalProvider, animeId, episodeId)?.let {
            if (it.durationMs > 0L && it.positionMs >= it.durationMs - RESUME_END_GUARD_MS) 0L else it.positionMs
        } ?: 0L

        val sources = runCatching { registry.get(originalProvider).getSources(animeId, episodeId) }.getOrDefault(emptyList())
        applyCandidates(originalProvider, sources, preferredSourceId)
        if (candidates.isEmpty()) {
            val alt = registry.findAlternativeStreams(loadedDetails.title, episode.number, attemptedProviders)
            if (alt != null) {
                attemptedProviders += alt.provider
                applyCandidates(alt.provider, alt.sources, null)
            }
        }
        if (candidates.isEmpty()) {
            showUnavailable("Источники видео недоступны")
            return
        }
        playerGlue?.title = loadedDetails.title
        playerGlue?.subtitle = listOfNotNull(
            episode.number?.let { "Серия $it" },
            episode.title.takeIf { it.isNotBlank() },
        ).distinct().joinToString(" • ")
        playCandidate(0, resume)
    }

    private fun applyCandidates(origin: ProviderId, sources: List<ProviderSource>, preferredSourceId: String?) {
        val orderedSources = sources.sortedWith(compareByDescending<ProviderSource> { it.id == preferredSourceId })
        candidates = orderedSources.flatMap { source ->
            source.streams.sortedByDescending { it.quality }.map { Candidate(source, it, origin) }
        }.filter { it.stream.url.startsWith("https://") || it.stream.url.startsWith("http://") }
            .distinctBy { "${it.originProvider.wireId}|${it.stream.stableKey}" }
    }

    private fun playCandidate(index: Int, seekPosition: Long) {
        val candidate = candidates.getOrNull(index) ?: run {
            showUnavailable("AniRu попробовал все доступные варианты")
            return
        }
        candidateIndex = index
        val attempt = ++attemptId
        fallbackAttempt = -1
        progressBarManager.show()
        playerGlue?.setQuality(candidate.stream.toPlayerQuality())
        playerGlue?.subtitle = listOfNotNull(
            currentEpisode?.number?.let { "Серия $it" },
            candidate.source.title,
            candidate.originProvider.uiName,
        ).distinct().joinToString(" • ")
        preparePlayer(
            candidate.stream.url,
            candidate.stream.headers,
            when (candidate.stream.type) {
                StreamType.HLS -> hlsMimeType()
                StreamType.DASH -> dashMimeType()
                StreamType.MP4 -> mp4MimeType()
                StreamType.UNKNOWN -> null
            },
        )
        player?.seekTo(seekPosition.coerceAtLeast(0L))
        playerGlue?.play()
        watchdog?.cancel()
        watchdog = viewLifecycleOwner.lifecycleScope.launch {
            delay(START_TIMEOUT_MS)
            if (attempt == attemptId && player?.playbackState != Player.STATE_READY) {
                requestFallback(player?.currentPosition ?: seekPosition)
            }
        }
    }

    private fun requestFallback(seekPosition: Long) {
        val failed = attemptId
        if (fallbackAttempt == failed) return
        fallbackAttempt = failed
        watchdog?.cancel()
        viewLifecycleOwner.lifecycleScope.launch {
            if (failed != attemptId) return@launch
            val next = candidateIndex + 1
            if (next in candidates.indices) {
                playCandidate(next, seekPosition)
                return@launch
            }
            val d = details
            val e = currentEpisode
            if (d != null && e != null) {
                val alt = registry.findAlternativeStreams(d.title, e.number, attemptedProviders)
                if (alt != null) {
                    attemptedProviders += alt.provider
                    applyCandidates(alt.provider, alt.sources, null)
                    if (candidates.isNotEmpty()) {
                        playCandidate(0, seekPosition)
                        return@launch
                    }
                }
            }
            showUnavailable("AniRu попробовал все доступные providers")
        }
    }

    private fun cycleCandidate() {
        if (candidates.size <= 1) return
        val next = (candidateIndex + 1) % candidates.size
        playCandidate(next, player?.currentPosition ?: 0L)
    }

    private fun openRelativeEpisode(delta: Int) {
        val d = details ?: return
        val index = d.episodes.indexOfFirst { it.id == currentEpisodeId }
        val next = index + delta
        if (index < 0 || next !in d.episodes.indices) return
        saveProgress()
        viewLifecycleOwner.lifecycleScope.launch {
            loadEpisode(d.episodes[next].id, requestedSourceId)
        }
    }

    private fun openEpisodes() {
        saveProgress()
        guidedRouter.open(
            ProviderEpisodesGuidedScreen(
                providerId = originalProvider.wireId,
                animeId = animeId,
                currentEpisodeId = currentEpisodeId,
                replacePlayer = true,
            )
        )
    }

    private fun startSaver() {
        saveJob?.cancel()
        saveJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(SAVE_INTERVAL_MS)
                saveProgress()
            }
        }
    }

    private fun saveProgress(forceCompleted: Boolean = false) {
        val d = details ?: return
        val e = currentEpisode ?: return
        val duration = player?.duration?.takeIf { it > 0L } ?: 0L
        val position = if (forceCompleted && duration > 0L) duration else player?.currentPosition ?: 0L
        history.updateProgress(
            ProviderLocalRepository.History(
                provider = originalProvider,
                animeId = animeId,
                animeTitle = d.title,
                posterUrl = d.posterUrl,
                episodeId = e.id,
                episodeNumber = e.number,
                episodeTitle = e.title,
                sourceId = candidates.getOrNull(candidateIndex)?.source?.id ?: requestedSourceId,
                positionMs = position,
                durationMs = duration,
                watchedAt = System.currentTimeMillis(),
            )
        )
    }

    private fun showUnavailable(message: String) {
        watchdog?.cancel()
        saveJob?.cancel()
        progressBarManager.hide()
        playerGlue?.pause()
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun ProviderStream.toPlayerQuality(): PlayerQuality = when {
        quality >= 1080 -> PlayerQuality.FULLHD
        quality >= 720 -> PlayerQuality.HD
        else -> PlayerQuality.SD
    }
}
