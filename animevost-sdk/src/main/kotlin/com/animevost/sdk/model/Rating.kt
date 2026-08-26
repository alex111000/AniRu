package com.animevost.sdk.model

data class RatingVoteResult(
    val newsId: Int,
    val submittedRating: Int,
    val rating: Double?,
    val voteCount: Int?,
    val ratingHtml: String?,
    val success: Boolean,
)
