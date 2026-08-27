package ru.radiationx.anilibria.screen.mainpages

import androidx.fragment.app.Fragment
import androidx.leanback.widget.Row
import ru.radiationx.anilibria.common.CachedRowsFragmentFactory
import ru.radiationx.anilibria.screen.anilibria.AniLibriaFragment
import ru.radiationx.anilibria.screen.animevost.home.AnimeVostFragment
import ru.radiationx.anilibria.screen.history.HistoryFragment
import ru.radiationx.anilibria.screen.main.MainFragment
import ru.radiationx.anilibria.screen.provider.catalog.UnifiedCatalogFragment
import ru.radiationx.anilibria.screen.provider.catalog.ProviderSettingsFragment

class MainPagesFragmentFactory : CachedRowsFragmentFactory() {

    companion object {
        const val ID_MAIN = 1L
        const val ID_ANIMEVOST = 2L
        const val ID_ANILIBRIA = 3L
        const val ID_HISTORY = 4L
        const val ID_SETTINGS = 5L

        val ids = listOf(
            ID_MAIN,
            ID_ANIMEVOST,
            ID_ANILIBRIA,
            ID_HISTORY,
            ID_SETTINGS,
        )

        val variant1 = mapOf(
            ID_MAIN to "Главное",
            ID_ANIMEVOST to "Фильмы",
            ID_ANILIBRIA to "Сериалы",
            ID_HISTORY to "Поиск",
            ID_SETTINGS to "Настройки",
        )
    }

    override fun getFragmentByRow(row: Row): Fragment = when (row.id) {
        ID_MAIN -> HistoryFragment.newHomeInstance()
        ID_ANIMEVOST -> UnifiedCatalogFragment.newInstance("MOVIE")
        ID_ANILIBRIA -> UnifiedCatalogFragment.newInstance("SERIES")
        ID_HISTORY -> UnifiedCatalogFragment.newInstance("SEARCH")
        ID_SETTINGS -> ProviderSettingsFragment()
        else -> super.getFragmentByRow(row)
    }
}
