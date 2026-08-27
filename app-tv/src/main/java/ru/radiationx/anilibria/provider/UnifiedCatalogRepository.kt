package ru.radiationx.anilibria.provider

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject

/** Disk-backed metadata only. Video URLs are resolved only after Play is pressed. */
class UnifiedCatalogRepository @Inject constructor(context: Context, private val registry: ProviderRegistry) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val file = AtomicFile(File(context.filesDir, "unified-catalog-v2.json"))
    private val gson = Gson()
    private val mutex = Mutex()
    private val settings = context.getSharedPreferences("aniru_catalog_v2", Context.MODE_PRIVATE)
    private val entries = linkedMapOf<String, Entry>()
    private val pages = mutableMapOf<ProviderId, Int>()
    private val exhausted = mutableSetOf<ProviderId>()
    private val seenPages = mutableMapOf<ProviderId, MutableSet<List<String>>>()
    private val enriched = mutableSetOf<String>()
    private val details = mutableMapOf<String, Pair<Long, ProviderAnimeDetails>>()
    private var sync: Job? = null
    private var initialized = false
    private var batchTime = System.currentTimeMillis()
    val items = MutableStateFlow<List<UnifiedAnime>>(emptyList())
    val status = MutableStateFlow("Загрузка сохранённого каталога…")
    val providerStatus = MutableStateFlow<Map<ProviderId, String>>(emptyMap())
    val loading = MutableStateFlow(false)

    private data class Entry(val anime: ProviderAnime, val firstSeen: Long)
    private data class Snapshot(val entries: List<Entry>, val batchTime: Long)

    fun enabled(id: ProviderId): Boolean = settings.getBoolean("enabled_${id.wireId}", true)
    fun setEnabled(id: ProviderId, enabled: Boolean) {
        settings.edit().putBoolean("enabled_${id.wireId}", enabled).apply()
        scope.launch { mutex.withLock { publish() } }
    }

    @Synchronized fun start(refresh: Boolean = false) {
        if (sync?.isActive == true) return
        sync = scope.launch {
            loading.value = true
            try {
                mutex.withLock {
                    if (!initialized) {
                        runCatching { gson.fromJson(file.openRead().bufferedReader().use { it.readText() }, Snapshot::class.java) }
                            .getOrNull()?.let { saved ->
                                saved.entries.forEach { entries[it.anime.reference] = it }
                                batchTime = saved.batchTime
                            }
                        initialized = true
                        publish()
                    }
                    if (refresh) { pages.clear(); exhausted.clear(); seenPages.clear(); enriched.clear(); batchTime = System.currentTimeMillis() }
                }
                // Round-robin by page, but never more than ONE catalog request in flight.
                while (isActive) {
                    val providers = registry.searchableProviders().filter { enabled(it.id) && it.capabilities.browse && it.id !in exhausted }
                    if (providers.isEmpty() && mutex.withLock { entries.values.none { it.anime.kind == AnimeKind.UNKNOWN && it.anime.reference !in enriched && enabled(it.anime.provider) } }) break
                    for (provider in providers) {
                        ensureActive()
                        val page = pages[provider.id] ?: 1
                        status.value = "${provider.displayName} · страница $page · ${items.value.size} аниме"
                        try {
                            val result = withTimeoutOrNull(7_000L) { provider.browse(page) }
                                ?: throw java.io.IOException("Время ожидания истекло")
                            val added = mutex.withLock {
                                val count = result.count { it.reference !in entries }
                                result.forEach { put(it) }
                                publish(); persist(); count
                            }
                            pages[provider.id] = page + 1
                            val repeated = !seenPages.getOrPut(provider.id) { mutableSetOf() }.add(result.map { it.id })
                            if (result.isEmpty() || repeated) exhausted += provider.id
                            health(provider.id, if (result.isEmpty()) "Каталог загружен" else "Доступен · страница $page")
                        } catch (error: Exception) {
                            currentCoroutineContext().ensureActive()
                            exhausted += provider.id
                            health(provider.id, "Недоступен — повторить через «Обновить»")
                        }
                        delay(650)
                    }
                    // Detail-page metadata is fetched one at a time, never its playlist/streams.
                    val pending = mutex.withLock { entries.values.map { it.anime }.filter {
                        it.kind == AnimeKind.UNKNOWN && it.reference !in enriched && enabled(it.provider)
                    }.take(4) }
                    for (item in pending) {
                        enriched += item.reference
                        try {
                            val metadata = withTimeoutOrNull(5_000L) { registry.get(item.provider).catalogMetadata(item) }
                            if (metadata != null) mutex.withLock { put(metadata); publish(); persist() }
                        } catch (error: Exception) { currentCoroutineContext().ensureActive() }
                        delay(250)
                    }
                }
                status.value = "${items.value.size} аниме · обновить можно вручную"
            } finally { loading.value = false }
        }
    }

    private fun health(id: ProviderId, text: String) { providerStatus.value = providerStatus.value + (id to text) }
    private fun put(item: ProviderAnime) {
        if (item.id.isBlank() || item.title.isBlank()) return
        val previous = entries[item.reference]
        val enriched = if (previous != null && item.kind == AnimeKind.UNKNOWN) item.copy(
            kind = previous.anime.kind,
            year = item.year.ifBlank { previous.anime.year },
            genres = item.genres.ifEmpty { previous.anime.genres },
            externalIds = item.externalIds.ifEmpty { previous.anime.externalIds },
        ) else item
        entries[item.reference] = Entry(enriched, previous?.firstSeen ?: batchTime)
    }

    private fun publish() {
        val groups = mutableListOf<MutableList<Entry>>()
        val names = mutableMapOf<String, MutableSet<Int>>()
        val ids = mutableMapOf<String, MutableSet<Int>>()
        entries.values.filter { enabled(it.anime.provider) }.forEach { entry ->
            val item = entry.anime
            val candidates = AnimeIdentity.names(item).flatMap { names[it].orEmpty() }.toSet() +
                item.externalIds.flatMap { ids["${it.key}:${it.value}"].orEmpty() }
            val index = candidates.firstOrNull { candidate -> groups[candidate].all { AnimeIdentity.same(it.anime, item) } }
                ?: groups.size.also { groups.add(mutableListOf()) }
            groups[index].add(entry)
            AnimeIdentity.names(item).forEach { names.getOrPut(it) { mutableSetOf() }.add(index) }
            item.externalIds.forEach { (key, value) -> ids.getOrPut("$key:$value") { mutableSetOf() }.add(index) }
        }
        items.value = groups.map { group -> UnifiedAnime(group.map { it.anime }, group.minOf { it.firstSeen }) }
            .ordered(CatalogOrder.ADDED)
    }

    private fun persist() {
        val stream = file.startWrite()
        try {
            stream.write(gson.toJson(Snapshot(entries.values.toList(), batchTime)).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) { file.failWrite(stream); throw error }
    }

    fun group(provider: ProviderId, id: String): UnifiedAnime? = items.value.firstOrNull { group ->
        group.versions.any { it.provider == provider && it.id == id }
    }

    suspend fun getDetails(provider: ProviderId, id: String): ProviderAnimeDetails = withContext(Dispatchers.IO) {
        val key = "${provider.wireId}|$id"
        mutex.withLock { details[key]?.takeIf { System.currentTimeMillis() - it.first < 300_000 }?.let { return@withContext it.second } }
        val result = withTimeoutOrNull(15_000L) { registry.get(provider).getDetails(id) }
            ?: throw java.io.IOException("Источник не ответил. Повторите или выберите другой источник.")
        mutex.withLock {
            details[key] = System.currentTimeMillis() to result
            put(result.asAnime()); publish(); persist()
        }
        result
    }

    suspend fun search(query: String, onResult: (List<UnifiedAnime>) -> Unit) = supervisorScope {
        val matching = linkedMapOf<String, ProviderAnime>()
        items.value.filter { group -> group.versions.any { AnimeIdentity.names(it).any { name -> name.contains(AnimeIdentity.normalize(query)) } } }
            .flatMap { it.versions }.forEach { matching[it.reference] = it }
        fun results(): List<UnifiedAnime> {
            val groups = mutableListOf<MutableList<ProviderAnime>>()
            matching.values.forEach { item ->
                val group = groups.firstOrNull { versions -> versions.all { AnimeIdentity.same(it, item) } }
                if (group == null) groups.add(mutableListOf(item)) else group.add(item)
            }
            return groups.map { UnifiedAnime(it, batchTime) }
        }
        val resultLock = Mutex()
        onResult(results())
        registry.searchableProviders().filter { enabled(it.id) }.map { provider -> launch {
            try {
                val found = withTimeoutOrNull(8_000) { provider.search(query) }.orEmpty()
                resultLock.withLock { found.forEach { matching[it.reference] = it }; onResult(results()) }
                withContext(Dispatchers.IO) { mutex.withLock { found.forEach(::put); publish(); persist() } }
            } catch (error: Exception) { currentCoroutineContext().ensureActive() }
        } }.joinAll()
    }
}
