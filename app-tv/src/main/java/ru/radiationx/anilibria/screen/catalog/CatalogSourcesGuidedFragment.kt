package ru.radiationx.anilibria.screen.catalog

import android.os.Bundle
import androidx.leanback.widget.GuidanceStylist.Guidance
import androidx.leanback.widget.GuidedAction
import com.github.terrakok.cicerone.Router
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.common.fragment.FakeGuidedStepFragment
import ru.radiationx.anilibria.common.fragment.GuidedRouter
import ru.radiationx.anilibria.provider.ProviderId
import ru.radiationx.anilibria.screen.AnimeVostCatalogScreen
import ru.radiationx.anilibria.screen.ProviderCatalogScreen
import ru.radiationx.anilibria.screen.SearchScreen
import ru.radiationx.quill.inject

/** Choose a catalog. Search itself always queries all configured providers. */
class CatalogSourcesGuidedFragment : FakeGuidedStepFragment() {
    private val guidedRouter by inject<GuidedRouter>()
    private val router by inject<Router>()

    override fun onProvideTheme(): Int = R.style.AppTheme_Player_LeanbackWizard

    override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance = Guidance(
        "Каталог",
        "Выберите источник. Поиск по названию ищет сразу во всех источниках.",
        "AniRu",
        null,
    )

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        actions += action(ACTION_ANILIBRIA, "От AniLibria", "Год, сезон, жанр и статус")
        actions += action(ACTION_ANIMEVOST, "От AnimeVost", "Полный каталог AnimeVost")
        actions += action(ACTION_YUMMY, "От YummyAnime", "Каталог с несколькими озвучками")
        actions += action(ACTION_SAMEBAND, "От SameBand", "Новые релизы SameBand")
    }

    private fun action(id: Long, title: String, description: String) = GuidedAction.Builder(requireContext())
        .id(id).title(title).description(description).build()

    override fun onGuidedActionClicked(action: GuidedAction) {
        val screen = when (action.id) {
            ACTION_ANILIBRIA -> SearchScreen()
            ACTION_ANIMEVOST -> AnimeVostCatalogScreen(null, "Каталог AnimeVost")
            ACTION_YUMMY -> ProviderCatalogScreen(ProviderId.YUMMY_ANIME.wireId, "Каталог YummyAnime")
            ACTION_SAMEBAND -> ProviderCatalogScreen(ProviderId.SAMEBAND.wireId, "Новинки SameBand")
            else -> return
        }
        guidedRouter.finishGuidedChain()
        router.navigateTo(screen)
    }

    private companion object {
        const val ACTION_ANILIBRIA = 1L
        const val ACTION_ANIMEVOST = 2L
        const val ACTION_YUMMY = 3L
        const val ACTION_SAMEBAND = 4L
    }
}
