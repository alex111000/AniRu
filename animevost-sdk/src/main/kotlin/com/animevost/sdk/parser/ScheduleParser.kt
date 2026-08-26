package com.animevost.sdk.parser

import com.animevost.sdk.model.ScheduleEntry
import com.animevost.sdk.model.ScheduleDay
import com.animevost.sdk.model.Weekday
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class ScheduleParser {
    fun parse(html: String, baseUrl: String = "https://animevost.org/"): List<ScheduleDay> {
        val doc = Jsoup.parse(html, normalizeBaseUrl(baseUrl))

        return Weekday.entries.mapNotNull { weekday ->
            val container = doc.getElementById(weekday.containerId) ?: return@mapNotNull null
            ScheduleDay(
                weekday = weekday,
                entries = container.select("a[href]")
                    .mapNotNull { link -> parseEntry(link) },
            )
        }
    }

    private fun parseEntry(link: Element): ScheduleEntry? {
        val url = link.absUrl("href")
        if (!animeDetailUrlRegex.containsMatchIn(url)) return null

        val text = link.text().trim()
        if (text.isBlank()) return null

        val timeMatch = timeSuffixRegex.find(text)
        val title = if (timeMatch != null) {
            text.substring(0, timeMatch.range.first)
        } else {
            text
        }.trim().removeSuffix("~").trim()

        if (title.isBlank()) return null

        return ScheduleEntry(
            title = title,
            url = url,
            timeLabel = timeMatch?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() },
            posterUrl = findPosterUrl(link),
        )
    }

    /**
     * AnimeVost normally renders schedule as text-only links, but mirrors/themes can
     * occasionally include an image inside the link. Capture it when present.
     * The TV app still has a details-page fallback when this returns null.
     */
    private fun findPosterUrl(link: Element): String? =
        PosterUrlParser.fromElement(link.selectFirst("img, source"), link.baseUri())

    private fun normalizeBaseUrl(baseUrl: String): String =
        baseUrl.trim().trimEnd('/') + "/"

    private companion object {
        val animeDetailUrlRegex = Regex("""/tip/[^/]+/\d+-[^/]+\.html(?:[#?].*)?$""")
        val timeSuffixRegex = Regex("""~?\s*\(([^()]*)\)\s*$""")
    }
}
