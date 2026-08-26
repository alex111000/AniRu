package ru.radiationx.anilibria.screen.animevost.episodes

import android.os.Bundle
import android.view.View
import androidx.leanback.widget.GuidedAction
import androidx.lifecycle.lifecycleScope
import com.animevost.sdk.model.AnimeEpisode
import com.github.terrakok.cicerone.Router
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.animevost.AnimeVostHistoryRepository
import ru.radiationx.anilibria.animevost.AnimeVostRepository
import ru.radiationx.anilibria.common.fragment.FakeGuidedStepFragment
import ru.radiationx.anilibria.common.fragment.GuidedRouter
import ru.radiationx.anilibria.screen.AnimeVostPlayerScreen
import ru.radiationx.quill.get

class AnimeVostEpisodesGuidedFragment : FakeGuidedStepFragment() {

    companion object {
        private const val ARG_ANIME_URL = "animevost_episode_picker_url"
        private const val ARG_CURRENT_VIDEO_ID = "animevost_episode_picker_current_video"
        private const val ARG_REPLACE_PLAYER = "animevost_episode_picker_replace_player"

        private const val RANGE_SIZE = 100
        private const val RANGE_THRESHOLD = 150
        private const val ACTION_RANGE_BASE = 100_000L
        private const val ACTION_BACK_RANGES = 90_001L
        private const val ACTION_LAST_EPISODE = 90_002L
        private const val ACTION_CONTINUE = 90_003L

        fun newInstance(
            animeUrl: String,
            currentVideoId: String?,
            replacePlayer: Boolean,
        ) = AnimeVostEpisodesGuidedFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_ANIME_URL, animeUrl)
                putString(ARG_CURRENT_VIDEO_ID, currentVideoId)
                putBoolean(ARG_REPLACE_PLAYER, replacePlayer)
            }
        }
    }

    private val repository by lazy { get<AnimeVostRepository>() }
    private val historyRepository by lazy { get<AnimeVostHistoryRepository>() }
    private val guidedRouter by lazy { get<GuidedRouter>() }
    private val router by lazy { get<Router>() }

    private val animeUrl by lazy { requireArguments().getString(ARG_ANIME_URL).orEmpty() }
    private val currentVideoId by lazy { requireArguments().getString(ARG_CURRENT_VIDEO_ID) }
    private val replacePlayer by lazy { requireArguments().getBoolean(ARG_REPLACE_PLAYER, false) }

    private var episodes: List<AnimeEpisode> = emptyList()
    private var progressByVideoId: Map<String, ru.radiationx.anilibria.animevost.AnimeVostHistoryItem> = emptyMap()

    override fun onProvideTheme(): Int = R.style.AppTheme_Player_LeanbackWizard

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        showInfo("Загрузка полного списка серий…")

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { repository.getEpisodes(animeUrl) }
                .onSuccess { loaded ->
                    episodes = loaded
                    progressByVideoId = historyRepository.getEpisodeProgress(animeUrl).associateBy { it.videoId }
                    when {
                        episodes.isEmpty() -> showInfo("Серии не найдены")
                        episodes.size > RANGE_THRESHOLD -> showRanges()
                        else -> showEpisodes(episodes.indices)
                    }
                }
                .onFailure { error -> showInfo("Не удалось загрузить серии", error.message) }
        }
    }

    private fun showRanges() {
        val continueItem = historyRepository.getHistory().firstOrNull { it.animeUrl == animeUrl }
        val rangeActions = mutableListOf<GuidedAction>()
        if (continueItem != null) {
            rangeActions += GuidedAction.Builder(requireContext())
                .id(ACTION_CONTINUE)
                .title("Продолжить просмотр")
                .description(buildString {
                    continueItem.episodeNumber?.let { append("Серия $it") }
                    if (continueItem.progressPercent > 0) {
                        if (isNotEmpty()) append(" • ")
                        append("${continueItem.progressPercent}%")
                    }
                })
                .build()
        }
        rangeActions += GuidedAction.Builder(requireContext())
            .id(ACTION_LAST_EPISODE)
            .title("Последняя серия")
            .description(episodes.lastOrNull()?.name)
            .build()

        episodes.indices.chunked(RANGE_SIZE).forEachIndexed { chunkIndex, indices ->
            val first = episodes[indices.first()].number ?: indices.first() + 1
            val last = episodes[indices.last()].number ?: indices.last() + 1
            rangeActions += GuidedAction.Builder(requireContext())
                .id(ACTION_RANGE_BASE + chunkIndex)
                .title("Серии $first–$last")
                .description("${indices.size} серий")
                .build()
        }
        actions = rangeActions

        val currentIndex = episodes.indexOfFirst { it.videoId == currentVideoId }
        if (currentIndex >= 0) {
            val rangeIndex = currentIndex / RANGE_SIZE
            selectedActionPosition = rangeActions.indexOfFirst { it.id == ACTION_RANGE_BASE + rangeIndex }
        }
    }

    private fun showEpisodes(indices: IntRange) = showEpisodes(indices.toList())

    private fun showEpisodes(indices: List<Int>) {
        val list = mutableListOf<GuidedAction>()
        if (episodes.size > RANGE_THRESHOLD) {
            list += GuidedAction.Builder(requireContext())
                .id(ACTION_BACK_RANGES)
                .title("← Диапазоны серий")
                .build()
        }
        indices.forEach { index ->
            val episode = episodes.getOrNull(index) ?: return@forEach
            list += GuidedAction.Builder(requireContext())
                .id(index.toLong())
                .title(episode.name)
                .description(buildEpisodeDescription(episode))
                .build()
        }
        actions = list
        val currentIndex = episodes.indexOfFirst { it.videoId == currentVideoId }
        val pos = list.indexOfFirst { it.id == currentIndex.toLong() }
        if (pos >= 0) selectedActionPosition = pos
    }


    private fun buildEpisodeDescription(episode: AnimeEpisode): String? {
        val base = episode.number?.let { "Серия $it" }.orEmpty()
        val progress = progressByVideoId[episode.videoId]
            ?: episode.number?.let { number ->
                progressByVideoId.values.firstOrNull { it.episodeNumber == number }
            }
        val state = when {
            progress == null -> ""
            progress.isCompleted -> "Просмотрено"
            progress.progressPercent > 0 -> "${progress.progressPercent}%"
            else -> "Начато"
        }
        return listOf(base, state).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { null }
    }

    private fun showInfo(title: String, description: String? = null) {
        actions = listOf(
            GuidedAction.Builder(requireContext())
                .id(-1L)
                .title(title)
                .description(description)
                .enabled(false)
                .focusable(false)
                .infoOnly(true)
                .build()
        )
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        when {
            action.id == ACTION_BACK_RANGES -> showRanges()
            action.id == ACTION_LAST_EPISODE -> episodes.lastOrNull()?.let { openEpisode(it) }
            action.id == ACTION_CONTINUE -> {
                val item = historyRepository.getHistory().firstOrNull { it.animeUrl == animeUrl } ?: return
                val episode = episodes.firstOrNull { it.videoId == item.videoId }
                    ?: item.episodeNumber?.let { number -> episodes.firstOrNull { it.number == number } }
                    ?: return
                openEpisode(episode)
            }
            action.id >= ACTION_RANGE_BASE -> {
                val chunkIndex = (action.id - ACTION_RANGE_BASE).toInt()
                val start = chunkIndex * RANGE_SIZE
                val endExclusive = minOf(start + RANGE_SIZE, episodes.size)
                if (start in 0 until endExclusive) showEpisodes((start until endExclusive).toList())
            }
            else -> episodes.getOrNull(action.id.toInt())?.let { openEpisode(it) }
        }
    }

    private fun openEpisode(episode: AnimeEpisode) {
        guidedRouter.close()
        val screen = AnimeVostPlayerScreen(
            animeUrl = animeUrl,
            videoId = episode.videoId,
            episodeName = episode.name,
        )
        if (replacePlayer) router.replaceScreen(screen) else router.navigateTo(screen)
    }
}
