package com.animevost.sdk.parser

import com.animevost.sdk.model.AnimePreview
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class RandomAnimeParser {
    fun parse(
        html: String,
        baseUrl: String = "https://animevost.org/",
    ): AnimePreview? {
        val doc = Jsoup.parse(html, normalizeBaseUrl(baseUrl))
        val link = doc.selectFirst("a[href]") ?: return null
        val url = link.absUrl("href")
        if (url.isBlank()) return null

        val titleParts = AnimeTitleParser.split(
            link.selectFirst("span")?.text()?.trim()
                ?: link.text().trim(),
        )
        val stats = parseStats(doc.body())

        return AnimePreview(
            id = extractId(url) ?: return null,
            title = titleParts.title,
            originalTitle = titleParts.originalTitle,
            episodeInfo = titleParts.episodeInfo,
            url = url,
            posterUrl = PosterUrlParser.fromElement(doc.selectFirst("img, source"), normalizeBaseUrl(baseUrl))
                ?: PosterUrlParser.fromDocumentMeta(doc, normalizeBaseUrl(baseUrl)),
            publishedDate = null,
            viewCount = stats.views,
            commentCount = stats.comments,
            rating = null,
            voteCount = null,
            categories = emptyList(),
        )
    }

    private fun parseStats(root: Element): RandomStats {
        val text = root.selectFirst(".imgOngoingVie")?.text().orEmpty()
        return RandomStats(
            views = statsRegex.find(text)
                ?.groupValues
                ?.get(1)
                ?.let(::parseInt),
            comments = statsRegex.find(text)
                ?.groupValues
                ?.get(2)
                ?.let(::parseInt),
        )
    }

    private fun extractId(url: String): Int? =
        idInUrlRegex.find(url)?.groupValues?.get(1)?.toIntOrNull()

    private fun parseInt(text: String): Int? =
        text.replace(nonDigitsRegex, "").takeIf { it.isNotBlank() }?.toIntOrNull()

    private fun normalizeBaseUrl(baseUrl: String): String =
        baseUrl.trim().trimEnd('/') + "/"

    private data class RandomStats(
        val views: Int?,
        val comments: Int?,
    )

    private companion object {
        val idInUrlRegex = Regex("""/(\d+)-[^/]+\.html(?:[#?].*)?$""")
        val statsRegex = Regex("""Просмотров:\s*([\d\s]+).*Комментарий:\s*([\d\s]+)""")
        val nonDigitsRegex = Regex("""[^\d]""")
    }
}
