package ru.radiationx.anilibria.animevost

import com.animevost.sdk.model.AnimePage
import com.animevost.sdk.model.AnimePreview
import com.animevost.sdk.model.CatalogLink
import com.animevost.sdk.model.CatalogSort
import com.animevost.sdk.model.NavigationData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnimeVostCatalogStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun cacheSurvivesProcessRestartAndSeparatesFreshFromStale() {
        val directory = temporary.newFolder()
        var now = 1_000L
        val cache = AnimeVostCatalogCache(directory) { now }
        cache.write("page", page())
        val restarted = AnimeVostCatalogCache(directory) { now }
        assertEquals(page(), restarted.read("page", AnimePage::class.java))
        now += AnimeVostCatalogCache.FRESH_MS + 1
        assertNull(restarted.read("page", AnimePage::class.java))
        assertEquals(page(), restarted.read("page", AnimePage::class.java, allowStale = true))
        now += AnimeVostCatalogCache.MAX_AGE_MS
        assertNull(restarted.read("page", AnimePage::class.java, allowStale = true))
    }

    @Test fun invalidAndCorruptCacheAreIgnored() {
        val directory = temporary.newFolder()
        val cache = AnimeVostCatalogCache(directory)
        cache.write("page", page())
        directory.listFiles()!!.single().writeText("{broken")
        assertNull(AnimeVostCatalogCache(directory).read("page", AnimePage::class.java))
        directory.listFiles()!!.single().writeText("{\"savedAt\":${System.currentTimeMillis()},\"value\":{}}")
        assertNull(AnimeVostCatalogCache(directory).read("page", AnimePage::class.java))
    }

    @Test fun simultaneousHomeAndGridRequestsUseOneDownload() = runBlocking {
        val calls = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val store = store { _, _, _ ->
            calls.incrementAndGet()
            started.complete(Unit)
            release.await()
            page()
        }
        val requests = List(8) { async { store.getCatalog(path = "/ongoing/") } }
        started.await()
        release.complete(Unit)
        assertTrue(requests.awaitAll().all { it == page() })
        assertEquals(1, calls.get())
        assertEquals(page(), store.getCatalog(path = "ongoing"))
        assertEquals(1, calls.get())
    }

    @Test fun cacheKeysKeepCategorySortAndPageIndependent() = runBlocking {
        val calls = AtomicInteger()
        val store = store { number, _, _ -> calls.incrementAndGet(); page(number) }
        store.getCatalog(path = "ongoing")
        store.getCatalog(path = "preview")
        store.getCatalog(path = "ongoing", page = 2)
        store.getCatalog(path = "ongoing", sort = CatalogSort.RATING)
        store.getCatalog(path = "ongoing")
        assertEquals(4, calls.get())
    }

    @Test fun networkFailureUsesSavedPageWithoutExtendingItsAge() = runBlocking {
        var now = 1_000L
        var fail = false
        val cache = AnimeVostCatalogCache(temporary.newFolder()) { now }
        val store = AnimeVostCatalogStore(cache, { _, _, _ ->
            if (fail) throw IOException("offline")
            page()
        }, { navigation() })
        store.getCatalog(path = "ongoing")
        now += AnimeVostCatalogCache.FRESH_MS + 1
        fail = true
        assertEquals(page(), store.getCatalog(path = "ongoing"))
        now += AnimeVostCatalogCache.MAX_AGE_MS
        assertFailsWith<IOException> { store.getCatalog(path = "ongoing") }
    }

    @Test fun navigationIsPersistedAndReused() = runBlocking {
        val directory = temporary.newFolder()
        val first = AnimeVostCatalogStore(AnimeVostCatalogCache(directory), { _, _, _ -> page() }, { navigation() })
        first.getNavigation()
        val restarted = AnimeVostCatalogStore(AnimeVostCatalogCache(directory), { _, _, _ -> error("network") }, { error("network") })
        assertEquals(navigation(), restarted.getCachedNavigation())
        assertEquals(navigation(), restarted.getNavigation())
    }

    @Test fun requestTimeoutBecomesRecoverableIOException() = runTest {
        assertFailsWith<IOException> { animeVostRequest<Unit>(10) { awaitCancellation() } }
    }

    @Test fun screenCancellationIsNotConvertedIntoNetworkFailure() = runTest {
        var handledAsError = false
        val job = launch {
            try { animeVostRequest<Unit> { awaitCancellation() } }
            catch (_: IOException) { handledAsError = true }
        }
        testScheduler.runCurrent()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertFalse(handledAsError)
    }

    private fun store(fetch: suspend (Int, CatalogSort, String?) -> AnimePage) = AnimeVostCatalogStore(
        AnimeVostCatalogCache(temporary.newFolder()), fetch, { navigation() },
    )

    private fun navigation() = NavigationData(sections = listOf(CatalogLink("Онгоинги", "https://animevost.org/ongoing/", "/ongoing/")))
    private fun page(number: Int = 1) = AnimePage(listOf(testPreview(number)), number, 3)
}

internal fun testPreview(id: Int = 42) = AnimePreview(
    id = id, title = "Anime $id", url = "https://animevost.org/$id.html",
    originalTitle = null, episodeInfo = null, posterUrl = "https://animevost.org/$id.jpg",
    publishedDate = null, viewCount = null, commentCount = null, rating = null, voteCount = null,
    categories = emptyList(),
)
