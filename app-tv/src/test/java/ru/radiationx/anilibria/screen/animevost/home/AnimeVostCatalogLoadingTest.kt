package ru.radiationx.anilibria.screen.animevost.home

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.animevost.sdk.model.AnimePage
import com.animevost.sdk.model.CatalogLink
import com.animevost.sdk.model.CatalogSort
import com.animevost.sdk.model.NavigationData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import ru.radiationx.anilibria.animevost.AnimeVostCatalogSource
import ru.radiationx.anilibria.animevost.testPreview
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.screen.animevost.catalog.AnimeVostCatalogExtra
import ru.radiationx.anilibria.screen.animevost.catalog.AnimeVostCatalogViewModel
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AnimeVostCatalogLoadingTest {
    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun everyCategoryLoadsSequentiallyWithoutScrolling() = runTest {
        val source = FakeCatalogSource()
        var active = 0
        var maxActive = 0
        source.fetch = { _, _ ->
            active++
            maxActive = maxOf(maxActive, active)
            delay(100)
            active--
            page()
        }
        val vm = AnimeVostExpandedCatalogViewModel(source) {}
        vm.onCreate(owner)
        advanceUntilIdle()
        assertEquals(listOf("/a/", "/b/", "/c/"), source.requests.map { it.first })
        assertEquals(1, maxActive)
        assertTrue(vm.rowsData.value.all { it.loadState == AnimeVostCategoryLoadState.LOADED })
        vm.viewModelScope.cancel()
    }

    @Test fun selectedRowIsNextButDoesNotCancelActiveRequest() = runTest {
        val source = FakeCatalogSource()
        val release = CompletableDeferred<Unit>()
        source.fetch = { path, _ -> if (path == "/a/") release.await(); page() }
        val vm = AnimeVostExpandedCatalogViewModel(source) {}
        vm.onCreate(owner)
        runCurrent()
        vm.onRowSelected(vm.rowsData.value[2].id)
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("/a/", "/c/", "/b/"), source.requests.map { it.first })
        vm.viewModelScope.cancel()
    }

    @Test fun moreNavigatesImmediatelyToTheCorrectCategoryDuringLoading() = runTest {
        val source = FakeCatalogSource().apply { fetch = { _, _ -> awaitCancellation() } }
        val navigated = mutableListOf<LibriaCard>()
        val vm = AnimeVostExpandedCatalogViewModel(source) { navigated += it }
        vm.onCreate(owner)
        runCurrent()
        val row = vm.rowsData.value[2]
        vm.onLinkCardClick(row.id)
        assertEquals(LibriaCard.Type.AnimeVostCatalog("/c/", "Category c"), navigated.single().type)
        assertEquals(listOf("/a/" to 1), source.requests)
        vm.viewModelScope.cancel()
    }

    @Test fun cachedRowsAppearBeforeNavigationOrCategoryNetworkCompletes() = runTest {
        val source = FakeCatalogSource().apply {
            cachedNavigation = navigation
            cachedPages["/c/"] = page()
            fetchNavigation = { awaitCancellation() }
            fetch = { _, _ -> awaitCancellation() }
        }
        val vm = AnimeVostExpandedCatalogViewModel(source) {}
        vm.onCreate(owner)
        runCurrent()
        val cachedRow = vm.rowsData.value[2]
        assertEquals(AnimeVostCategoryLoadState.LOADED, cachedRow.loadState)
        assertEquals(1, cachedRow.cards.filterIsInstance<LibriaCard>().size)
        assertIs<LinkCard>(cachedRow.cards.last())
        vm.viewModelScope.cancel()
    }

    @Test fun nestedTimeoutShowsRetryAndContinuesWithNextCategory() = runTest {
        val source = FakeCatalogSource().apply {
            fetch = { path, _ ->
                if (path == "/a/") withTimeout(10) { awaitCancellation() }
                page()
            }
        }
        val vm = AnimeVostExpandedCatalogViewModel(source) {}
        vm.onCreate(owner)
        advanceUntilIdle()
        assertEquals(AnimeVostCategoryLoadState.ERROR, vm.rowsData.value.first().loadState)
        assertTrue(vm.rowsData.value.first().cards.filterIsInstance<LoadingCard>().single().isError)
        assertEquals(AnimeVostCategoryLoadState.LOADED, vm.rowsData.value.last().loadState)
        vm.viewModelScope.cancel()
    }

    @Test fun cancellingScreenStopsTheQueueInsteadOfStartingAnotherCategory() = runTest {
        val source = FakeCatalogSource().apply { fetch = { _, _ -> awaitCancellation() } }
        val vm = AnimeVostExpandedCatalogViewModel(source) {}
        vm.onCreate(owner)
        runCurrent()
        vm.viewModelScope.cancel()
        advanceUntilIdle()
        assertEquals(1, source.requests.size)
    }

    @Test fun fullGridShowsCacheThenLoadsAllPagesWithTheSameCategoryPath() = runTest {
        val source = FakeCatalogSource().apply {
            cachedPages["/c/"] = page()
            fetch = { _, number -> delay(100); page(number) }
        }
        val vm = AnimeVostCatalogViewModel(AnimeVostCatalogExtra("/c/", "Category c"), source) {}
        vm.onCreate(owner)
        runCurrent()
        assertEquals(1, vm.cardsData.value.filterIsInstance<LibriaCard>().size)
        // Clicking more while the cached page refreshes must not discard the action.
        vm.onLinkCardClick()
        advanceUntilIdle()
        assertEquals(listOf("/c/" to 1, "/c/" to 2), source.requests)
        assertEquals(2, vm.cardsData.value.filterIsInstance<LibriaCard>().size)
        vm.onLinkCardClick()
        advanceUntilIdle()
        assertEquals(3, vm.cardsData.value.filterIsInstance<LibriaCard>().size)
        assertFalse(vm.cardsData.value.any { it is LinkCard || it is LoadingCard })
        vm.viewModelScope.cancel()
    }

    @Test fun fullGridRetriesTheFailedPageWithoutLosingPreviousCards() = runTest {
        val source = FakeCatalogSource()
        var fail = true
        source.fetch = { _, number ->
            if (number == 2 && fail) throw IOException("offline")
            page(number)
        }
        val vm = AnimeVostCatalogViewModel(AnimeVostCatalogExtra("/b/", "Category b"), source) {}
        vm.onCreate(owner)
        advanceUntilIdle()
        vm.onLinkCardClick()
        advanceUntilIdle()
        assertTrue(vm.cardsData.value.filterIsInstance<LoadingCard>().single().isError)
        assertEquals(1, vm.cardsData.value.filterIsInstance<LibriaCard>().size)
        fail = false
        vm.onLoadingCardClick()
        advanceUntilIdle()
        assertEquals(listOf(1, 2, 2), source.requests.map { it.second })
        assertEquals(2, vm.cardsData.value.filterIsInstance<LibriaCard>().size)
        vm.viewModelScope.cancel()
    }

    private fun page(number: Int = 1) = AnimePage(listOf(testPreview(number)), number, 3)
    private val owner = object : LifecycleOwner {
        override val lifecycle: Lifecycle get() = error("Not used by the ViewModel callback")
    }
}

private class FakeCatalogSource : AnimeVostCatalogSource {
    val navigation = NavigationData(sections = listOf("a", "b", "c").map {
        CatalogLink("Category $it", "https://animevost.org/$it/", "/$it/")
    })
    var cachedNavigation: NavigationData? = null
    val cachedPages = mutableMapOf<String?, AnimePage>()
    val requests = mutableListOf<Pair<String?, Int>>()
    var fetchNavigation: suspend () -> NavigationData = { navigation }
    var fetch: suspend (String?, Int) -> AnimePage = { _, number -> AnimePage(listOf(testPreview(number)), number, 3) }
    override suspend fun getNavigation(forceRefresh: Boolean) = fetchNavigation()
    override suspend fun getCachedNavigation() = cachedNavigation
    override suspend fun getCachedCatalog(path: String?) = cachedPages[path]
    override suspend fun getCatalog(page: Int, sort: CatalogSort, path: String?): AnimePage {
        requests += path to page
        return fetch(path, page)
    }
}
