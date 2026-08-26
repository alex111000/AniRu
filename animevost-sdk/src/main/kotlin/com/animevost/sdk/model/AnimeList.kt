package com.animevost.sdk.model

data class AnimePage(
    val items: List<AnimePreview>,
    val currentPage: Int,
    val totalPages: Int,
)

data class AnimePreview(
    val id: Int,
    val title: String,
    val originalTitle: String?,
    val episodeInfo: String?,
    val url: String,
    val posterUrl: String?,
    val publishedDate: String?,
    val viewCount: Int?,
    val commentCount: Int?,
    val rating: Double?,
    val voteCount: Int?,
    val categories: List<AnimeCategory>,
)

data class AnimeCategory(
    val title: String,
    val url: String,
)
