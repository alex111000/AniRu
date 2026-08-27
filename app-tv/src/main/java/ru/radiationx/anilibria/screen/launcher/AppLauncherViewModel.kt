package ru.radiationx.anilibria.screen.launcher

import com.github.terrakok.cicerone.Router
import kotlinx.coroutines.flow.MutableStateFlow
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.anilibria.screen.MainPagesScreen
import ru.radiationx.anilibria.screen.ProviderDetailsScreen
import ru.radiationx.data.entity.domain.types.ReleaseId
import javax.inject.Inject

class AppLauncherViewModel @Inject constructor(
    private val router: Router,
) : LifecycleViewModel() {


    val appReadyState = MutableStateFlow<Unit?>(null)

    fun openRelease(id: ReleaseId) {
        router.navigateTo(ProviderDetailsScreen("anilibria", id.id.toString()))
    }

    fun openAnimeVost(animeUrl: String) {
        if (animeUrl.isNotBlank()) {
            router.navigateTo(ProviderDetailsScreen("animevost", animeUrl))
        }
    }

    fun openProvider(providerId: String, animeId: String) {
        if (providerId.isNotBlank() && animeId.isNotBlank()) {
            router.navigateTo(ProviderDetailsScreen(providerId, animeId))
        }
    }

    fun coldLaunch() {
        // A single legacy AniLibria endpoint must not gate eight independent providers
        // or local Continue/Favorites. The new provider APIs configure independently.
        initMain()
    }

    private fun initMain() {
        router.newRootScreen(MainPagesScreen())
        appReadyState.value = Unit
    }
}
