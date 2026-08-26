package ru.radiationx.anilibria.screen.mainpages

import androidx.fragment.app.Fragment
import androidx.leanback.widget.Row
import ru.radiationx.anilibria.common.CachedRowsFragmentFactory
import ru.radiationx.anilibria.screen.anilibria.AniLibriaFragment
import ru.radiationx.anilibria.screen.animevost.home.AnimeVostFragment
import ru.radiationx.anilibria.screen.history.HistoryFragment
import ru.radiationx.anilibria.screen.main.MainFragment

class MainPagesFragmentFactory : CachedRowsFragmentFactory() {

    companion object {
        const val ID_MAIN = 1L
        const val ID_ANIMEVOST = 2L
        const val ID_ANILIBRIA = 3L
        const val ID_HISTORY = 4L

        val ids = listOf(
            ID_MAIN,
            ID_ANIMEVOST,
            ID_ANILIBRIA,
            ID_HISTORY,
        )

        val variant1 = mapOf(
            ID_MAIN to "Главное",
            ID_ANIMEVOST to "От AnimeVost",
            ID_ANILIBRIA to "От AniLibria",
            ID_HISTORY to "Я смотрю",
        )
    }

    override fun getFragmentByRow(row: Row): Fragment = when (row.id) {
        ID_MAIN -> MainFragment()
        ID_ANIMEVOST -> AnimeVostFragment()
        ID_ANILIBRIA -> AniLibriaFragment()
        ID_HISTORY -> HistoryFragment()
        else -> super.getFragmentByRow(row)
    }
}
