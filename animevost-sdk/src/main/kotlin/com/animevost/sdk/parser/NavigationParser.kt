package com.animevost.sdk.parser

import com.animevost.sdk.model.CatalogLink
import com.animevost.sdk.model.NavigationData
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI

class NavigationParser {
    fun parse(
        html: String,
        baseUrl: String = "https://animevost.org/",
    ): NavigationData {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        val doc = Jsoup.parse(html, normalizedBaseUrl)
        val nav = doc.selectFirst("div.menu ul#topnav") ?: return NavigationData()

        return NavigationData(
            genres = nav.catalogLinks("""^/zhanr/[^/]+/$""", normalizedBaseUrl),
            types = nav.catalogLinks("""^/tip/[^/]+/$""", normalizedBaseUrl),
            years = nav.catalogLinks("""^/god/\d{4}/$""", normalizedBaseUrl)
                .sortedByDescending { it.title },
            sections = nav.catalogLinks("""^/(ongoing|preview)/$""", normalizedBaseUrl),
        )
    }

    private fun Element.catalogLinks(
        hrefPattern: String,
        baseUrl: String,
    ): List<CatalogLink> {
        val regex = Regex(hrefPattern)
        return select("a[href]")
            .asSequence()
            .filter { link -> regex.matches(link.attr("href").trim()) }
            .mapNotNull { link -> link.toCatalogLink(baseUrl) }
            .distinctBy { it.path }
            .toList()
    }

    private fun Element.toCatalogLink(baseUrl: String): CatalogLink? {
        val title = text().trim()
        val href = attr("href").trim()
        if (title.isBlank() || href.isBlank()) return null

        val url = absUrl("href").ifBlank { resolveUrl(href, baseUrl) }
        val path = toPath(url).ifBlank { href.trimStart('/') }
        if (url.isBlank() || path.isBlank()) return null

        return CatalogLink(
            title = title,
            url = url,
            path = path,
        )
    }

    private fun toPath(url: String): String =
        runCatching {
            URI(url).rawPath.trimStart('/')
        }.getOrDefault("")

    private fun resolveUrl(href: String, baseUrl: String): String =
        runCatching { URI(baseUrl).resolve(href).toString() }.getOrDefault(href)

    private fun normalizeBaseUrl(baseUrl: String): String =
        baseUrl.trim().trimEnd('/') + "/"
}
