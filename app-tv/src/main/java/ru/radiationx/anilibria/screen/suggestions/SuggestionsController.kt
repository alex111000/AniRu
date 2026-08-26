package ru.radiationx.anilibria.screen.suggestions

import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.shared.ktx.EventFlow
import javax.inject.Inject

class SuggestionsController @Inject constructor() {
    val resultEvent = EventFlow<SearchResult>()

    data class SearchResult(
        val aniLibria: List<LibriaCard>,
        val animeVost: List<LibriaCard>,
        val yummyAnime: List<LibriaCard>,
        val sameBand: List<LibriaCard>,
        val query: String,
        val validQuery: Boolean,
    ) {
        val isEmpty: Boolean
            get() = aniLibria.isEmpty() && animeVost.isEmpty() && yummyAnime.isEmpty() && sameBand.isEmpty()
    }
}
