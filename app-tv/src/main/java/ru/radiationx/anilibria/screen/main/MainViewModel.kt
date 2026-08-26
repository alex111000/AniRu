package ru.radiationx.anilibria.screen.main

import ru.radiationx.anilibria.common.BaseRowsViewModel
import javax.inject.Inject

class MainViewModel @Inject constructor() : BaseRowsViewModel() {

    companion object {
        const val FEED_ROW_ID = 1L
        const val SCHEDULE_ROW_ID = 2L
        const val FAVORITE_ROW_ID = 3L
        const val YOUTUBE_ROW_ID = 4L
        const val ANIMEVOST_ROW_ID = 5L
        const val PROVIDERS_ROW_ID = 6L
    }

    override val rowIds: List<Long> =
        listOf(FEED_ROW_ID, ANIMEVOST_ROW_ID, PROVIDERS_ROW_ID, SCHEDULE_ROW_ID, YOUTUBE_ROW_ID)

    override val availableRows: MutableSet<Long> = rowIds.toMutableSet()
}
