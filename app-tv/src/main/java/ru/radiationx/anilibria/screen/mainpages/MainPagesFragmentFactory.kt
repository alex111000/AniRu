package ru.radiationx.anilibria.screen.mainpages

import androidx.fragment.app.Fragment
import androidx.leanback.widget.Row
import ru.radiationx.anilibria.common.CachedRowsFragmentFactory
import ru.radiationx.anilibria.screen.history.HistoryFragment
import ru.radiationx.anilibria.screen.provider.catalog.UnifiedCatalogFragment

class MainPagesFragmentFactory : CachedRowsFragmentFactory() {

    companion object {
        const val ID_MAIN = 1L
        const val ID_ANIMEVOST = 2L
        const val ID_ANILIBRIA = 3L

        val ids = listOf(
            ID_MAIN,
            ID_ANIMEVOST,
            ID_ANILIBRIA,
        )

        val variant1 = mapOf(
            ID_MAIN to "Главное",
            ID_ANIMEVOST to "Фильмы",
            ID_ANILIBRIA to "Сериалы",
        )
    }

    override fun getFragmentByRow(row: Row): Fragment = when (row.id) {
        ID_MAIN -> HistoryFragment.newHomeInstance()
        ID_ANIMEVOST -> UnifiedCatalogFragment.newInstance("MOVIE")
        ID_ANILIBRIA -> UnifiedCatalogFragment.newInstance("SERIES")
        else -> super.getFragmentByRow(row)
    }
}
