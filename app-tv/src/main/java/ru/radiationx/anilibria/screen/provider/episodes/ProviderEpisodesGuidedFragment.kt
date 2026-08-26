package ru.radiationx.anilibria.screen.provider.episodes

import android.os.Bundle
import android.view.View
import androidx.leanback.widget.GuidedAction
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.common.fragment.FakeGuidedStepFragment
import ru.radiationx.anilibria.common.fragment.GuidedRouter
import ru.radiationx.anilibria.provider.ProviderEpisode
import ru.radiationx.anilibria.provider.ProviderId
import ru.radiationx.anilibria.provider.ProviderLocalRepository
import ru.radiationx.anilibria.provider.ProviderRegistry
import ru.radiationx.anilibria.screen.ProviderSourcesGuidedScreen
import ru.radiationx.quill.get

class ProviderEpisodesGuidedFragment : FakeGuidedStepFragment() {
    companion object {
        private const val ARG_PROVIDER = "provider_episode_picker_provider"
        private const val ARG_ANIME = "provider_episode_picker_anime"
        private const val ARG_CURRENT = "provider_episode_picker_current"
        private const val ARG_REPLACE = "provider_episode_picker_replace"
        private const val RANGE_SIZE = 100
        private const val RANGE_THRESHOLD = 150
        private const val ACTION_RANGE_BASE = 100_000L
        private const val ACTION_BACK = 90_001L
        private const val ACTION_LAST = 90_002L
        private const val ACTION_CONTINUE = 90_003L

        fun newInstance(providerId: String, animeId: String, currentEpisodeId: String?, replacePlayer: Boolean) =
            ProviderEpisodesGuidedFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PROVIDER, providerId)
                    putString(ARG_ANIME, animeId)
                    putString(ARG_CURRENT, currentEpisodeId)
                    putBoolean(ARG_REPLACE, replacePlayer)
                }
            }
    }

    private val registry by lazy { get<ProviderRegistry>() }
    private val localRepository by lazy { get<ProviderLocalRepository>() }
    private val guidedRouter by lazy { get<GuidedRouter>() }
    private val providerId by lazy {
        requireNotNull(ProviderId.fromWireId(requireArguments().getString(ARG_PROVIDER).orEmpty()))
    }
    private val animeId by lazy { requireArguments().getString(ARG_ANIME).orEmpty() }
    private val currentEpisodeId by lazy { requireArguments().getString(ARG_CURRENT) }
    private val replacePlayer by lazy { requireArguments().getBoolean(ARG_REPLACE, false) }
    private var episodes: List<ProviderEpisode> = emptyList()

    override fun onProvideTheme(): Int = R.style.AppTheme_Player_LeanbackWizard

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        showInfo("Загрузка серий…")
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { registry.get(providerId).getDetails(animeId).episodes }
                .onSuccess {
                    episodes = it
                    when {
                        it.isEmpty() -> showInfo("Серии не найдены")
                        it.size > RANGE_THRESHOLD -> showRanges()
                        else -> showEpisodes(it.indices.toList())
                    }
                }
                .onFailure { showInfo("Не удалось загрузить серии", it.message) }
        }
    }

    private fun showRanges() {
        val list = mutableListOf<GuidedAction>()
        val continueItem = localRepository.getHistory().firstOrNull {
            it.provider == providerId && it.animeId == animeId && !it.isCompleted
        }
        if (continueItem != null) {
            list += GuidedAction.Builder(requireContext()).id(ACTION_CONTINUE)
                .title("Продолжить просмотр")
                .description(listOfNotNull(
                    continueItem.episodeNumber?.let { "Серия $it" },
                    continueItem.progressPercent.takeIf { it > 0 }?.let { "$it%" },
                ).joinToString(" • ")).build()
        }
        list += GuidedAction.Builder(requireContext()).id(ACTION_LAST)
            .title("Последняя серия").description(episodes.lastOrNull()?.title).build()
        episodes.indices.chunked(RANGE_SIZE).forEachIndexed { chunk, indices ->
            val first = episodes[indices.first()].number ?: indices.first() + 1
            val last = episodes[indices.last()].number ?: indices.last() + 1
            list += GuidedAction.Builder(requireContext()).id(ACTION_RANGE_BASE + chunk)
                .title("Серии $first–$last").description("${indices.size} серий").build()
        }
        actions = list
        val currentIndex = episodes.indexOfFirst { it.id == currentEpisodeId }
        if (currentIndex >= 0) {
            selectedActionPosition = list.indexOfFirst { it.id == ACTION_RANGE_BASE + currentIndex / RANGE_SIZE }
        }
    }

    private fun showEpisodes(indices: List<Int>) {
        val list = mutableListOf<GuidedAction>()
        if (episodes.size > RANGE_THRESHOLD) {
            list += GuidedAction.Builder(requireContext()).id(ACTION_BACK).title("← Диапазоны серий").build()
        }
        indices.forEach { index ->
            val episode = episodes.getOrNull(index) ?: return@forEach
            val progress = localRepository.getProgress(providerId, animeId, episode.id)
            val state = when {
                progress == null -> null
                progress.isCompleted -> "Просмотрено"
                progress.progressPercent > 0 -> "${progress.progressPercent}%"
                else -> "Начато"
            }
            list += GuidedAction.Builder(requireContext()).id(index.toLong())
                .title(episode.title)
                .description(listOfNotNull(episode.number?.let { "Серия $it" }, state).joinToString(" • "))
                .build()
        }
        actions = list
        val current = episodes.indexOfFirst { it.id == currentEpisodeId }
        val position = list.indexOfFirst { it.id == current.toLong() }
        if (position >= 0) selectedActionPosition = position
    }

    private fun showInfo(title: String, description: String? = null) {
        actions = listOf(GuidedAction.Builder(requireContext()).id(-1).title(title).description(description)
            .enabled(false).focusable(false).infoOnly(true).build())
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        when {
            action.id == ACTION_BACK -> showRanges()
            action.id == ACTION_LAST -> episodes.lastOrNull()?.let(::openEpisode)
            action.id == ACTION_CONTINUE -> {
                val history = localRepository.getHistory().firstOrNull {
                    it.provider == providerId && it.animeId == animeId && !it.isCompleted
                } ?: return
                episodes.firstOrNull { it.id == history.episodeId }?.let(::openEpisode)
            }
            action.id >= ACTION_RANGE_BASE -> {
                val chunk = (action.id - ACTION_RANGE_BASE).toInt()
                val start = chunk * RANGE_SIZE
                val end = minOf(start + RANGE_SIZE, episodes.size)
                if (start in 0 until end) showEpisodes((start until end).toList())
            }
            else -> episodes.getOrNull(action.id.toInt())?.let(::openEpisode)
        }
    }

    private fun openEpisode(episode: ProviderEpisode) {
        guidedRouter.replace(
            ProviderSourcesGuidedScreen(
                providerId = providerId.wireId,
                animeId = animeId,
                episodeId = episode.id,
                replacePlayer = replacePlayer,
                currentSourceId = null,
            )
        )
    }
}
