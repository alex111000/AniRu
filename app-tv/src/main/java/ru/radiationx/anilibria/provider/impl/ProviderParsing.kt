package ru.radiationx.anilibria.provider.impl

import org.json.JSONArray
import org.json.JSONObject
import ru.radiationx.anilibria.provider.*
import java.net.URI
import java.net.URLEncoder

internal fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else
    (0 until length()).mapNotNull { optJSONObject(it) }
internal fun encodeValue(value: String): String = URLEncoder.encode(value, "UTF-8")
internal fun absolute(base: String, path: String): String = when {
    path.isBlank() || path == "null" -> ""
    path.startsWith("//") -> "https:$path"
    else -> runCatching { URI(base).resolve(path).toString() }.getOrDefault("")
}
internal fun mediaStream(value: String, referer: String, voice: String, quality: Int = 0): ProviderStream? {
    val url = absolute(referer, value.replace("\\/", "/").replace("&amp;", "&"))
    if (!url.startsWith("https://") && !url.startsWith("http://")) return null
    val type = when {
        url.contains(".m3u8", true) -> StreamType.HLS
        url.contains(".mpd", true) -> StreamType.DASH
        url.contains(".mp4", true) -> StreamType.MP4
        else -> return null
    }
    return ProviderStream(url, quality, type, mapOf("Referer" to referer, "User-Agent" to ProviderHttpClient.USER_AGENT), voice)
}
internal fun parseMediaList(value: String, referer: String, voice: String): List<ProviderStream> =
    value.split(',').flatMap { part ->
        val quality = Regex("^\\[(\\d{3,4})").find(part.trim())?.groupValues?.get(1)?.toIntOrNull() ?: 0
        part.replace(Regex("^\\s*\\[[^]]*]"), "").split(" or ").mapNotNull { mediaStream(it.trim(), referer, voice, quality) }
    }.distinctBy { it.stableKey }
