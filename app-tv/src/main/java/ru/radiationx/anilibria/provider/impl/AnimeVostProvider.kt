package ru.radiationx.anilibria.provider.impl

import ru.radiationx.anilibria.animevost.AnimeVostRepository
import ru.radiationx.anilibria.provider.AnimeProvider
import ru.radiationx.anilibria.provider.ProviderAnime
import ru.radiationx.anilibria.provider.ProviderAnimeDetails
import ru.radiationx.anilibria.provider.ProviderCapabilities
import ru.radiationx.anilibria.provider.ProviderEpisode
import ru.radiationx.anilibria.provider.ProviderId
import ru.radiationx.anilibria.provider.ProviderSource
import ru.radiationx.anilibria.provider.ProviderStream
import ru.radiationx.anilibria.provider.StreamType
import javax.inject.Inject

class AnimeVostProvider @Inject constructor(
    private val repository: AnimeVostRepository,
) : AnimeProvider {

    override val id = ProviderId.ANIMEVOST
    override val displayName = id.uiName
    override val capabilities = ProviderCapabilities(
        search = true,
        details = true,
        playback = true,
        multipleVoices = false,
    )

    override suspend fun search(query: String): List<ProviderAnime> {
        val result = buildList {
            for (page in 1..SEARCH_PAGES) {
                val response = runCatching { repository.search(query.trim(), page) }.getOrNull() ?: break
                addAll(response.items.map { item ->
                    ProviderAnime(
                        provider = id,
                        id = item.url,
                        title = item.title,
                        originalTitle = item.originalTitle.orEmpty(),
                        posterUrl = item.posterUrl.orEmpty(),
                        extra = buildString {
                            append(displayName)
                            item.episodeInfo?.takeIf { it.isNotBlank() }?.let { append(" • ").append(it) }
                            item.rating?.let { append(" • ★ ").append(it) }
                        },
                    )
                })
                if (page >= response.totalPages || response.items.isEmpty()) break
            }
        }
        return result.distinctBy { it.id }
    }

    override suspend fun getDetails(animeId: String): ProviderAnimeDetails {
        val details = repository.getDetails(animeId)
        return ProviderAnimeDetails(
            provider = id,
            id = details.url,
            title = details.title,
            originalTitle = details.originalTitle.orEmpty(),
            description = details.description.orEmpty(),
            posterUrl = details.posterUrl.orEmpty(),
            year = details.year.orEmpty(),
            extra = listOfNotNull(
                displayName,
                details.year?.takeIf { it.isNotBlank() },
                details.type?.takeIf { it.isNotBlank() },
                details.episodeInfo?.takeIf { it.isNotBlank() },
                details.rating?.let { "★ $it" },
                details.episodes.takeIf { it.isNotEmpty() }?.let { "Серий: ${it.size}" },
            ).joinToString(" • "),
            genres = details.genres,
            episodes = details.episodes.mapIndexed { index, episode ->
                ProviderEpisode(
                    id = episode.videoId,
                    number = episode.number ?: index + 1,
                    title = episode.name,
                    thumbnailUrl = episode.thumbnailUrl.orEmpty(),
                )
            },
        )
    }

    override suspend fun getSources(animeId: String, episodeId: String): List<ProviderSource> {
        val details = repository.getDetails(animeId)
        val episode = details.episodes.firstOrNull { it.videoId == episodeId } ?: return emptyList()
        val raw = repository.getPlaybackSources(
            animeUrl = animeId,
            videoId = episode.videoId,
            episodeNumber = episode.number,
        )
        val streams = raw.flatMap { source ->
            buildList {
                if (source.url.startsWith("http")) {
                    add(
                        ProviderStream(
                            url = source.url,
                            quality = quality(source.quality),
                            type = typeOf(source.url),
                            sourceTitle = displayName,
                        )
                    )
                }
                source.downloadUrl?.takeIf { it.startsWith("http") && it != source.url }?.let { url ->
                    add(
                        ProviderStream(
                            url = url,
                            quality = quality(source.quality),
                            type = typeOf(url),
                            sourceTitle = displayName,
                        )
                    )
                }
            }
        }.distinctBy { it.stableKey }.sortedByDescending { it.quality }
        if (streams.isEmpty()) return emptyList()
        return listOf(
            ProviderSource(
                id = "animevost:${episode.videoId}",
                title = displayName,
                player = "AnimeVost",
                streams = streams,
            )
        )
    }

    override suspend fun isAvailable(): Boolean = runCatching {
        repository.getCatalog(page = 1).items.isNotEmpty()
    }.getOrDefault(false)

    private fun quality(value: String): Int =
        Regex("(\\d{3,4})").find(value)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

    private fun typeOf(url: String): StreamType = when {
        url.contains(".m3u8", true) -> StreamType.HLS
        url.contains(".mpd", true) -> StreamType.DASH
        url.contains(".mp4", true) -> StreamType.MP4
        else -> StreamType.UNKNOWN
    }

    private companion object {
        const val SEARCH_PAGES = 3
    }
}
