package com.animevost.sdk.parser

import com.animevost.sdk.model.AnimeComment
import com.animevost.sdk.model.CommentAction
import com.animevost.sdk.model.CommentAuthor
import com.animevost.sdk.model.CommentPage
import com.animevost.sdk.model.CommentQuote
import org.jsoup.Jsoup
import org.jsoup.nodes.Comment
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

class CommentsParser {
    fun parsePage(
        html: String,
        pageUrl: String? = null,
        baseUrl: String = "https://animevost.org/",
    ): CommentPage {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        val doc = Jsoup.parse(html, pageUrl ?: normalizedBaseUrl)
        return parseDocument(
            root = doc,
            newsId = parseScriptInt(html, "dle_news_id") ?: extractNewsId(pageUrl.orEmpty()),
            allowHash = parseScriptString(html, "dle_login_hash"),
            currentPage = parseScriptInt(html, "current_comments_page") ?: 1,
            totalPages = parseScriptInt(html, "total_comments_pages"),
        )
    }

    fun parseComments(
        html: String,
        newsId: Int? = null,
        currentPage: Int = 1,
        totalPages: Int? = null,
        allowHash: String? = null,
        baseUrl: String = "https://animevost.org/",
    ): CommentPage {
        val doc = Jsoup.parse(html, normalizeBaseUrl(baseUrl))
        return parseDocument(
            root = doc,
            newsId = newsId,
            allowHash = allowHash ?: parseScriptString(html, "dle_login_hash"),
            currentPage = currentPage,
            totalPages = totalPages,
        )
    }

    private fun parseDocument(
        root: Element,
        newsId: Int?,
        allowHash: String?,
        currentPage: Int,
        totalPages: Int?,
    ): CommentPage {
        val comments = root.select("""div[id^="comment-id-"]""")
            .mapNotNull(::parseComment)
            .withNormalizedDepth()

        return CommentPage(
            newsId = newsId,
            allowHash = allowHash?.takeIf { it.isNotBlank() },
            comments = comments,
            currentPage = currentPage,
            totalPages = totalPages,
        )
    }

