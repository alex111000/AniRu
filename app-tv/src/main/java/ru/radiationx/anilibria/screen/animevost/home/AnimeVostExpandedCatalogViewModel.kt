package ru.radiationx.anilibria.screen.animevost.home

import androidx.lifecycle.viewModelScope
import com.animevost.sdk.model.AnimePreview
import com.animevost.sdk.model.NavigationData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import ru.radiationx.anilibria.animevost.AnimeVostRepository
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.screen.LifecycleViewModel
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

data class AnimeVostCategoryDefinition(
    val title: String,
    val path: String,
)

enum class AnimeVostCategoryLoadState {
    NOT_LOADED,
    LOADING,
    LOADED,
    EMPTY,
    ERROR,
}

data class AnimeVostCategoryRowState(
    val id: Long,
    val title: String,
    val path: String?,
    val cards: List<CardItem>,
    val loadState: AnimeVostCategoryLoadState,
    val currentPage: Int = 0,
    val totalPages: Int = 1,
)

class AnimeVostExpandedCatalogViewModel @Inject constructor(
    private val repository: AnimeVostRepository,
    private val cardRouter: LibriaCardRouter,
) : LifecycleViewModel() {

    companion object {
        const val CATEGORY_ROW_ID_BASE = 10_000L
        private const val NAVIGATION_ROW_ID = CATEGORY_ROW_ID_BASE
        private const val INITIAL_PREFETCH_COUNT = 2
        private const val MAX_CONCURRENT_CATEGORY_LOADS = 3
    }

    val rowsData = MutableStateFlow<List<AnimeVostCategoryRowState>>(emptyList())

    private var navigationJob: Job? = null
    private val categoryJobs = ConcurrentHashMap<Long, Job>()
    private val categoryLoadSemaphore = Semaphore(MAX_CONCURRENT_CATEGORY_LOADS)

    override fun onColdCreate() {
        super.onColdCreate()
        loadNavigation()
    }

    fun onRowSelected(rowId: Long) {
        val row = rowsData.value.firstOrNull { it.id == rowId } ?: return
        if (row.shouldLoadOnSelection(categoryJobs.keys)) {
            loadCategory(rowId, page = 1)
        }
    }

    fun onLoadingCardClick(rowId: Long) {
        val row = rowsData.value.firstOrNull { it.id == rowId } ?: return
        if (row.path == null) {
            loadNavigation(forceRefresh = true)
        } else {
            val retryPage = if (
                row.currentPage == 0 || row.loadState == AnimeVostCategoryLoadState.EMPTY
            ) 1 else row.currentPage + 1
            loadCategory(rowId, page = retryPage)
        }
    }

    fun onLinkCardBind(rowId: Long) {
        val row = rowsData.value.firstOrNull { it.id == rowId } ?: return
        if (row.loadState == AnimeVostCategoryLoadState.LOADED && row.currentPage < row.totalPages) {
            loadCategory(rowId, page = row.currentPage + 1)
        }
    }

    fun onLinkCardClick(rowId: Long) = onLinkCardBind(rowId)

    fun onLibriaCardClick(card: LibriaCard) {
        cardRouter.navigate(card)
    }

    private fun loadNavigation(forceRefresh: Boolean = false) {
        if (navigationJob?.isActive == true) return
        categoryJobs.values.forEach { it.cancel() }
        categoryJobs.clear()

        rowsData.value = listOf(
            AnimeVostCategoryRowState(
                id = NAVIGATION_ROW_ID,
                title = "Каталог AnimeVost",
                path = null,
                cards = listOf(LoadingCard("Загрузка категорий")),
                loadState = AnimeVostCategoryLoadState.LOADING,
            )
        )
        navigationJob = viewModelScope.launch {
            try {
                val definitions = withContext(Dispatchers.IO) {
                    repository.getNavigation(forceRefresh).toCategoryDefinitions()
                }
                val categoryRows = definitions.mapIndexed { index, definition ->
                    AnimeVostCategoryRowState(
                        id = CATEGORY_ROW_ID_BASE + index + 1,
                        title = definition.title,
                        path = definition.path,
                        cards = listOf(LoadingCard("Загрузка аниме")),
                        loadState = AnimeVostCategoryLoadState.NOT_LOADED,
                    )
                }
                rowsData.value = categoryRows
                categoryRows.take(INITIAL_PREFETCH_COUNT).forEach { row ->
                    loadCategory(row.id, page = 1)
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                Timber.e(error)
                rowsData.value = listOf(
                    AnimeVostCategoryRowState(
                        id = NAVIGATION_ROW_ID,
                        title = "Каталог AnimeVost",
                        path = null,
                        cards = listOf(
                            LoadingCard(
                                title = "Повторить загрузку",
                                description = "Не удалось загрузить категории: ${error.message}",
                                isError = true,
                            )
                        ),
                        loadState = AnimeVostCategoryLoadState.ERROR,
                    )
                )
            }
        }
    }

    private fun loadCategory(rowId: Long, page: Int) {
        val row = rowsData.value.firstOrNull { it.id == rowId } ?: return
        val path = row.path ?: return
        if (categoryJobs[rowId]?.isActive == true) return

        val existingCards = row.cards.filterIsInstance<LibriaCard>()

        updateRow(rowId) {
            it.copy(
                cards = existingCards + LoadingCard("Загрузка аниме"),
                loadState = AnimeVostCategoryLoadState.LOADING,
            )
        }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            categoryLoadSemaphore.withPermit {
                try {
                    val result = withContext(Dispatchers.IO) {
                        repository.getCatalog(page = page, path = path)
                    }
                    val newCards = result.items.map { it.toAnimeVostCard() }
                    val mergedCards = (if (page <= 1) newCards else existingCards + newCards)
                        .distinctBy { it.getId() }
                    updateRow(rowId) {
                        it.copy(
                            cards = when {
                                mergedCards.isEmpty() -> listOf(
                                    LoadingCard("Нет данных", "Нажмите, чтобы повторить")
                                )
                                result.currentPage < result.totalPages -> mergedCards +
                                    LinkCard("Загрузить еще")
                                else -> mergedCards
                            },
                            loadState = if (mergedCards.isEmpty()) {
                                AnimeVostCategoryLoadState.EMPTY
                            } else {
                                AnimeVostCategoryLoadState.LOADED
                            },
                            currentPage = result.currentPage,
                            totalPages = result.totalPages,
                        )
                    }
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    Timber.e(error)
                    updateRow(rowId) {
                        it.copy(
                            cards = existingCards +
                                LoadingCard(
                                    title = "Повторить загрузку",
                                    description = "Не удалось загрузить категорию: ${error.message}",
                                    isError = true,
                                ),
                            loadState = AnimeVostCategoryLoadState.ERROR,
                        )
                    }
                }
            }
        }
        categoryJobs[rowId] = job
        job.invokeOnCompletion {
            categoryJobs.remove(rowId, job)
        }
        job.start()
    }

    private fun updateRow(
        rowId: Long,
        transform: (AnimeVostCategoryRowState) -> AnimeVostCategoryRowState,
    ) {
        rowsData.value = rowsData.value.map { row ->
            if (row.id == rowId) transform(row) else row
        }
    }
}

