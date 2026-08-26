package ru.radiationx.anilibria.screen.animevost.home

import com.animevost.sdk.model.AnimePreview
import com.animevost.sdk.model.ScheduleEntry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import ru.radiationx.anilibria.animevost.AnimeVostRepository
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class AnimeVostScheduleViewModel @Inject constructor(
    private val repository: AnimeVostRepository,
    private val cardRouter: LibriaCardRouter,
) : BaseCardsViewModel() {

    override val defaultTitle: String = "Расписание AnimeVost"

    override suspend fun getLoader(requestPage: Int): List<LibriaCard> {
        val scheduleEntries = repository.getSchedule()
            .flatMap { day -> day.entries.map { entry -> day.weekday.displayName to entry } }
            .take(MAX_SCHEDULE_CARDS)

        val visualPool = runCatching { repository.getRecentAnimeForVisuals() }
            .getOrDefault(emptyList())
            .filter { !it.posterUrl.isNullOrBlank() }

        val byUrl = visualPool.associateBy { normalizeUrl(it.url) }
        val byTitle = buildMap<String, AnimePreview> {
            visualPool.forEach { anime ->
                getOrPut(normalizeTitle(anime.title)) { anime }
                anime.originalTitle?.takeIf { it.isNotBlank() }?.let { original ->
                    getOrPut(normalizeTitle(original)) { anime }
                }
            }
        }

        // AnimeVost's schedule is generally text-only. First reuse posters from the
        // catalog cache, then resolve only the still-missing titles from their detail
        // pages. Requests are de-duplicated and deliberately limited in parallelism.
        val missingUrls = scheduleEntries
            .map { it.second }
            .filter { entry -> resolveKnownPoster(entry, byUrl, byTitle).isNullOrBlank() }
            .map { it.url }
            .distinctBy(::normalizeUrl)

        val detailPosters = resolveMissingPosters(missingUrls)

        return scheduleEntries.map { (weekday, entry) ->
            val poster = resolveKnownPoster(entry, byUrl, byTitle)
                ?: detailPosters[normalizeUrl(entry.url)]

            LibriaCard(
                title = entry.title,
                description = buildString {
                    append(weekday)
                    entry.timeLabel?.takeIf { it.isNotBlank() }?.let { append(" • $it") }
                },
                // Empty image intentionally keeps the existing AniRu logo fallback.
                image = poster.orEmpty(),
                type = LibriaCard.Type.AnimeVost(entry.url),
            )
        }
    }

    private fun resolveKnownPoster(
        entry: ScheduleEntry,
        byUrl: Map<String, AnimePreview>,
        byTitle: Map<String, AnimePreview>,
    ): String? = entry.posterUrl
        ?.takeIf { it.isNotBlank() }
        ?: byUrl[normalizeUrl(entry.url)]?.posterUrl?.takeIf { it.isNotBlank() }
        ?: byTitle[normalizeTitle(entry.title)]?.posterUrl?.takeIf { it.isNotBlank() }

    private suspend fun resolveMissingPosters(urls: List<String>): Map<String, String> = coroutineScope {
        if (urls.isEmpty()) return@coroutineScope emptyMap()

        val semaphore = Semaphore(POSTER_LOOKUP_PARALLELISM)
        val resolved = ConcurrentHashMap<String, String>()

        // Artwork is best-effort: never keep the whole schedule row spinning because
        // one mirror/detail page is slow. Completed poster lookups are kept even if
        // the overall visual-enrichment budget expires.
        withTimeoutOrNull(POSTER_LOOKUP_BUDGET_MS) {
            urls.map { url ->
                async {
                    semaphore.withPermit {
                        runCatching { repository.getPosterForAnime(url) }
                            .getOrNull()
                            ?.takeIf { it.isNotBlank() }
                            ?.let { poster -> resolved[normalizeUrl(url)] = poster }
                    }
                }
            }.awaitAll()
        }

        resolved.toMap()
    }

    override fun hasMoreCards(newCards: List<LibriaCard>, allCards: List<LibriaCard>): Boolean = false

    override fun onLibriaCardClick(card: LibriaCard) = cardRouter.navigate(card)

    private fun normalizeUrl(value: String): String =
        runCatching { URI(value.trim()).path.trimEnd('/') }
            .getOrDefault(value.trim().substringBefore('?').substringBefore('#').trimEnd('/'))
            .lowercase()

    private fun normalizeTitle(value: String): String =
        value.lowercase()
            .replace('ё', 'е')
            .replace(Regex("[^\\p{L}\\p{N}]+"), "")

    private companion object {
        const val MAX_SCHEDULE_CARDS = 50
        const val POSTER_LOOKUP_PARALLELISM = 4
        const val POSTER_LOOKUP_BUDGET_MS = 12_000L
    }
}
