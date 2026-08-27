package ru.radiationx.anilibria.animevost

import com.animevost.sdk.model.AnimePage
import com.animevost.sdk.model.CatalogSort
import com.animevost.sdk.model.NavigationData
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.security.MessageDigest

interface AnimeVostCatalogSource {
    suspend fun getNavigation(forceRefresh: Boolean = false): NavigationData
    suspend fun getCachedNavigation(): NavigationData?
    suspend fun getCatalog(page: Int = 1, sort: CatalogSort = CatalogSort.DATE, path: String? = null): AnimePage
    suspend fun getCachedCatalog(path: String?): AnimePage?
}

/** Shared by the home previews and the full category grid. All disk work stays off Main. */
internal class AnimeVostCatalogStore(
    private val cache: AnimeVostCatalogCache,
    private val fetchCatalog: suspend (Int, CatalogSort, String?) -> AnimePage,
    private val fetchNavigation: suspend () -> NavigationData,
) : AnimeVostCatalogSource {
    private val locks = Array(32) { Mutex() }

    override suspend fun getNavigation(forceRefresh: Boolean): NavigationData =
        load("navigation", NavigationData::class.java, forceRefresh) {
            animeVostRequest { fetchNavigation() }
        }

    override suspend fun getCachedNavigation(): NavigationData? = withContext(Dispatchers.IO) {
        cache.read("navigation", NavigationData::class.java, allowStale = true)
    }

    override suspend fun getCatalog(page: Int, sort: CatalogSort, path: String?): AnimePage {
        val safePage = page.coerceAtLeast(1)
        return load(catalogKey(safePage, sort, path), AnimePage::class.java) {
            animeVostRequest { fetchCatalog(safePage, sort, path) }
        }
    }

    override suspend fun getCachedCatalog(path: String?): AnimePage? = withContext(Dispatchers.IO) {
        cache.read(catalogKey(1, CatalogSort.DATE, path), AnimePage::class.java, allowStale = true)
    }

    private suspend fun <T : Any> load(
        key: String,
        type: Class<T>,
        forceRefresh: Boolean = false,
        fetch: suspend () -> T,
    ): T = withContext(Dispatchers.IO) {
        if (!forceRefresh) cache.read(key, type)?.let { return@withContext it }
        // Bounded lock table: duplicate requests share their result instead of downloading twice.
        locks[(key.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            if (!forceRefresh) cache.read(key, type)?.let { return@withLock it }
            try {
                fetch().also { cache.write(key, it) }
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                cache.read(key, type, allowStale = true) ?: throw error
            }
        }
    }

    private fun catalogKey(page: Int, sort: CatalogSort, path: String?): String =
        "catalog:${path.orEmpty().trim().trim('/')}|${sort.name}|$page"
}

/** An individual request timeout is a recoverable error, not cancellation of the screen. */
internal suspend fun <T : Any> animeVostRequest(
    timeoutMs: Long = 20_000L,
    block: suspend () -> T,
): T = withTimeoutOrNull(timeoutMs) { block() }
    ?: throw IOException("AnimeVost не ответил вовремя. Повторите загрузку.")

/** Small, disposable, versioned cache. Only public catalog metadata is stored. */
internal class AnimeVostCatalogCache(
    private val directory: File,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val gson = Gson()
    private val memory = LinkedHashMap<String, JsonObject>(16, 0.75f, true)

    @Synchronized
    fun <T : Any> read(key: String, type: Class<T>, allowStale: Boolean = false): T? = try {
        val envelope = memory[key] ?: fileFor(key).takeIf { it.isFile && it.length() <= MAX_FILE_BYTES }
            ?.readText()?.let { gson.fromJson(it, JsonObject::class.java) }
            ?.also { remember(key, it) }
        val age = envelope?.get("savedAt")?.asLong?.let { now() - it }
        if (age == null || age < 0 || age > if (allowStale) MAX_AGE_MS else FRESH_MS) {
            null
        } else {
            gson.fromJson(envelope?.get("value"), type)?.takeIf { valid(it) }
        }
    } catch (_: Exception) {
        // Corrupt/evicted files must never prevent a normal network load.
        null
    }

    @Synchronized
    fun write(key: String, value: Any) {
        if (!valid(value)) return
        val envelope = JsonObject().apply {
            addProperty("savedAt", now())
            add("value", gson.toJsonTree(value))
        }
        remember(key, envelope)
        runCatching {
            if (!directory.isDirectory && !directory.mkdirs()) return@runCatching
            val file = fileFor(key)
            val temporary = File(directory, "${file.name}.tmp")
            temporary.writeText(gson.toJson(envelope))
            if (!temporary.renameTo(file)) temporary.delete()
            directory.listFiles { entry -> entry.extension == "json" }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_ENTRIES)
                ?.forEach { it.delete() }
        }
    }

    private fun remember(key: String, value: JsonObject) {
        memory[key] = value
        while (memory.size > MAX_ENTRIES) memory.remove(memory.keys.first())
    }

    private fun fileFor(key: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$hash.json")
    }

    private fun valid(value: Any): Boolean = try {
        when (value) {
            is AnimePage -> value.currentPage >= 1 && value.totalPages >= 1 &&
                value.items.all { it.title.isNotBlank() && it.url.isNotBlank() &&
                    it.categories.all { category -> category.title.isNotBlank() && category.url.isNotBlank() } }
            is NavigationData -> (value.sections + value.genres + value.types + value.years)
                .let { links -> links.isNotEmpty() && links.all { it.title.isNotBlank() && it.path.isNotBlank() } }
            else -> false
        }
    } catch (_: Exception) {
        false
    }

    companion object {
        const val FRESH_MS = 10 * 60 * 1000L
        const val MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000L
        private const val MAX_ENTRIES = 256
        private const val MAX_FILE_BYTES = 512 * 1024L
    }
}
