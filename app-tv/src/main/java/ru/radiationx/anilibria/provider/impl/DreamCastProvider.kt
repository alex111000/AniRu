package ru.radiationx.anilibria.provider.impl

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import ru.radiationx.anilibria.provider.*
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class DreamCastProvider @Inject constructor(private val http: ProviderHttpClient) : AnimeProvider {
    override val id = ProviderId.DREAMCAST
    override val displayName = id.uiName
    override val capabilities = ProviderCapabilities(browse = true)
    private val catalog = ConcurrentHashMap<String, ProviderAnime>()
    private val playlists = ConcurrentHashMap<String, Pair<Long, List<JSONObject>>>()
    override suspend fun browse(page: Int) = list(page, "")
    override suspend fun search(query: String) = list(1, query)
    private suspend fun list(page: Int, query: String): List<ProviderAnime> {
        val root = JSONObject(http.postForm("$SITE/", mapOf("search" to query, "status" to "", "pageSize" to "32", "pageNumber" to page.toString()),
            mapOf("Referer" to SITE, "Origin" to SITE, "X-Requested-With" to "XMLHttpRequest")))
        val raw = root.optJSONArray("releases")?.objects() ?: root.optJSONObject("releases")?.let { data ->
            data.keys().asSequence().mapNotNull { data.optJSONObject(it) }.toList()
        }.orEmpty()
        return raw.map { data -> ProviderAnime(id, absolute(SITE, data.optString("url")),
            data.optString("russian").ifBlank { data.optString("original") }, data.optString("original"),
            data.optString("description").replace("null", ""), absolute(SITE, data.optString("image")),
            data.optString("dateissue"), displayName, AnimeKind.parse(data.optString("type")),
            data.optString("genres").split(',').map { it.trim() }.filter { it.isNotBlank() },
            data.optString("rating").replace(',', '.').toDoubleOrNull(), parseProviderDate(data.optString("date")))
        }.onEach { catalog[it.id] = it }
    }
    override suspend fun getDetails(animeId: String): ProviderAnimeDetails {
        require(animeId.startsWith("$SITE/"))
        val html = http.get(animeId)
        val doc = Jsoup.parse(html, SITE)
        val encoded = Regex("new Playerjs\\(\"(.*?)\"\\)").find(html)?.groupValues?.get(1)
        val jsUrl = doc.selectFirst("script[src*=/js/playerjs]")?.absUrl("src").orEmpty()
        val playlist = if (encoded != null && jsUrl.isNotBlank()) {
            val decoded = DreamPlaylistDecoder.decode(http.get(jsUrl), encoded)
            decoded.optJSONArray("file")?.objects() ?: listOf(JSONObject().put("file", decoded.optString("file")))
        } else emptyList()
        playlists[animeId] = System.currentTimeMillis() to playlist
        val metadata = catalog[animeId]
        return ProviderAnimeDetails(id, animeId, metadata?.title ?: doc.select("h3").first()?.text().orEmpty(),
            metadata?.originalTitle.orEmpty(), doc.select(".postDesc").text(),
            metadata?.posterUrl ?: doc.selectFirst(".details_poster img")?.absUrl("src").orEmpty(),
            metadata?.year.orEmpty(), displayName, metadata?.genres.orEmpty(),
            playlist.mapIndexed { index, item ->
                val title = Jsoup.parse(item.optString("title")).text()
                val number = Regex("(?:^|\\s)(\\d+)(?:\\s|$)").find(title)?.groupValues?.get(1)?.toIntOrNull() ?: index + 1
                ProviderEpisode(index.toString(), number, title.ifBlank { "Серия $number" })
            }, metadata?.kind ?: AnimeKind.parse(doc.select(".details_info").text()), metadata?.rating)
    }
    override suspend fun getSources(animeId: String, episodeId: String): List<ProviderSource> {
        if (playlists[animeId]?.let { System.currentTimeMillis() - it.first < 300_000 } != true) getDetails(animeId)
        val file = playlists[animeId]?.second?.getOrNull(episodeId.toIntOrNull() ?: return emptyList())?.optString("file").orEmpty()
        val streams = parseMediaList(file, SITE, displayName)
        return if (streams.isEmpty()) emptyList() else listOf(ProviderSource("dreamcast", displayName, "PlayerJS", streams))
    }
    override suspend fun isAvailable() = runCatching { browse(1).isNotEmpty() }.getOrDefault(false)
    private companion object { const val SITE = "https://dreamerscast.com" }
}

/** Data decoding only; downloaded JavaScript is never executed. Port of dreamcast_chipers.py. */
internal object DreamPlaylistDecoder {
    private const val ALPHABET = "ABCDEFGHIJKLMabcdefghijklmNOPQRSTUVWXYZnopqrstuvwxyz"
    fun decode(script: String, encoded: String): JSONObject {
        require(script.length < 4_000_000 && encoded.length < 4_000_000)
        val params = Regex("return p}\\('((?:\\\\.|[^'])*)',\\s*(\\d+),\\s*(\\d+),\\s*'([^']*)'\\.split", RegexOption.DOT_MATCHES_ALL)
            .find(script) ?: throw java.io.IOException("Формат плеера DreamersCast изменился")
        val radix = params.groupValues[2].toInt().also { require(it in 2..62) }
        val count = params.groupValues[3].toInt().also { require(it in 1..100_000) }
        val words = params.groupValues[4].split('|')
        fun key(index: Int): String {
            val digit = index % radix
            val ch = if (digit > 35) (digit + 29).toChar() else "0123456789abcdefghijklmnopqrstuvwxyz"[digit]
            return (if (index >= radix) key(index / radix) else "") + ch
        }
        val dictionary = (0 until count).associate { key(it) to words.getOrNull(it).orEmpty().ifBlank { key(it) } }
        val unpacked = Regex("\\b\\w+\\b").replace(params.groupValues[1]) { dictionary[it.value] ?: it.value }
        val secret = Regex("u:\\s*\\\\\\s*['\"]([^=]+=[\\\\]+)\\s*['\"]").find(unpacked)?.groupValues?.get(1)
            ?: throw java.io.IOException("Не найден ключ плейлиста DreamersCast")
        var payload = secret.drop(2)
        if (secret.startsWith("#1")) {
            val rotated = ALPHABET.drop(24) + ALPHABET.take(24)
            payload = payload.map { ch -> ALPHABET.indexOf(ch).let { if (it >= 0) rotated[it] else ch } }.joinToString("")
        }
        val custom = ALPHABET + "0123456789+/="
        val standard = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/="
        val normal = payload.filter { it in custom }.map { standard[custom.indexOf(it)] }.joinToString("")
        val secrets = JSONObject(String(Base64.decode(normal, Base64.DEFAULT), Charsets.UTF_8))
        var data = encoded.drop(2)
        for (index in 4 downTo 0) {
            val value = secrets.optString("bk$index")
            if (value.isNotBlank() && value != "undefined" && value != "null") {
                val quoted = encodeValue(value).replace("+", "%20").replace("%2F", "/")
                val token = Base64.encodeToString(quoted.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                data = data.replace("//$token", "")
            }
        }
        return JSONObject(URLDecoder.decode(String(Base64.decode(data, Base64.DEFAULT), Charsets.UTF_8).replace("+", "%2B"), "UTF-8"))
    }
}
