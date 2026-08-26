package ru.radiationx.anilibria.screen.suggestions

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.radiationx.anilibria.common.BaseRowsViewModel
import javax.inject.Inject

class SuggestionsRowsViewModel @Inject constructor(
    suggestionsController: SuggestionsController,
) : BaseRowsViewModel() {
    companion object {
        const val ANILIBRIA_RESULT_ROW_ID = 1L
        const val RECOMMENDS_ROW_ID = 2L
        const val ANIMEVOST_RESULT_ROW_ID = 3L
        const val YUMMY_RESULT_ROW_ID = 4L
        const val SAMEBAND_RESULT_ROW_ID = 5L
    }

    val emptyResultState = MutableStateFlow(false)
    override val rowIds: List<Long> = listOf(
        ANILIBRIA_RESULT_ROW_ID,
        ANIMEVOST_RESULT_ROW_ID,
        YUMMY_RESULT_ROW_ID,
        SAMEBAND_RESULT_ROW_ID,
        RECOMMENDS_ROW_ID,
    )
    override val availableRows: MutableSet<Long> = mutableSetOf(RECOMMENDS_ROW_ID)

    init {
        suggestionsController.resultEvent.onEach { result ->
            emptyResultState.value = result.validQuery && result.isEmpty
            updateAvailableRow(ANILIBRIA_RESULT_ROW_ID, result.validQuery && result.aniLibria.isNotEmpty())
            updateAvailableRow(ANIMEVOST_RESULT_ROW_ID, result.validQuery && result.animeVost.isNotEmpty())
            updateAvailableRow(YUMMY_RESULT_ROW_ID, result.validQuery && result.yummyAnime.isNotEmpty())
            updateAvailableRow(SAMEBAND_RESULT_ROW_ID, result.validQuery && result.sameBand.isNotEmpty())
            updateAvailableRow(RECOMMENDS_ROW_ID, !result.validQuery && result.isEmpty)
        }.launchIn(viewModelScope)
    }
}
