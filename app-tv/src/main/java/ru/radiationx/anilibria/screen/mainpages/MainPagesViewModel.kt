package ru.radiationx.anilibria.screen.mainpages

import com.github.terrakok.cicerone.Router
import kotlinx.coroutines.flow.MutableStateFlow
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.anilibria.screen.CatalogSourcesGuidedScreen
import ru.radiationx.anilibria.screen.SuggestionsScreen
import javax.inject.Inject

class MainPagesViewModel @Inject constructor(
    private val router: Router,
) : LifecycleViewModel() {

    // AniRu is a personal fork; upstream AniLiberty update checks are intentionally disabled.
    val hasUpdatesData = MutableStateFlow(false)

    fun onAppUpdateClick() {
        // No-op for personal AniRu builds.
    }

    fun onCatalogClick() {
        router.navigateTo(ru.radiationx.anilibria.screen.UnifiedCatalogScreen("SERIES"))
    }

    fun onSearchClick() {
        router.navigateTo(ru.radiationx.anilibria.screen.UnifiedCatalogScreen("SEARCH"))
    }
}
