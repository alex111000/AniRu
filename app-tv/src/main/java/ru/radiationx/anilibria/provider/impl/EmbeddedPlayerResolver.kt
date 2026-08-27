package ru.radiationx.anilibria.provider.impl

import android.util.Base64
import org.json.JSONObject
import org.jsoup.Jsoup
import ru.radiationx.anilibria.provider.*
import javax.inject.Inject

/** Native, non-evaluating parsers for public player responses (anicli-api, MIT). */
class EmbeddedPlayerResolver @Inject constructor(private val http: ProviderHttpClient) {
    suspend fun resolve(value: String, referer: String, voice: String): List<ProviderStream> {
        val url = absolute(referer, value)
        mediaStream(url, referer, voice)?.let { return listOf(it) }
        if (url.isBlank()) return emptyList()
        if (url.contains("aksor") && url.contains("/video/")) {
            val key = url.substringBefore('?').substringAfterLast('/')
            val root = JSONObject(http.get("https://player.aksor.tv/api/video/$key", mapOf("Referer" to referer)))
            val qualities = root.optJSONObject("qualities") ?: return emptyList()
            return qualities.keys().asSequence().mapNotNull { quality ->
                mediaStream(qualities.optString(quality), url, voice, when (quality) {
                    "q4k" -> 2160; "q2k" -> 1440; else -> quality.removePrefix("q").toIntOrNull() ?: 0
                })
            }.toList()
        }
        val html = http.get(url, mapOf("Referer" to referer))
        val doc = Jsoup.parse(html, url)
        if (url.contains("/cdn-iframe/")) {
            val node = doc.selectFirst("video-player[data-title-id]") ?: return emptyList()
            val parts = java.net.URI(url).path.substringAfter("/cdn-iframe/").split('/')
            if (parts.size != 4) return emptyList()
            val api = "https://plapi.cdnvideohub.com/api/v1/player/sv"
            val playlist = JSONObject(http.get("$api/playlist?pub=${encodeValue(node.attr("data-publisher-id"))}&aggr=${encodeValue(node.attr("data-aggregator"))}&id=${encodeValue(node.attr("data-title-id"))}"))
            val item = playlist.optJSONArray("items").objects().firstOrNull {
                it.optInt("season") == parts[2].toIntOrNull() && it.optInt("episode") == parts[3].toIntOrNull() && it.optString("voiceStudio") == parts[1]
            } ?: return emptyList()
            val sources = JSONObject(http.get("$api/video/${encodeValue(item.optString("vkId"))}")).optJSONObject("sources") ?: return emptyList()
            val qualities = mapOf("mpegTinyUrl" to 144, "mpegLowestUrl" to 240, "mpegLowUrl" to 360, "mpegMediumUrl" to 480,
                "mpegHighUrl" to 720, "mpegFullHdUrl" to 1080, "mpegQhdUrl" to 1440, "mpeg2kUrl" to 1440, "mpeg4kUrl" to 2160)
            val direct = qualities.mapNotNull { (key, quality) -> mediaStream(sources.optString(key), url, voice, quality) }
            val best = direct.maxOfOrNull { it.quality } ?: 0
            return direct + listOf("hlsUrl", "dashUrl").mapNotNull { mediaStream(sources.optString(it), url, voice, best) }
        }
        doc.selectFirst("#video[data-parameters]")?.let { node ->
            val data = JSONObject(node.attr("data-parameters"))
            if (data.optString("error").isNotBlank()) return emptyList()
            return listOf("hls", "dash").mapNotNull { key ->
                val obj = data.optJSONObject(key) ?: runCatching { JSONObject(data.optString(key)) }.getOrNull()
                mediaStream(obj?.optString("src").orEmpty(), "https://aniboom.one/", voice,
                    data.optInt("qualityVideo", 0))?.let {
                    it.copy(headers = it.headers + mapOf("Origin" to "https://aniboom.one", "Accept-Language" to "ru-RU"))
                }
            }.distinctBy { it.stableKey }
        }
        if (html.contains("vInfo.hash") && html.contains("vInfo.id")) return kodik(url, html, voice)
        val clean = html.replace("\\/", "/").replace("\\u002F", "/")
        val result = mutableListOf<ProviderStream>()
        Regex("(?:file|src)\\s*[:=]\\s*[\"']([^\"']+)[\"']").findAll(clean).forEach {
            result += parseMediaList(it.groupValues[1], url, voice)
        }
        Regex("[\"'](/v/[^\"']+\\.mp4)[\"']").find(clean)?.let {
            mediaStream(it.groupValues[1], url, voice)?.let(result::add)
        }
        Regex("https?://[^\\s\"'<>]+\\.(?:m3u8|mpd|mp4)(?:\\?[^\\s\"'<>]*)?").findAll(clean).forEach {
            mediaStream(it.value, url, voice)?.let(result::add)
        }
        return result.distinctBy { it.stableKey }
    }

    private suspend fun kodik(url: String, html: String, voice: String): List<ProviderStream> {
        fun variable(name: String) = Regex("(?:var\\s+)?${Regex.escape(name)}\\s*=\\s*[\"'](.*?)[\"']")
            .find(html)?.groupValues?.get(1).orEmpty()
        val payload = mutableMapOf("d" to variable("domain"))
        listOf("d_sign", "pd", "pd_sign", "ref", "ref_sign").forEach { payload[it] = variable(it) }
        listOf("type", "hash", "id").forEach { payload[it] = variable("vInfo.$it") }
        if (payload["id"].isNullOrBlank() || payload["hash"].isNullOrBlank()) return emptyList()
        payload.putAll(mapOf("bad_user" to "false", "info" to "{}", "cdn_is_working" to "true"))
        val scriptUrl = Jsoup.parse(html, url).selectFirst("script[src*=assets/js]")?.absUrl("src").orEmpty()
        if (scriptUrl.isBlank()) return emptyList()
        val script = http.get(scriptUrl, mapOf("Referer" to url))
        val encoded = Regex("ajax[^)]+atob\\([\"']([A-Za-z0-9+/=]+)[\"']\\)")
            .find(script)?.groupValues?.get(1) ?: return emptyList()
        val path = String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
        if (!path.startsWith("/") || path.startsWith("//")) return emptyList()
        val origin = java.net.URI(url).let { "${it.scheme}://${it.host}" }
        val response = JSONObject(http.postForm(origin + path, payload, mapOf("Origin" to origin, "Referer" to url)))
        val links = response.optJSONObject("links") ?: return emptyList()
        return links.keys().asSequence().flatMap { quality ->
            links.optJSONArray(quality).objects().asSequence().mapNotNull { entry ->
                val src = entry.optString("src")
                val decoded = if (src.startsWith("http") || src.startsWith("//")) src else runCatching {
                    val rotated = src.map { ch -> when (ch) {
                        in 'a'..'z' -> ('a'.code + (ch.code - 'a'.code + 18) % 26).toChar()
                        in 'A'..'Z' -> ('A'.code + (ch.code - 'A'.code + 18) % 26).toChar()
                        else -> ch
                    } }.joinToString("")
                    String(Base64.decode(rotated, Base64.DEFAULT), Charsets.UTF_8)
                }.getOrDefault("")
                mediaStream(decoded, url, voice, quality.toIntOrNull() ?: 0)
            }
        }.toList()
    }
}
