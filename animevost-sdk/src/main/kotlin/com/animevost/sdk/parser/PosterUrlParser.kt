package com.animevost.sdk.parser

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

/**
 * Resolves AnimeVost artwork from normal and lazy-loaded markup.
 *
 * AnimeVost and its mirrors can move poster URLs between `src`, `data-src`,
 * `data-original` and `srcset`. Keeping this logic in one place prevents a
 * perfectly valid poster from being treated as missing by one screen/parser.
 */
internal object PosterUrlParser {

    private val directAttributes = listOf(
        "data-src",
        "data-original",
        "data-lazy-src",
        "data-url",
        "data-image",
        "src",
    )

    private val srcSetAttributes = listOf("data-srcset", "srcset")

    fun fromElement(element: Element?, baseUrl: String): String? {
        if (element == null) return null

        val candidates = if (element.tagName().equals("img", ignoreCase = true) ||
            element.tagName().equals("source", ignoreCase = true)
        ) {
            sequenceOf(element)
        } else {
            element.select("img, source").asSequence()
        }

        return candidates.firstNotNullOfOrNull { image ->
            resolveImage(image, baseUrl)
        }
    }

    fun fromDocumentMeta(document: Document, baseUrl: String): String? {
        val metaCandidates = sequenceOf(
            document.selectFirst("meta[property=og:image]"),
            document.selectFirst("meta[property=og:image:url]"),
            document.selectFirst("meta[name=twitter:image]"),
            document.selectFirst("meta[name=twitter:image:src]"),
            document.selectFirst("link[rel=image_src]"),
        ).filterNotNull()

        return metaCandidates.firstNotNullOfOrNull { node ->
            sequenceOf(node.attr("content"), node.attr("href"))
                .mapNotNull { raw -> normalize(raw, baseUrl) }
                .firstOrNull()
        }
    }

    private fun resolveImage(image: Element, baseUrl: String): String? {
        directAttributes.forEach { attribute ->
            normalize(image.attr(attribute), baseUrl)?.let { return it }
        }

        srcSetAttributes.forEach { attribute ->
            val srcSet = image.attr(attribute).trim()
            if (srcSet.isBlank()) return@forEach

            // Prefer the largest/final candidate from a conventional srcset.
            val resolved = srcSet.split(',')
                .asReversed()
                .asSequence()
                .map { item -> item.trim().split(Regex("\\s+"), limit = 2).firstOrNull().orEmpty() }
                .mapNotNull { raw -> normalize(raw, baseUrl) }
                .firstOrNull()
            if (resolved != null) return resolved
        }

        return null
    }

    private fun normalize(raw: String, baseUrl: String): String? {
        val value = raw.trim().trim('"', '\'')
        if (value.isBlank()) return null
        if (value.startsWith("data:", ignoreCase = true) ||
            value.startsWith("javascript:", ignoreCase = true) ||
            value.equals("about:blank", ignoreCase = true) ||
            value == "#"
        ) return null

        val resolved = runCatching {
            when {
                value.startsWith("//") -> "https:$value"
                value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true) -> value
                else -> URI(baseUrl.trim().trimEnd('/') + "/").resolve(value).toString()
            }
        }.getOrNull()?.trim().orEmpty()

        if (resolved.isBlank()) return null

        val lower = resolved.lowercase()
        if (placeholderMarkers.any { marker -> lower.contains(marker) }) return null
        return resolved
    }

    private val placeholderMarkers = listOf(
        "transparent.gif",
        "spacer.gif",
        "blank.gif",
        "lazy.gif",
        "loading.gif",
        "no-image",
        "no_image",
        "noimage",
    )
}
