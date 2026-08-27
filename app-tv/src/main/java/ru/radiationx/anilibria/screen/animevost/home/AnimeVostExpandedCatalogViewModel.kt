package ru.radiationx.anilibria.screen.animevost.home

import androidx.lifecycle.viewModelScope
import com.animevost.sdk.model.AnimePage
import com.animevost.sdk.model.AnimePreview
import com.animevost.sdk.model.NavigationData
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
import timber.log.Timber
import javax.inject.Inject

data class AnimeVostCategoryDefinition(val title: String, val path: String)

enum class AnimeVostCategoryLoadState { NOT_LOADED, LOADING, LOADED, EMPTY, ERROR }

data class AnimeVostCategoryRowState(
    val id: Long,
    val title: String,
    val path: String?,
    val cards: List<CardItem>,
    val loadState: AnimeVostCategoryLoadState,
    val currentPage: Int = 0,
    val totalPages: Int = 1,
)

class AnimeVostExpandedCatalogViewModel internal constructor(
    private val source: AnimeVostCatalogSource,
    private val navigate: (LibriaCard) -> Unit,
) : LifecycleViewModel() {

    @Inject constructor(repository: AnimeVostRepository, cardRouter: LibriaCardRouter) :
        this(repository, cardRouter::navigate)

    companion object {
        const val CATEGORY_ROW_ID_BASE = 10_000L
        private const val NAVIGATION_ROW_ID = CATEGORY_ROW_ID_BASE
    }

    val rowsData = MutableStateFlow<List<AnimeVostCategoryRowState>>(emptyList())
    private var navigationJob: Job? = null
    private var categoryWorker: Job? = null
    private var activeRowId: Long? = null
    private val pendingRows = mutableListOf<Long>()

    override fun onColdCreate() {
        super.onColdCreate()
        loadNavigation()
    }

    fun onRowSelected(rowId: Long) {
        // One background request at a time; the visible row goes next, not last.
        if (pendingRows.remove(rowId)) pendingRows.add(0, rowId)
    }

    fun onLoadingCardClick(rowId: Long) {
        val row = rowsData.value.firstOrNull { it.id == rowId } ?: return
        if (row.path == null) {
            loadNavigation(forceRefresh = true)
        } else if (activeRowId != rowId) {
            pendingRows.remove(rowId)
            pendingRows.add(0, rowId)
            startWorker()
        }
    }

    fun onLinkCardClick(rowId: Long) {
        rowsData.value.firstOrNull { it.id == rowId }?.toCatalogCard()?.let(navigate)
    }

    fun onLibriaCardClick(card: LibriaCard) = navigate(card)

    private fun loadNavigation(forceRefresh: Boolean = false) {
        if (navigationJob?.isActive == true) return
        if (rowsData.value.none { it.path != null }) {
            rowsData.value = listOf(navigationRow(LoadingCard("Загрузка категорий")))
        }
        navigationJob = viewModelScope.launch {
            try {
                // Restore every saved preview before waiting for any network request.
                val cached = source.getCachedNavigation()
                if (cached != null) showCategories(cached)
                val fresh = source.getNavigation(forceRefresh)
                if (cached == null || fresh != cached) showCategories(fresh)
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                Timber.e(error)
                if (rowsData.value.none { it.path != null }) {
                    rowsData.value = listOf(navigationRow(errorCard(error), AnimeVostCategoryLoadState.ERROR))
                }
            }
        }
    }

    private suspend fun showCategories(navigation: NavigationData) {
        val definitions = navigation.toCategoryDefinitions()
        check(definitions.isNotEmpty()) { "Список категорий пуст" }
        categoryWorker?.cancelAndJoin()
        categoryWorker = null
        pendingRows.clear()
        activeRowId = null
        rowsData.value = definitions.mapIndexed { index, definition ->
            val row = AnimeVostCategoryRowState(
                id = CATEGORY_ROW_ID_BASE + index + 1,
                title = definition.title,
                path = definition.path,
                cards = listOf(LinkCard("Открыть категорию")),
                loadState = AnimeVostCategoryLoadState.NOT_LOADED,
            )
            source.getCachedCatalog(definition.path)?.let { row.withPage(it) } ?: row
        }
        pendingRows.addAll(rowsData.value.toInitialCategoryLoadQueue())
        startWorker()
    }

    private fun startWorker() {
        if (categoryWorker?.isActive == true) return
        categoryWorker = viewModelScope.launch {
            while (pendingRows.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val rowId = pendingRows.removeAt(0)
                val row = rowsData.value.firstOrNull { it.id == rowId } ?: continue
                val path = row.path ?: continue
                activeRowId = rowId
                if (row.cards.none { it is LibriaCard }) {
                    updateRow(rowId) {
                        it.copy(
                            cards = listOf(LoadingCard("Загрузка аниме"), LinkCard("Открыть категорию")),
                            loadState = AnimeVostCategoryLoadState.LOADING,
                        )
                    }
                }
                try {
                    val page = source.getCatalog(path = path)
                    updateRow(rowId) { it.withPage(page) }
                } catch (error: Exception) {
                    // A nested request timeout must not strand this row or stop the queue.
                    currentCoroutineContext().ensureActive()
                    Timber.e(error)
                    updateRow(rowId) {
                        it.copy(
                            cards = it.cards.filterIsInstance<LibriaCard>() +
                                errorCard(error) + LinkCard("Открыть категорию"),
                            loadState = AnimeVostCategoryLoadState.ERROR,
                        )
                    }
                } finally {
                    activeRowId = null
                }
            }
        }
    }

    private fun navigationRow(card: CardItem, state: AnimeVostCategoryLoadState = AnimeVostCategoryLoadState.LOADING) =
        AnimeVostCategoryRowState(NAVIGATION_ROW_ID, "Каталог AnimeVost", null, listOf(card), state)

    private fun errorCard(error: Exception) = LoadingCard(
        "Повторить загрузку", "Не удалось загрузить: ${error.message}", isError = true,
    )

    private fun updateRow(rowId: Long, transform: (AnimeVostCategoryRowState) -> AnimeVostCategoryRowState) {
        rowsData.value = rowsData.value.map { if (it.id == rowId) transform(it) else it }
    }
}

internal fun AnimeVostCategoryRowState.withPage(page: AnimePage): AnimeVostCategoryRowState {
    val anime = page.items.map { it.toAnimeVostCard() }.distinctBy { it.getId() }
    return copy(
        // Always open the complete category, including single-page categories.
        cards = anime + LinkCard(if (anime.isEmpty()) "Нет аниме — открыть категорию" else "Загрузить ещё"),
        loadState = if (anime.isEmpty()) AnimeVostCategoryLoadState.EMPTY else AnimeVostCategoryLoadState.LOADED,
        currentPage = page.currentPage,
        totalPages = page.totalPages,
    )
}

internal fun AnimeVostCategoryRowState.toCatalogCard(): LibriaCard? = path?.let {
    LibriaCard(title, "", "", LibriaCard.Type.AnimeVostCatalog(it, title))
}

internal fun List<AnimeVostCategoryRowState>.toInitialCategoryLoadQueue(): List<Long> =
    filter { it.path != null }.map { it.id }

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