internal fun AnimeVostCategoryRowState.shouldLoadOnSelection(activeRowIds: Set<Long>): Boolean =
    path != null && (
        loadState == AnimeVostCategoryLoadState.NOT_LOADED ||
            (loadState == AnimeVostCategoryLoadState.LOADING && id !in activeRowIds)
        )

internal fun NavigationData.toCategoryDefinitions(): List<AnimeVostCategoryDefinition> = buildList {
    sections.forEach { add(AnimeVostCategoryDefinition(it.title, it.path)) }
    genres.forEach { add(AnimeVostCategoryDefinition(it.title, it.path)) }
    types.forEach { add(AnimeVostCategoryDefinition(it.title, it.path)) }
    years.forEach { add(AnimeVostCategoryDefinition(it.title, it.path)) }
}.distinctBy { it.path }

internal fun AnimePreview.toAnimeVostCard(): LibriaCard = LibriaCard(
    title = title,
    description = buildString {
        originalTitle?.takeIf { it.isNotBlank() }?.let { append(it) }
        episodeInfo?.takeIf { it.isNotBlank() }?.let {
            if (isNotEmpty()) append(" • ")
            append(it)
        }
        rating?.let {
            if (isNotEmpty()) append(" • ")
            append("★ ")
            append(it)
        }
    },
    image = posterUrl.orEmpty(),
    type = LibriaCard.Type.AnimeVost(url),
)
