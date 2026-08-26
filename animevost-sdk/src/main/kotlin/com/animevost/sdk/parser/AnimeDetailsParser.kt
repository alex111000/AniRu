package com.animevost.sdk.parser

import com.animevost.sdk.model.AnimeCategory
import com.animevost.sdk.model.AnimeDetails
import com.animevost.sdk.model.AnimeEpisode
import com.animevost.sdk.model.RelatedSeries
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI

class AnimeDetailsParser {
    fun parse(
        html: String,
        pageUrl: String? = null,
        baseUrl: String = "https://animevost.org/",
    ): AnimeDetails {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        val doc = Jsoup.parse(html, normalizedBaseUrl)
        val story = doc.selectFirst("div.shortstory") ?: doc
        val content = story.selectFirst(".shortstoryContent") ?: story
        val titleParts = AnimeTitleParser.split(
            story.selectFirst(".shortstoryHead h1")?.text()?.trim().orEmpty(),
        )
        val url = resolvePageUrl(story, pageUrl, normalizedBaseUrl)
        val voteSpan = story.selectFirst("""span[id^="vote-num-id-"]""")
        val staticInfo = story.selectFirst(".staticInfo")

        return AnimeDetails(
            id = extractId(voteSpan?.id().orEmpty())
                ?: extractId(url)
                ?: extractId(pageUrl.orEmpty())
                ?: 0,
            url = url,
            title = titleParts.title,
            originalTitle = titleParts.originalTitle,
            episodeInfo = titleParts.episodeInfo,
            alternativeTitle = content.selectFirst("""h4[itemprop="name"], h4""")
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotBlank() },
            posterUrl = PosterUrlParser.fromElement(
                content.selectFirst("img[itemprop=image], img.imgRadius, .shortstoryPoster img, img"),
                normalizedBaseUrl,
            ) ?: PosterUrlParser.fromDocumentMeta(doc, normalizedBaseUrl),
            publishedDate = staticInfo
                ?.selectFirst(".staticInfoLeftData")
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotBlank() },
            viewCount = parseInt(staticInfo?.selectFirst(".staticInfoRightSmotr")?.text()),
            commentCount = parseInt(staticInfo?.selectFirst("""#dle-comm-link, a[href*="#comment"]""")?.text()),
            year = extractField(content, "Год выхода"),
            genres = parseGenres(content),
            type = extractField(content, "Тип"),
            episodeCount = extractField(content, "Количество серий"),
            director = content.selectFirst("""span[itemprop="director"]""")
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: extractField(content, "Режиссёр")
                ?: extractField(content, "Режиссер"),
            rating = parseRating(story),
            voteCount = parseInt(voteSpan?.text()),
            description = content.selectFirst("""span[itemprop="description"]""")
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: extractField(content, "Описание"),
            categories = parseCategories(story),
            relatedSeries = parseRelatedSeries(story),
            episodes = parseEpisodes(html),
        )
    }

    private fun resolvePageUrl(story: Element, pageUrl: String?, baseUrl: String): String {
        val candidate = story.ownerDocument()
            ?.selectFirst("""meta[property="og:url"]""")
            ?.attr("content")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: pageUrl.orEmpty()
        return resolveUrl(candidate, baseUrl)
    }

    private fun parseGenres(content: Element): List<String> =
        extractField(content, "Жанр")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()

    private fun extractField(content: Element, label: String): String? {
        for (element in content.select("p, div")) {
            val strong = element.selectFirst("> strong") ?: continue
            val strongText = strong.text().trim()
            if (!strongText.trimEnd(':').equals(label, ignoreCase = true)) continue

            val value = element.text()
                .removePrefix(strongText)
                .trim()
                .takeIf { it.isNotBlank() }
            if (value != null) return value
        }
        return null
    }

    private fun parseRating(story: Element): Double? {
        val rating = story.selectFirst(".current-rating") ?: return null
        val percent = ratingWidthRegex.find(rating.attr("style"))
            ?.groupValues
            ?.get(1)
            ?.toDoubleOrNull()
            ?: rating.text().trim().toDoubleOrNull()
            ?: return null
        return percent / 20.0
    }

    private fun parseCategories(story: Element): List<AnimeCategory> =
        story.select(".shortstoryFuter span a[href]").mapNotNull { link ->
            val title = link.text().trim()
            val url = link.absUrl("href")
            if (title.isBlank() || url.isBlank()) return@mapNotNull null
            AnimeCategory(title = title, url = url)
        }

    private fun parseRelatedSeries(story: Element): List<RelatedSeries> {
        val spoiler = story.select(".title_spoiler")
            .firstOrNull { it.text().contains("состоит", ignoreCase = true) }
            ?.nextElementSibling()
            ?.takeIf { it.hasClass("text_spoiler") }
            ?: return emptyList()

        return spoiler.select("li").mapNotNull { item ->
            val link = item.selectFirst("a[href]") ?: return@mapNotNull null
            val title = link.attr("title").ifBlank { link.text() }.trim()
            val url = link.absUrl("href")
            if (title.isBlank() || url.isBlank()) return@mapNotNull null

            RelatedSeries(
                title = title,
                url = url,
                description = normalizeText(item.ownText())
                    .trimStart('-', ' ')
                    .takeIf { it.isNotBlank() },
            )
        }
    }

    private fun parseEpisodes(html: String): List<AnimeEpisode> {
        val data = dataBlockRegex.find(html)?.groupValues?.get(1) ?: return emptyList()
        return dataEntryRegex.findAll(data).map { match ->
            val name = match.groupValues[1].trim()
            val videoId = match.groupValues[2].trim()
            AnimeEpisode(
                name = name,
                videoId = videoId,
                number = episodeNumberRegex.find(name)?.value?.toIntOrNull(),
                thumbnailUrl = "$thumbnailBaseUrl$videoId.jpg",
            )
        }.toList()
    }

    private fun resolveUrl(rawUrl: String, baseUrl: String): String {
        val value = rawUrl.trim()
        if (value.isBlank()) return ""
        return runCatching { URI(baseUrl).resolve(value).toString() }.getOrDefault(value)
    }

    private fun extractId(value: String): Int? =
        idRegex.find(value)?.groupValues?.get(1)?.toIntOrNull()

    private fun parseInt(text: String?): Int? =
        text?.replace(nonDigitsRegex, "")?.takeIf { it.isNotBlank() }?.toIntOrNull()

    private fun normalizeText(text: String): String =
        text.replace('\u00A0', ' ')
            .replace(whitespaceRegex, " ")
            .trim()

    private fun normalizeBaseUrl(baseUrl: String): String =
        baseUrl.trim().trimEnd('/') + "/"

    private companion object {
        const val thumbnailBaseUrl = "https://media.aniland.org/img/"
        val dataBlockRegex = Regex("""var\s+data\s*=\s*[{](.*?)[}]\s*;""", RegexOption.DOT_MATCHES_ALL)
        val dataEntryRegex = Regex("""["']([^"']+)["']\s*:\s*["'](\d+)["']""")
        val episodeNumberRegex = Regex("""\d+""")
        val idRegex = Regex("""(?:vote-num-id-|/)(\d+)(?:-|$)""")
        val ratingWidthRegex = Regex("""width:\s*(\d+(?:\.\d+)?)%""")
        val nonDigitsRegex = Regex("""[^\d]""")
        val whitespaceRegex = Regex("""\s+""")
    }
}
