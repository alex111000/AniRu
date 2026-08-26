package ru.radiationx.anilibria.screen.launcher

import androidx.lifecycle.viewModelScope
import com.github.terrakok.cicerone.Router
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.radiationx.anilibria.screen.AnimeVostDetailsScreen
import ru.radiationx.anilibria.screen.ConfigScreen
import ru.radiationx.anilibria.screen.DetailsScreen
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.anilibria.screen.MainPagesScreen
import ru.radiationx.anilibria.screen.ProviderDetailsScreen
import ru.radiationx.data.datasource.remote.address.ApiConfig
import ru.radiationx.data.entity.domain.types.ReleaseId
import javax.inject.Inject

class AppLauncherViewModel @Inject constructor(
    private val apiConfig: ApiConfig,
    private val router: Router,
) : LifecycleViewModel() {

    private var firstLaunch = true

    val appReadyState = MutableStateFlow<Unit?>(null)

    fun openRelease(id: ReleaseId) {
        router.navigateTo(DetailsScreen(id))
    }

    fun openAnimeVost(animeUrl: String) {
        if (animeUrl.isNotBlank()) {
            router.navigateTo(AnimeVostDetailsScreen(animeUrl))
        }
    }

    fun openProvider(providerId: String, animeId: String) {
        if (providerId.isNotBlank() && animeId.isNotBlank()) {
            router.navigateTo(ProviderDetailsScreen(providerId, animeId))
        }
    }

    fun coldLaunch() {
        initWithConfig()
    }

    private fun initWithConfig() {
        apiConfig
            .observeNeedConfig()
            .distinctUntilChanged()
            .onEach {
                if (it) {
                    router.newRootScreen(ConfigScreen())
                } else if (firstLaunch) {
                    initMain()
                }
            }
            .launchIn(viewModelScope)

        if (apiConfig.needConfig) {
            router.newRootScreen(ConfigScreen())
        } else {
            initMain()
        }
    }

    private fun initMain() {
        firstLaunch = false
        router.newRootScreen(MainPagesScreen())
        appReadyState.value = Unit
    }
}
