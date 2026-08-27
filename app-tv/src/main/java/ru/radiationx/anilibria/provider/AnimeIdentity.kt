package ru.radiationx.anilibria.provider

import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

enum class AnimeKind { MOVIE, SERIES, UNKNOWN;
    companion object {
        fun parse(value: String): AnimeKind {
            val text = value.lowercase(Locale.ROOT)
            return when {
                Regex("movie|фильм|полнометраж|theatrical").containsMatchIn(text) -> MOVIE
                Regex("(^|\\W)tv|сериал|серии|series|ova|ona|special|спешл|тв").containsMatchIn(text) -> SERIES
                else -> UNKNOWN
            }
        }
    }
}

fun ProviderAnimeDetails.asAnime() = ProviderAnime(provider, id, title, originalTitle,
    description, posterUrl, year, extra, kind, genres, rating, addedAt, externalIds)
val ProviderAnime.reference: String get() = "${provider.wireId}|$id"

/** No substring/fuzzy match: sequels, remakes, movies and specials must stay separate. */
object AnimeIdentity {
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace('ё', 'е')
        .replace(Regex("\\[[^]]*(?:серия|серии|из|of)[^]]*]", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    fun names(item: ProviderAnime): Set<String> = listOf(item.title, item.originalTitle)
        .flatMap { it.split(" / ") }.map(::normalize).filter { it.length > 2 }.toSet()

    fun same(a: ProviderAnime, b: ProviderAnime): Boolean {
        if (a.reference == b.reference) return true
        if (a.kind != AnimeKind.UNKNOWN && b.kind != AnimeKind.UNKNOWN && a.kind != b.kind) return false
        if (a.year.isNotBlank() && b.year.isNotBlank() && a.year != b.year) return false
        val commonIds = a.externalIds.keys.intersect(b.externalIds.keys)
        if (commonIds.any { a.externalIds[it] != b.externalIds[it] }) return false
        if (commonIds.any { !a.externalIds[it].isNullOrBlank() }) return true
        if (a.year.isBlank() || b.year.isBlank() || a.kind == AnimeKind.UNKNOWN || b.kind == AnimeKind.UNKNOWN) return false
        return names(a).intersect(names(b)).isNotEmpty()
    }

    fun sameEpisode(a: ProviderEpisode, b: ProviderEpisode): Boolean =
        a.number != null && b.number != null && a.numberLabel == b.numberLabel &&
            a.season == b.season && a.special == b.special
}

data class UnifiedAnime(val versions: List<ProviderAnime>, val firstSeenAt: Long) {
    val primary: ProviderAnime get() = versions.first()
    val key: String get() = primary.reference
    val addedAt: Long get() = versions.map { it.addedAt }.filter { it > 0 }.minOrNull() ?: firstSeenAt
    val year: Int get() = versions.mapNotNull { it.year.toIntOrNull() }.maxOrNull() ?: 0
    val kind: AnimeKind get() = versions.firstOrNull { it.kind != AnimeKind.UNKNOWN }?.kind ?: AnimeKind.UNKNOWN
    val genres: List<String> get() = versions.flatMap { it.genres }.distinct()
    val rating: Double? get() = versions.firstNotNullOfOrNull { it.rating }
}

enum class CatalogOrder(val label: String) {
    ADDED("Недавно добавленные"), YEAR("Год: сначала новые"), TITLE("Название"), RATING("Рейтинг")
}

fun List<UnifiedAnime>.ordered(order: CatalogOrder): List<UnifiedAnime> = sortedWith(when (order) {
    CatalogOrder.ADDED -> compareByDescending<UnifiedAnime> { it.addedAt }.thenByDescending { it.year }
    CatalogOrder.YEAR -> compareByDescending<UnifiedAnime> { it.year }.thenByDescending { it.addedAt }
    CatalogOrder.TITLE -> compareBy { AnimeIdentity.normalize(it.primary.title) }
    CatalogOrder.RATING -> compareByDescending<UnifiedAnime> { it.rating ?: -1.0 }.thenByDescending { it.year }
}.thenBy { it.key })

fun parseProviderDate(value: String): Long {
    value.toLongOrNull()?.let { return if (it < 10_000_000_000L) it * 1000 else it }
    val normalized = value.replace(Regex("Z$"), "+0000").replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")
    for (pattern in listOf("yyyy-MM-dd'T'HH:mm:ss.SSSZ", "yyyy-MM-dd'T'HH:mm:ssZ", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd", "d-MM-yyyy")) {
        val parsed = runCatching { SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC"); isLenient = false
        }.parse(normalized)?.time }.getOrNull()
        if (parsed != null) return parsed
    }
    return 0L
}
