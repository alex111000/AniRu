package com.animevost.sdk.parser

import com.animevost.sdk.model.VideoSource
import org.jsoup.Jsoup
import java.net.URI

class VideoSourceParser {
    fun parse(response: String): List<VideoSource> {
        val downloadUrls = parseDownloadUrls(response)
        val playerSources = parsePlayerSources(response, downloadUrls)
        if (playerSources.isNotEmpty()) return playerSources

        val downloadSources = downloadUrls.mapNotNull { (quality, downloadUrl) ->
            parseSource(
                rawUrl = downloadUrl.removeDownloadFlag(),
                quality = quality,
                downloadUrl = downloadUrl,
            )
        }
        if (downloadSources.isNotEmpty()) return downloadSources

        return sourceUrlRegex.findAll(response)
            .map { it.value.trim() }
            .mapNotNull { parseSource(rawUrl = it, quality = DefaultQuality, downloadUrl = null) }
            .toList()
    }

    private fun parsePlayerSources(response: String, downloadUrls: Map<String, String>): List<VideoSource> =
        fileValueRegex.findAll(response)
            .map { it.groupValues[1].unescapeJavaScriptString() }
            .filter { it.contains("http") }
            .flatMap { parseQualitySegments(it, downloadUrls).asSequence() }
            .distinctBy { it.quality }
            .toList()

    private fun parseQualitySegments(fileValue: String, downloadUrls: Map<String, String>): List<VideoSource> =
        qualitySegmentRegex.findAll(fileValue)
            .mapNotNull { match ->
                val quality = normalizeQuality(match.groupValues[1])
                val streamUrl = sourceUrlRegex.find(match.groupValues[2])?.value?.trim()
                    ?: return@mapNotNull null
                parseSource(
                    rawUrl = streamUrl,
                    quality = quality,
                    downloadUrl = findDownloadUrl(quality, downloadUrls),
                )
            }
            .toList()

    private fun parseDownloadUrls(response: String): Map<String, String> {
        val doc = Jsoup.parse(response)
        return doc.select("a.butt[download][href]")
            .mapNotNull { link ->
                val quality = link.text().trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val url = link.absUrl("href").ifBlank { link.attr("href") }.trim()
                if (url.isBlank()) return@mapNotNull null
                normalizeQuality(quality) to url
            }
            .toMap()
    }

    private fun findDownloadUrl(quality: String, downloadUrls: Map<String, String>): String? {
        val targetKey = qualityKey(quality)
        return downloadUrls.entries.firstOrNull { (candidate, _) ->
            qualityKey(candidate) == targetKey
        }?.value
    }

    private fun parseSource(rawUrl: String, quality: String, downloadUrl: String?): VideoSource? {
        if (rawUrl.isBlank()) return null
        val cleanUrl = rawUrl.trim().trimEnd('"', '\'')
        val uri = runCatching { URI(cleanUrl) }.getOrNull() ?: return null
        if (uri.scheme !in setOf("http", "https")) return null
        return VideoSource(
            quality = quality,
            url = cleanUrl,
            downloadUrl = downloadUrl,
            host = uri.host,
        )
    }

    private fun String.removeDownloadFlag(): String =
        replace("&d=1", "")
            .replace("?d=1&", "?")
            .replace("?d=1", "")

    private fun String.unescapeJavaScriptString(): String =
        replace("\\/", "/")
            .replace("\\\"", "\"")
            .replace("\\u0026", "&")

    private fun normalizeQuality(quality: String): String =
        quality.trim()
            .replace('\u0440', 'p')
            .replace('\u0420', 'P')

    private fun qualityKey(quality: String): String {
        val normalized = normalizeQuality(quality)
        qualityNumberRegex.find(normalized)?.let { return it.groupValues[1] }
        return normalized.uppercase()
            .replace(Regex("""[^A-Z0-9]+"""), "")
    }

    private companion object {
        const val DefaultQuality = "default"
        val fileValueRegex = Regex(""""file"\s*:\s*"([^"]+)"""")
        val qualitySegmentRegex = Regex("""\[([^\]]+)]([^\[]*)""")
        val qualityNumberRegex = Regex("""(\d{3,4})\s*[pP]""")
        val sourceUrlRegex = Regex("""https?://[^\s,]+""")
    }
}
