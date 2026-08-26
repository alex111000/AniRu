package ru.radiationx.anilibria.screen.provider.episodes

import android.os.Bundle
import android.view.View
import androidx.leanback.widget.GuidedAction
import androidx.lifecycle.lifecycleScope
import com.github.terrakok.cicerone.Router
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.common.fragment.FakeGuidedStepFragment
import ru.radiationx.anilibria.common.fragment.GuidedRouter
import ru.radiationx.anilibria.provider.ProviderId
import ru.radiationx.anilibria.provider.ProviderRegistry
import ru.radiationx.anilibria.provider.ProviderSource
import ru.radiationx.anilibria.screen.ProviderPlayerScreen
import ru.radiationx.quill.get

class ProviderSourcesGuidedFragment : FakeGuidedStepFragment() {
    companion object {
        private const val ARG_PROVIDER = "provider_sources_provider"
        private const val ARG_ANIME = "provider_sources_anime"
        private const val ARG_EPISODE = "provider_sources_episode"
        private const val ARG_REPLACE = "provider_sources_replace"
        private const val ARG_CURRENT = "provider_sources_current"
        private const val ACTION_AUTO = 90_000L

        fun newInstance(providerId: String, animeId: String, episodeId: String, replacePlayer: Boolean, currentSourceId: String?) =
            ProviderSourcesGuidedFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PROVIDER, providerId)
                    putString(ARG_ANIME, animeId)
                    putString(ARG_EPISODE, episodeId)
                    putBoolean(ARG_REPLACE, replacePlayer)
                    putString(ARG_CURRENT, currentSourceId)
                }
            }
    }

    private val registry by lazy { get<ProviderRegistry>() }
    private val guidedRouter by lazy { get<GuidedRouter>() }
    private val router by lazy { get<Router>() }
    private val providerId by lazy { requireNotNull(ProviderId.fromWireId(requireArguments().getString(ARG_PROVIDER).orEmpty())) }
    private val animeId by lazy { requireArguments().getString(ARG_ANIME).orEmpty() }
    private val episodeId by lazy { requireArguments().getString(ARG_EPISODE).orEmpty() }
    private val replacePlayer by lazy { requireArguments().getBoolean(ARG_REPLACE, false) }
    private val currentSourceId by lazy { requireArguments().getString(ARG_CURRENT) }
    private var sources: List<ProviderSource> = emptyList()

    override fun onProvideTheme(): Int = R.style.AppTheme_Player_LeanbackWizard

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        showInfo("Загрузка озвучек и источников…")
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { registry.get(providerId).getSources(animeId, episodeId) }
                .onSuccess { loaded ->
                    sources = loaded
                    if (loaded.isEmpty()) {
                        actions = listOf(
                            GuidedAction.Builder(requireContext()).id(ACTION_AUTO)
                                .title("Автоматический резервный источник")
                                .description("AniRu попробует другой доступный provider")
                                .build()
                        )
                    } else {
                        actions = loaded.mapIndexed { index, source ->
                            val max = source.streams.maxOfOrNull { it.quality }?.takeIf { it > 0 }
                            GuidedAction.Builder(requireContext()).id(index.toLong())
                                .title(source.title.ifBlank { providerId.uiName })
                                .description(listOfNotNull(
                                    source.player.takeIf { it.isNotBlank() },
                                    max?.let { "до ${it}p" },
                                    "${source.streams.size} вариантов",
                                ).joinToString(" • ")).build()
                        }
                        val pos = loaded.indexOfFirst { it.id == currentSourceId }
                        if (pos >= 0) selectedActionPosition = pos
                    }
                }
                .onFailure { showInfo("Не удалось загрузить источники", it.message) }
        }
    }

    private fun showInfo(title: String, description: String? = null) {
        actions = listOf(GuidedAction.Builder(requireContext()).id(-1).title(title).description(description)
            .enabled(false).focusable(false).infoOnly(true).build())
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        val sourceId = if (action.id == ACTION_AUTO) null else sources.getOrNull(action.id.toInt())?.id ?: return
        guidedRouter.close()
        val screen = ProviderPlayerScreen(providerId.wireId, animeId, episodeId, sourceId)
        if (replacePlayer) router.replaceScreen(screen) else router.navigateTo(screen)
    }
}
