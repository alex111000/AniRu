package ru.radiationx.anilibria.provider

/**
 * Stable provider contract used by AniRu's multi-source layer.
 *
 * Provider-specific ids never leak into AniLibria ReleaseId. This prevents
 * collisions such as AniLibria #179 and a different provider's #179.
 */
interface AnimeProvider {
    val id: ProviderId
    val displayName: String
    val capabilities: ProviderCapabilities

    suspend fun search(query: String): List<ProviderAnime>

    suspend fun getDetails(animeId: String): ProviderAnimeDetails

    suspend fun getSources(animeId: String, episodeId: String): List<ProviderSource>

    suspend fun browse(page: Int = 1): List<ProviderAnime> = emptyList()

    /**
     * Lightweight availability probe. It must never throw to callers.
     */
    suspend fun isAvailable(): Boolean
}

enum class ProviderId(val wireId: String, val uiName: String) {
    ANILIBRIA("anilibria", "AniLibria"),
    ANIMEVOST("animevost", "AnimeVost"),
    YUMMY_ANIME("yummy_anime", "YummyAnime"),
    SAMEBAND("sameband", "SameBand"),
    ;

    companion object {
        fun fromWireId(value: String): ProviderId? = entries.firstOrNull { it.wireId == value }
    }
}

data class ProviderCapabilities(
    val search: Boolean = true,
    val details: Boolean = true,
    val playback: Boolean = true,
    val multipleVoices: Boolean = false,
    val browse: Boolean = false,
)

data class ProviderAnime(
    val provider: ProviderId,
    val id: String,
    val title: String,
    val originalTitle: String = "",
    val description: String = "",
    val posterUrl: String = "",
    val year: String = "",
    val extra: String = "",
)

data class ProviderAnimeDetails(
    val provider: ProviderId,
    val id: String,
    val title: String,
    val originalTitle: String = "",
    val description: String = "",
    val posterUrl: String = "",
    val year: String = "",
    val extra: String = "",
    val genres: List<String> = emptyList(),
    val episodes: List<ProviderEpisode> = emptyList(),
)

data class ProviderEpisode(
    val id: String,
    val number: Int?,
    val title: String,
    val thumbnailUrl: String = "",
)

data class ProviderSource(
    val id: String,
    val title: String,
    val player: String,
    val streams: List<ProviderStream>,
)

data class ProviderStream(
    val url: String,
    val quality: Int,
    val type: StreamType,
    val headers: Map<String, String> = emptyMap(),
    val sourceTitle: String = "",
) {
    val stableKey: String
        get() = "$quality|$url"
}

enum class StreamType {
    MP4,
    HLS,
    DASH,
    UNKNOWN,
}
