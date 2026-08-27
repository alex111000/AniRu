package ru.radiationx.anilibria.provider.impl

import org.json.JSONObject
import ru.radiationx.anilibria.provider.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/** AniLiberty is the current API of the AniLibria source in anicli-api. */
class AniLibriaProvider @Inject constructor(
    private val http: ProviderHttpClient,
) : AnimeProvider {

    override val id = ProviderId.ANILIBRIA
    override val displayName = id.uiName
    override val capabilities = ProviderCapabilities(
        search = true,
        details = true,
        playback = true,
        multipleVoices = false,
        browse = true,
    )

    private val cache = ConcurrentHashMap<String, Pair<Long, JSONObject>>()
    override suspend fun search(query: String) = catalog(1, query)
    override suspend fun browse(page: Int) = catalog(page, "")
    private suspend fun catalog(page: Int, query: String): List<ProviderAnime> {
        val root = JSONObject(http.get("$API/anime/catalog/releases?page=$page&limit=30" +
            if (query.isBlank()) "" else "&f[search]=${encodeValue(query)}"))
        return root.optJSONArray("data").objects().map(::anime)
    }
    private fun anime(obj: JSONObject): ProviderAnime = ProviderAnime(
        provider = id, id = obj.optString("id"), title = obj.optJSONObject("name")?.optString("main").orEmpty(),
        originalTitle = obj.optJSONObject("name")?.optString("english").orEmpty(),
        description = obj.optString("description"), posterUrl = absolute(SITE, obj.optJSONObject("poster")?.optString("src").orEmpty()),
        year = obj.optString("year").replace("null", ""),
        kind = AnimeKind.parse(obj.optJSONObject("type")?.let { it.optString("description") + " " + it.optString("value") }.orEmpty()),
        genres = obj.optJSONArray("genres").objects().map { it.optString("name") },
        addedAt = parseProviderDate(obj.optString("created_at")),
    )
    private suspend fun raw(id: String): JSONObject {
        cache[id]?.takeIf { System.currentTimeMillis() - it.first < 300_000 }?.let { return it.second }
        return JSONObject(http.get("$API/anime/releases/${encodeValue(id)}")).also { cache[id] = System.currentTimeMillis() to it }
    }
    override suspend fun getDetails(animeId: String): ProviderAnimeDetails {
        val raw = raw(animeId)
        val item = anime(raw)
        val episodes = raw.optJSONArray("episodes").objects().map { episode ->
            val number = episode.optString("ordinal")
            ProviderEpisode(episode.optString("id"), number.toDoubleOrNull()?.toInt(),
                episode.optString("name").ifBlank { "Серия $number" },
                numberLabel = number.toDoubleOrNull()?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }.orEmpty(),
                special = number.toDoubleOrNull()?.rem(1.0) != 0.0)
        }
        return ProviderAnimeDetails(id, animeId, item.title, item.originalTitle, item.description, item.posterUrl,
            item.year, "$displayName • ${item.year}", item.genres, episodes,
            item.kind, item.rating, item.addedAt, item.externalIds)
    }
    override suspend fun getSources(animeId: String, episodeId: String): List<ProviderSource> {
        val data = raw(animeId)
        if (data.optBoolean("is_blocked_by_geo") || data.optBoolean("is_blocked_by_copyrights")) return emptyList()
        val ep = data.optJSONArray("episodes").objects().firstOrNull { it.optString("id") == episodeId } ?: return emptyList()
        val streams = listOf(1080, 720, 480).mapNotNull { quality -> mediaStream(ep.optString("hls_$quality"), SITE, displayName, quality) }
        return if (streams.isEmpty()) emptyList() else listOf(ProviderSource("anilibria", "AniLibria", "AniLiberty", streams))
    }
    override suspend fun isAvailable() = runCatching { browse(1).isNotEmpty() }.getOrDefault(false)
    private companion object { const val SITE = "https://aniliberty.top"; const val API = "$SITE/api/v1" }
}
