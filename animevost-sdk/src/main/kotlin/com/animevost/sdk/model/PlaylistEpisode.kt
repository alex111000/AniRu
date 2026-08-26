package com.animevost.sdk.model

/** Direct AnimeVost playlist entry returned by api.animevost.org/v1/playlist. */
data class PlaylistEpisode(
    val name: String,
    val number: Int?,
    val hdUrl: String?,
    val standardUrl: String?,
    val previewUrl: String?,
)