    private fun parseComment(root: Element): AnimeComment? {
        val id = root.id()
            .removePrefix("comment-id-")
            .toIntOrNull()
            ?: return null
        val content = root.children()
            .firstOrNull { child -> child.classNames().any { indentLevelRegex.matches(it) } }
            ?: root
        val authorBlock = content.selectFirst(".commentFinalAva")
        val authorLink = authorBlock?.selectFirst("strong a[href]")
        val body = content.selectFirst("""div[id^="comm-id-"]""")
            ?: content.selectFirst(".commentFinalText")
            ?: return null
        val metadata = parseMetadata(content.selectFirst(".commentFinalData"))
        val bodyContent = parseBody(body)

        return AnimeComment(
            id = id,
            author = CommentAuthor(
                name = authorLink?.text()?.trim().orEmpty(),
                profileUrl = authorLink?.absUrl("href")?.takeIf { it.isNotBlank() },
            ),
            avatarUrl = authorBlock
                ?.selectFirst("img[src]")
                ?.absUrl("src")
                ?.takeIf { it.isNotBlank() },
            userGroup = authorBlock
                ?.select("> span")
                ?.firstOrNull { it.selectFirst("strong a") == null }
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotBlank() },
            createdAtLabel = metadata.createdAtLabel,
            authorCommentCount = metadata.authorCommentCount,
            ordinal = metadata.ordinal,
            bodyHtml = bodyContent.html,
            bodyText = bodyContent.text,
            quotes = bodyContent.quotes,
            indentLevel = parseIndentLevel(content),
            depth = 0,
            isOnline = parseOnlineStatus(content),
            actions = parseActions(content),
        )
    }

    private fun List<AnimeComment>.withNormalizedDepth(): List<AnimeComment> {
        val baseIndent = mapNotNull { it.indentLevel }.minOrNull()
        return map { comment ->
            val depth = if (baseIndent == null || comment.indentLevel == null) {
                0
            } else {
                ((comment.indentLevel - baseIndent) / INDENT_STEP).coerceAtLeast(0)
            }
            comment.copy(depth = depth)
        }
    }

    private fun parseMetadata(element: Element?): CommentMetadata {
        val metadataText = normalizeText(element?.ownText().orEmpty())
        val authorCommentCount = commentCountRegex.find(metadataText)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
        val createdAtLabel = commentCountRegex.replace(metadataText, "")
            .trim()
            .takeIf { it.isNotBlank() }
        val ordinal = element
            ?.selectFirst("span")
            ?.text()
            ?.replace(nonDigitsRegex, "")
            ?.takeIf { it.isNotBlank() }
            ?.toIntOrNull()

        return CommentMetadata(
            createdAtLabel = createdAtLabel,
            authorCommentCount = authorCommentCount,
            ordinal = ordinal,
        )
    }

    private fun parseBody(element: Element): ParsedBody {
        val clone = element.clone()
        val quotes = extractDirectQuotes(clone)
        removeCommentNodes(clone)
        trimEdges(clone)

        return ParsedBody(
            html = clone.html().trim(),
            text = normalizeText(clone.text()),
            quotes = quotes,
        )
    }

    private fun extractDirectQuotes(container: Element): List<CommentQuote> =
        container.children()
            .filter { it.hasClass("titlequote") }
            .mapNotNull { title ->
                val quote = title.nextElementSibling()?.takeIf { it.hasClass("quote") }
                    ?: return@mapNotNull null
                val parsed = parseQuote(title, quote)
                title.remove()
                quote.remove()
                parsed
            }

    private fun parseQuote(title: Element, quote: Element): CommentQuote {
        val quoteClone = quote.clone()
        val nestedQuotes = extractDirectQuotes(quoteClone)
        removeCommentNodes(quoteClone)
        trimEdges(quoteClone)

        return CommentQuote(
            authorName = quoteAuthorPrefixRegex.replace(title.text(), "")
                .trim()
                .takeIf { it.isNotBlank() },
            bodyHtml = quoteClone.html().trim(),
            bodyText = normalizeText(quoteClone.text()),
            quotes = nestedQuotes,
        )
    }

    private fun removeCommentNodes(element: Element) {
        element.childNodes().toList().forEach { node ->
            when (node) {
                is Comment -> node.remove()
                is Element -> removeCommentNodes(node)
            }
        }
    }

    private fun trimEdges(element: Element) {
        trimEdge(element, fromStart = true)
        trimEdge(element, fromStart = false)
    }

    private fun trimEdge(element: Element, fromStart: Boolean) {
        while (element.childNodeSize() > 0) {
            val index = if (fromStart) 0 else element.childNodeSize() - 1
            val node = element.childNode(index)
            val shouldRemove = when (node) {
                is TextNode -> node.text().isBlank()
                is Element -> node.tagName().equals("br", ignoreCase = true)
                else -> false
            }
            if (!shouldRemove) return
            node.remove()
        }
    }

    private fun parseIndentLevel(element: Element): Int? =
        element.classNames()
            .firstNotNullOfOrNull { className ->
                indentLevelRegex.find(className)?.groupValues?.get(1)?.toIntOrNull()
            }

    private fun parseOnlineStatus(element: Element): Boolean? {
        val status = element.selectFirst(".commentFinalIt span")
            ?.text()
            ?.trim()
            .orEmpty()
        return when {
            status.equals("Онлайн", ignoreCase = true) -> true
            status.equals("Оффлайн", ignoreCase = true) -> false
            else -> null
        }
    }

    private fun parseActions(element: Element): Set<CommentAction> {
        val actionsHtml = element.selectFirst(".commentFinalIt")?.html().orEmpty()
        return buildSet {
            if (actionsHtml.contains("dle_ins(")) add(CommentAction.REPLY)
            if (actionsHtml.contains("AddComplaint(")) add(CommentAction.REPORT)
            if (actionsHtml.contains("DeleteComments(")) add(CommentAction.DELETE)
            if (actionsHtml.contains("ajax_comm_edit(")) add(CommentAction.EDIT)
        }
    }

    private fun parseScriptInt(html: String, variableName: String): Int? =
        Regex("""var\s+${Regex.escape(variableName)}\s*=\s*['"](\d+)['"]""")
            .find(html)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()

    private fun parseScriptString(html: String, variableName: String): String? =
        Regex("""var\s+${Regex.escape(variableName)}\s*=\s*['"]([^'"]*)['"]""")
            .find(html)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun extractNewsId(value: String): Int? =
        newsIdRegex.find(value)?.groupValues?.get(1)?.toIntOrNull()

    private fun normalizeText(text: String): String =
        text.replace('\u00A0', ' ')
            .replace(whitespaceRegex, " ")
            .trim()

    private fun normalizeBaseUrl(baseUrl: String): String =
        baseUrl.trim().trimEnd('/') + "/"

    private data class ParsedBody(
        val html: String,
        val text: String,
        val quotes: List<CommentQuote>,
    )

    private data class CommentMetadata(
        val createdAtLabel: String?,
        val authorCommentCount: Int?,
        val ordinal: Int?,
    )

    private companion object {
        const val INDENT_STEP = 5
        val indentLevelRegex = Regex("""commentContent_(\d+)""")
        val commentCountRegex = Regex("""Комментарий:\s*(\d+)""")
        val nonDigitsRegex = Regex("""[^\d]""")
        val quoteAuthorPrefixRegex = Regex("""^\s*Цитата:\s*""", RegexOption.IGNORE_CASE)
        val newsIdRegex = Regex("""/(\d+)-[^/]+\.html(?:[#?].*)?$""")
        val whitespaceRegex = Regex("""\s+""")
    }
}
