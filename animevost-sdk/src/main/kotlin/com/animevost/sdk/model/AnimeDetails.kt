package com.animevost.sdk.model

data class AnimeDetails(
    val id: Int,
    val url: String,
    val title: String,
    val originalTitle: String?,
    val episodeInfo: String?,
    val alternativeTitle: String?,
    val posterUrl: String?,
    val publishedDate: String?,
    val viewCount: Int?,
    val commentCount: Int?,
    val year: String?,
    val genres: List<String>,
    val type: String?,
    val episodeCount: String?,
    val director: String?,
    val rating: Double?,
    val voteCount: Int?,
    val description: String?,
    val categories: List<AnimeCategory>,
    val relatedSeries: List<RelatedSeries>,
    val episodes: List<AnimeEpisode>,
)

data class RelatedSeries(
    val title: String,
    val url: String,
    val description: String?,
)

data class AnimeEpisode(
    val name: String,
    val videoId: String,
    val number: Int?,
    val thumbnailUrl: String?,
    val directUrl: String? = null,
    val standardUrl: String? = null,
)
