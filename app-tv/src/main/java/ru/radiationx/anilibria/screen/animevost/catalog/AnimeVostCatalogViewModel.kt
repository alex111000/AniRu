package ru.radiationx.anilibria.screen.animevost.catalog

import androidx.lifecycle.viewModelScope
import com.animevost.sdk.model.AnimePage
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.animevost.AnimeVostCatalogSource
import ru.radiationx.anilibria.animevost.AnimeVostRepository
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.anilibria.screen.animevost.home.toAnimeVostCard
import ru.radiationx.quill.QuillExtra
import javax.inject.Inject

data class AnimeVostCatalogExtra(val path: String?, val title: String) : QuillExtra

class AnimeVostCatalogViewModel internal constructor(
    private val extra: AnimeVostCatalogExtra,
    private val source: AnimeVostCatalogSource,
    private val navigate: (LibriaCard) -> Unit,
) : LifecycleViewModel() {
    @Inject constructor(extra: AnimeVostCatalogExtra, repository: AnimeVostRepository, cardRouter: LibriaCardRouter) :
        this(extra, repository, cardRouter::navigate)

    val cardsData = MutableStateFlow<List<CardItem>>(emptyList())
    private var cards: List<LibriaCard> = emptyList()
    private var currentPage = 0
    private var totalPages = 1
    private var failedPage = 1
    private var requestJob: Job? = null
    private var pendingMore = false

    override fun onColdCreate() {
        super.onColdCreate()
        loadPage(1)
    }

    fun onLibriaCardClick(card: LibriaCard) = navigate(card)
    fun onLinkCardClick() = onLinkCardBind()
    fun onLinkCardBind() {
        if (currentPage >= totalPages) return
        if (requestJob?.isActive == true) pendingMore = true
        else loadPage(currentPage + 1)
    }

    fun onLoadingCardClick() = loadPage(failedPage)

    private fun loadPage(page: Int) {
        if (requestJob?.isActive == true) return
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            failedPage = page
            cardsData.value = cards + LoadingCard("Загрузка аниме")
            var succeeded = false
            try {
                if (page == 1 && cards.isEmpty()) {
                    source.getCachedCatalog(extra.path)?.let { showPage(it) }
                }
                showPage(source.getCatalog(page = page, path = extra.path))
                succeeded = true
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                cardsData.value = cards + LoadingCard(
                    "Повторить загрузку", "Не удалось загрузить: ${error.message}", isError = true,
                )
            } finally {
                requestJob = null
            }
            // A click while the cached first page refreshes is not lost.
            val loadNext = pendingMore && succeeded && currentPage < totalPages
            pendingMore = false
            if (loadNext) loadPage(currentPage + 1)
        }
        requestJob = job
        job.start()
    }

    private fun showPage(page: AnimePage) {
        val newCards = page.items.map { it.toAnimeVostCard() }
        cards = (if (page.currentPage == 1) newCards else cards + newCards).distinctBy { it.getId() }
        currentPage = page.currentPage
        totalPages = page.totalPages
        cardsData.value = when {
            cards.isEmpty() -> listOf(LoadingCard("Ничего не найдено", "Нажмите, чтобы повторить", isError = true))
            currentPage < totalPages -> cards + LinkCard("Загрузить ещё")
            else -> cards
        }
    }
}
