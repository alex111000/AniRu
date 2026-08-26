package ru.radiationx.anilibria.common

import ru.radiationx.data.entity.domain.types.ReleaseId

data class LibriaCard(
    val title: String,
    val description: String,
    val image: String,
    val type: Type
) : CardItem {

    override fun getId(): Int {
        return type.hashCode()
    }

    sealed class Type {
        data class Release(val releaseId: ReleaseId) : Type()
        data class Youtube(val link: String) : Type()
        data class AnimeVost(val animeUrl: String) : Type()
        data class Provider(
            val providerId: String,
            val animeId: String,
        ) : Type()
        data class ProviderEpisode(
            val providerId: String,
            val animeId: String,
            val episodeId: String,
            val episodeNumber: Int?,
            val sourceId: String? = null,
            val directPlay: Boolean = false,
        ) : Type()
        data class AnimeVostCatalog(val path: String?, val title: String) : Type()
        data class AnimeVostEpisode(
            val animeUrl: String,
            val videoId: String,
            val episodeName: String,
            val episodeNumber: Int?,
        ) : Type()
    }
}