package ru.radiationx.anilibria.screen.suggestions

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.anilibria.search.UnifiedSearchRepository
import javax.inject.Inject

class SuggestionsResultViewModel @Inject constructor(
    private val unifiedSearchRepository: UnifiedSearchRepository,
    private val cardRouter: LibriaCardRouter,
    private val suggestionsController: SuggestionsController,
) : LifecycleViewModel() {
    val progressState = MutableStateFlow(false)
    val aniLibriaData = MutableStateFlow<List<LibriaCard>>(emptyList())
    val animeVostData = MutableStateFlow<List<LibriaCard>>(emptyList())
    val yummyAnimeData = MutableStateFlow<List<LibriaCard>>(emptyList())
    val sameBandData = MutableStateFlow<List<LibriaCard>>(emptyList())

    private var searchJob: Job? = null
    private var latestQuery: String = ""

    fun onQueryChange(query: String) {
        latestQuery = query.trim()
        searchJob?.cancel()
        if (latestQuery.length < MIN_QUERY_LENGTH) {
            progressState.value = false
            clearRows()
            suggestionsController.resultEvent.emit(
                SuggestionsController.SearchResult(emptyList(), emptyList(), emptyList(), emptyList(), latestQuery, false)
            )
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val querySnapshot = latestQuery
            progressState.value = true
            try {
                val result = unifiedSearchRepository.search(querySnapshot)
                if (querySnapshot != latestQuery) return@launch
                aniLibriaData.value = result.aniLibria
                animeVostData.value = result.animeVost
                yummyAnimeData.value = result.yummyAnime
                sameBandData.value = result.sameBand
                suggestionsController.resultEvent.emit(
                    SuggestionsController.SearchResult(
                        result.aniLibria, result.animeVost, result.yummyAnime, result.sameBand,
                        querySnapshot, true,
                    )
                )
            } finally {
                if (querySnapshot == latestQuery) progressState.value = false
            }
        }
    }

    fun onCardClick(item: LibriaCard) = cardRouter.navigate(item)

    private fun clearRows() {
        aniLibriaData.value = emptyList()
        animeVostData.value = emptyList()
        yummyAnimeData.value = emptyList()
        sameBandData.value = emptyList()
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 2
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
