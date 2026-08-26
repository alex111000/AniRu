package ru.radiationx.anilibria.provider

import kotlin.math.max

/**
 * Conservative title matcher used only for automatic playback failover.
 *
 * Automatic provider failover must prefer a false negative over opening a
 * different anime. Alternate titles separated with common delimiters are
 * treated as independent exact candidates (for example "Ван Пис / One Piece").
 */
object ProviderTitleMatcher {
    fun score(query: String, title: String, originalTitle: String = ""): Int {
        val q = normalize(query)
        if (q.isBlank()) return 0

        val candidates = listOf(title, originalTitle)
            .flatMap(::candidateVariants)
            .filter(String::isNotBlank)
            .distinct()

        return candidates.maxOfOrNull { candidate ->
            when {
                candidate == q -> 100
                candidate.contains(q) || q.contains(candidate) -> 85
                else -> {
                    val qTokens = q.split(' ').filter(String::isNotBlank).toSet()
                    val cTokens = candidate.split(' ').filter(String::isNotBlank).toSet()
                    if (qTokens.isEmpty() || cTokens.isEmpty()) 0
                    else 100 * qTokens.intersect(cTokens).size / max(qTokens.size, cTokens.size)
                }
            }
        } ?: 0
    }

    private fun candidateVariants(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        val parts = value.split(Regex("\\s*(?:/|\\||｜|•|·)\\s*"))
        return buildList {
            add(normalize(value))
            parts.forEach { add(normalize(it)) }
        }
    }

    fun normalize(value: String): String = value
        .lowercase()
        .replace('ё', 'е')
        .replace(Regex("[^a-zа-я0-9]+"), " ")
        .trim()
}
