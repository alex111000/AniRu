@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package ru.radiationx.anilibria.screen.provider.player

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.*
import kotlinx.coroutines.*
import ru.radiationx.anilibria.provider.*
import ru.radiationx.anilibria.screen.player.BasePlayerFragment
import ru.radiationx.anilibria.screen.player.VideoPlayerGlue
import ru.radiationx.data.entity.common.PlayerQuality
import ru.radiationx.quill.get

class UnifiedPlayerFragment : BasePlayerFragment() {
    companion object {
        fun newInstance(provider: String, anime: String, episode: String, source: String?) = UnifiedPlayerFragment().apply {
            arguments = Bundle().apply { putString("provider", provider); putString("anime", anime); putString("episode", episode); putString("source", source) }
        }
    }
    private val catalog by lazy { get<UnifiedCatalogRepository>() }
    private val resolver by lazy { get<PlaybackResolver>() }
    private val library by lazy { get<UnifiedLibraryRepository>() }
    private val history by lazy { get<ProviderLocalRepository>() }
    private val provider by lazy { requireNotNull(ProviderId.fromWireId(requireArguments().getString("provider").orEmpty())) }
    private val animeId by lazy { requireArguments().getString("anime").orEmpty() }
    private var details: ProviderAnimeDetails? = null
    private var episode: ProviderEpisode? = null
    private data class Candidate(val resolved: ResolvedSource, val stream: ProviderStream) {
        val key: String get() = "${resolved.provider}|${resolved.source.id}|${stream.stableKey}"
        val voice: String get() = resolved.source.title
    }
    private var choices = emptyList<Candidate>()
    private var current: Candidate? = null
    private var loadJob: Job? = null
    private var menuJob: Job? = null
    private var watchdog: Job? = null
    private var saver: Job? = null
    private var resumeAt = 0L
    private var attempts = mutableSetOf<String>()
    private var generation = 0
    private var extended = false
    private var changing = false

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        val controller = WindowCompat.getInsetsController(requireActivity().window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        player?.let { it.trackSelectionParameters = it.trackSelectionParameters.buildUpon().setForceHighestSupportedBitrate(true).build() }
        playerGlue?.enableSourceSelection()
        playerGlue?.actionListener = object : VideoPlayerGlue.OnActionClickedListener {
            override fun onPrevious() = relative(-1)
            override fun onNext() = relative(1)
            override fun onEpisodesClick() = episodeMenu()
            override fun onSourceClick() = expandChoices { sourceMenu() }
            override fun onVoiceClick() = expandChoices { voiceMenu() }
            override fun onQualityClick() = qualityMenu()
            override fun onSpeedClick() {
                val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
                choose("Скорость", speeds.map { "${it}×" }) { player?.playbackParameters = PlaybackParameters(speeds[it]) }
            }
        }
        progressBarManager.initialDelay = 0
        load(state?.getString("episode") ?: requireArguments().getString("episode").orEmpty(), state?.getLong("position"))
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("episode", episode?.id); outState.putLong("position", player?.currentPosition ?: resumeAt)
        super.onSaveInstanceState(outState)
    }
    private fun load(id: String, savedPosition: Long? = null) {
        loadJob?.cancel(); menuJob?.cancel(); watchdog?.cancel(); saver?.cancel()
        val ticket = ++generation
        save(); playerGlue?.pause(); choices = emptyList(); current = null; attempts.clear(); extended = false
        progressBarManager.show()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val d = details ?: catalog.getDetails(provider, animeId).also { details = it }
                val ep = d.episodes.firstOrNull { it.id == id } ?: throw java.io.IOException("Серия не найдена")
                episode = ep
                val previous = library.resume(provider, animeId, ep)
                resumeAt = savedPosition ?: previous?.takeUnless { it.completed }?.position ?: 0L
                playerGlue?.title = d.title
                playerGlue?.subtitle = "Поиск источника · ${ep.title}"
                val voice = library.voice(provider, animeId)
                val resolved = resolver.resolve(d, ep, voice)
                if (ticket != generation) return@launch
                addChoices(resolved)
                val chosen = choices.firstOrNull { voice == null || sameVoice(it.voice, voice) }
                if (chosen == null) {
                    if (choices.isNotEmpty()) { progressBarManager.hide(); voiceMenu("Выбранная озвучка недоступна. Выберите другую") }
                    else unavailable("Не найден доступный поток. «Источник» — повторить поиск.")
                } else play(chosen, resumeAt)
            } catch (error: Exception) { currentCoroutineContext().ensureActive(); unavailable("Источник не ответил. Попробуйте другой источник или повторите позже.") }
        }
    }
    private fun addChoices(sources: List<ResolvedSource>) {
        choices = (choices + sources.flatMap { resolved -> resolved.source.streams.map { Candidate(resolved, it) } })
            .filter { it.stream.url.startsWith("http://") || it.stream.url.startsWith("https://") }
            .distinctBy { it.key }.sortedByDescending { it.stream.quality }
    }
    private fun sameVoice(a: String, b: String) = AnimeIdentity.normalize(a) == AnimeIdentity.normalize(b)
    private fun play(candidate: Candidate, position: Long) {
        if (attempts.size >= 6) { unavailable("Не удалось запустить видео. Выберите источник вручную."); return }
        changing = true
        current = candidate; attempts += candidate.key; resumeAt = position
        library.rememberVoice(provider, animeId, candidate.voice)
        playerGlue?.subtitle = "${episode?.title.orEmpty()} · ${candidate.resolved.provider.uiName} · ${candidate.voice} · ${qualityLabel(candidate.stream.quality)}"
        playerGlue?.setQuality(when { candidate.stream.quality >= 1080 -> PlayerQuality.FULLHD; candidate.stream.quality >= 720 -> PlayerQuality.HD; else -> PlayerQuality.SD })
        progressBarManager.show()
        player?.let { it.trackSelectionParameters = it.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO).clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .setForceHighestSupportedBitrate(true).build() }
        preparePlayer(candidate.stream.url, candidate.stream.headers, when (candidate.stream.type) {
            StreamType.HLS -> hlsMimeType(); StreamType.DASH -> dashMimeType(); StreamType.MP4 -> mp4MimeType(); StreamType.UNKNOWN -> null
        })
        player?.seekTo(position.coerceAtLeast(0)); playerGlue?.play()
        changing = false
        watchdog?.cancel()
        val ticket = generation
        watchdog = viewLifecycleOwner.lifecycleScope.launch {
            delay(8_000)
            if (ticket == generation && player?.playbackState != Player.STATE_READY) fallback()
        }
    }
    override fun onPreparePlaying() {
        watchdog?.cancel(); progressBarManager.hide()
        saver?.cancel(); saver = viewLifecycleOwner.lifecycleScope.launch { while (isActive) { delay(5_000); save() } }
    }
    override fun onPlaybackError(error: PlaybackException) { if (!changing) fallback() }
    private fun fallback() {
        watchdog?.cancel()
        val playing = current ?: return
        val position = player?.currentPosition?.takeIf { it > 0 } ?: resumeAt
        val next = choices.firstOrNull { it.key !in attempts && sameVoice(it.voice, playing.voice) }
        if (next != null) { play(next, position); return }
        if (!extended) {
            expandChoices {
                val candidate = choices.firstOrNull { it.key !in attempts && sameVoice(it.voice, playing.voice) }
                if (candidate != null) play(candidate, position) else unavailable("Эта озвучка недоступна. Выберите другую через «Озвучка».")
            }
        } else unavailable("Эта озвучка недоступна. Выберите другую через «Озвучка».")
    }
    private fun expandChoices(then: () -> Unit) {
        if (extended) { then(); return }
        if (menuJob?.isActive == true) return
        val d = details ?: return
        val ep = episode ?: return
        val ticket = generation
        Toast.makeText(requireContext(), "Проверяем источники — до 8 секунд", Toast.LENGTH_SHORT).show()
        menuJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                addChoices(resolver.resolve(d, ep, current?.voice ?: library.voice(provider, animeId), all = true))
                if (ticket == generation) { extended = true; then() }
            } catch (error: Exception) { currentCoroutineContext().ensureActive(); unavailable("Проверка источников не удалась") }
        }
    }
    private fun sourceMenu() {
        val providers = choices.map { it.resolved.provider }.distinct()
        if (providers.isEmpty()) { extended = false; unavailable("Источники недоступны. Нажмите «Источник», чтобы повторить."); return }
        choose("Источник", providers.map { it.uiName }) { index ->
            val chosen = choices.filter { it.resolved.provider == providers[index] }
            val same = chosen.firstOrNull { current == null || sameVoice(it.voice, current!!.voice) }
            if (same != null) manual(same) else voiceMenu("В этом источнике другая озвучка — выберите", chosen)
        }
    }
    private fun voiceMenu(title: String = "Озвучка", available: List<Candidate> = choices) {
        val voices = available.distinctBy { AnimeIdentity.normalize(it.voice) }
        val audio = player?.currentTracks?.groups.orEmpty().filter { it.type == C.TRACK_TYPE_AUDIO }
            .flatMap { group -> (0 until group.length).filter { group.isTrackSupported(it) }.map { group to it } }
        val labels = voices.map { "${it.voice} · ${it.resolved.provider.uiName}" } + audio.map { (group, index) ->
            val format = group.getTrackFormat(index); "Аудиодорожка: ${format.label ?: format.language ?: (index + 1).toString()}"
        }
        if (labels.isEmpty()) { unavailable("Другие озвучки не найдены"); return }
        choose(title, labels) { index ->
            if (index < voices.size) manual(voices[index]) else {
                val (group, track) = audio[index - voices.size]
                player?.let { it.trackSelectionParameters = it.trackSelectionParameters.buildUpon()
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track)).build() }
            }
        }
    }
    private fun qualityMenu() {
        val playing = current ?: return
        val streams = choices.filter { it.resolved.provider == playing.resolved.provider && it.resolved.source.id == playing.resolved.source.id }.distinctBy { it.stream.quality }
        val tracks = player?.currentTracks?.groups.orEmpty().filter { it.type == C.TRACK_TYPE_VIDEO }
            .flatMap { group -> (0 until group.length).filter { group.isTrackSupported(it) }.map { group to it } }
            .distinctBy { it.first.getTrackFormat(it.second).height }.sortedByDescending { it.first.getTrackFormat(it.second).height }
        val labels = listOf("Авто · максимальное поддерживаемое") + streams.map { qualityLabel(it.stream.quality) } +
            tracks.map { "${it.first.getTrackFormat(it.second).height}p · дорожка" }
        choose("Качество — ${playing.voice}", labels) { index -> when {
            index == 0 -> {
                player?.let { it.trackSelectionParameters = it.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).setForceHighestSupportedBitrate(true).build() }
                streams.firstOrNull()?.let(::manual)
            }
            index <= streams.size -> manual(streams[index - 1])
            else -> { val (group, track) = tracks[index - streams.size - 1]
                player?.let { it.trackSelectionParameters = it.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track)).build() }
            }
        } }
    }
    private fun manual(candidate: Candidate) { attempts.clear(); play(candidate, player?.currentPosition?.takeIf { it > 0 } ?: resumeAt) }
    private fun qualityLabel(value: Int) = if (value > 0) "${value}p" else "Авто (HLS/DASH)"
    private fun choose(title: String, values: List<String>, selected: (Int) -> Unit) {
        AlertDialog.Builder(requireContext()).setTitle(title).setItems(values.toTypedArray()) { _, index -> selected(index) }.setNegativeButton("Закрыть", null).show()
    }
    private fun relative(delta: Int) {
        val eps = details?.episodes ?: return
        val index = eps.indexOfFirst { it.id == episode?.id }
        eps.getOrNull(index + delta)?.let { load(it.id) }
    }
    private fun episodeMenu() {
        val eps = details?.episodes.orEmpty()
        choose("Серии", eps.map { "Сезон ${it.season} · ${it.title}" }) { load(eps[it].id) }
    }
    override fun onCompletePlaying() { save(completed = true); relative(1) }
    private fun save(completed: Boolean = false) {
        val d = details ?: return; val ep = episode ?: return
        val duration = player?.duration?.takeIf { it > 0 } ?: 0L
        val position = if (completed && duration > 0) duration else player?.currentPosition?.takeIf { it > 0 } ?: resumeAt
        if (current == null || position <= 0) return
        history.updateProgress(ProviderLocalRepository.History(provider, animeId, d.title, d.posterUrl, ep.id, ep.number,
            ep.title, current?.resolved?.source?.id, position, duration, System.currentTimeMillis(), ep.season, ep.special, ep.numberLabel))
    }
    private fun unavailable(message: String) { watchdog?.cancel(); progressBarManager.hide(); playerGlue?.pause(); Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show() }
    override fun onPause() { save(); super.onPause() }
    override fun onDestroyView() {
        save(); generation++; loadJob?.cancel(); menuJob?.cancel(); watchdog?.cancel(); saver?.cancel()
        view?.let { WindowCompat.getInsetsController(requireActivity().window, it).show(WindowInsetsCompat.Type.systemBars()) }
        super.onDestroyView()
    }
}
