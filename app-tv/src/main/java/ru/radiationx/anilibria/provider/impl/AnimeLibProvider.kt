package ru.radiationx.anilibria.provider.impl

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import org.jsoup.Jsoup
import ru.radiationx.anilibria.provider.*
import javax.inject.Inject

class AnimeLibProvider @Inject constructor(private val http: ProviderHttpClient, private val players: EmbeddedPlayerResolver) : AnimeProvider {
    override val id = ProviderId.ANILIB
    override val displayName = id.uiName
    override val capabilities = ProviderCapabilities(browse = true, multipleVoices = true)
    private val headers = mapOf("Referer" to "https://animelib.org/", "Accept" to "application/json")
    override suspend fun browse(page: Int) = list(page, "")
    override suspend fun search(query: String) = list(1, query)
    private suspend fun list(page: Int, query: String): List<ProviderAnime> = JSONObject(http.get(
        "$API/anime?site_id[]=1&site_id[]=3&fields[]=releaseDate&fields[]=rate_avg&page=$page&q=${encodeValue(query)}", headers
    )).optJSONArray("data").objects().map(::anime)
    private fun anime(data: JSONObject): ProviderAnime {
        val shiki = Regex("/(?:animes/)?[a-z]?(\\d+)").find(data.optString("shikimori_href"))?.groupValues?.get(1)
        return ProviderAnime(id, data.optString("slug_url"), data.optString("rus_name").ifBlank { data.optString("name") },
            originalTitle = data.optString("name"), posterUrl = data.optJSONObject("cover")?.optString("default").orEmpty(),
            description = Jsoup.parse(data.optString("summary")).text(),
            year = Regex("(?:19|20)\\d{2}").find(data.optString("releaseDate"))?.value.orEmpty(),
            kind = AnimeKind.parse(data.optJSONObject("type")?.optString("label").orEmpty()),
            genres = data.optJSONArray("genres").objects().map { it.optString("name") },
            rating = data.optJSONObject("rating")?.optString("average")?.toDoubleOrNull(),
            externalIds = shiki?.let { mapOf("shikimori" to it) }.orEmpty(),
            addedAt = parseProviderDate(data.optString("created_at")))
    }
    override suspend fun getDetails(animeId: String): ProviderAnimeDetails {
        val data = JSONObject(http.get("$API/anime/${encodeValue(animeId)}?fields[]=summary&fields[]=genres&fields[]=shikimori_href", headers)).getJSONObject("data")
        val item = anime(data)
        val episodes = JSONObject(http.get("$API/episodes?anime_id=${encodeValue(animeId)}", headers))
            .optJSONArray("data").objects().map { ep ->
                val number = ep.optString("number")
                ProviderEpisode(ep.optString("id"), number.toIntOrNull(),
                    ep.optString("name").ifBlank { "Серия $number" }, season = ep.optString("season").toIntOrNull() ?: 1,
                    special = number.toIntOrNull() == null, numberLabel = number)
            }.sortedWith(compareBy<ProviderEpisode> { it.season }.thenBy { it.number ?: Int.MAX_VALUE })
        return ProviderAnimeDetails(id, animeId, item.title, item.originalTitle, item.description, item.posterUrl,
            item.year, "$displayName • ${item.year}", item.genres, episodes, item.kind, item.rating, item.addedAt, item.externalIds)
    }
    override suspend fun getSources(animeId: String, episodeId: String): List<ProviderSource> = supervisorScope {
        val data = JSONObject(http.get("$API/episodes/${encodeValue(episodeId)}", headers)).getJSONObject("data")
        val limit = Semaphore(3)
        data.optJSONArray("players").objects().map { player -> async {
            limit.withPermit {
                try {
                    val voice = player.optJSONObject("team")?.optString("name").orEmpty().ifBlank { "AnimeLib" }
                    val streams = if (player.optString("player") == "animelib") {
                        // Video qualities are returned only when the service authorizes access.
                        player.optJSONObject("video")?.optJSONArray("quality").objects().mapNotNull { quality ->
                            mediaStream(absolute("https://video1.cdnlibs.org/.%D0%B0s/", quality.optString("href")),
                                "https://v3.animelib.org/", voice, quality.optInt("quality"))
                        }
                    } else withTimeoutOrNull(5_000) { players.resolve(player.optString("src"), "https://animelib.org/", voice) }.orEmpty()
                    if (streams.isEmpty()) null else ProviderSource(player.optString("id"), voice, player.optString("player"), streams)
                } catch (error: Exception) { currentCoroutineContext().ensureActive(); null }
            }
        } }.awaitAll().filterNotNull()
    }
    override suspend fun isAvailable() = runCatching { browse(1).isNotEmpty() }.getOrDefault(false)
    private companion object { const val API = "https://api.cdnlibs.org/api" }
}
