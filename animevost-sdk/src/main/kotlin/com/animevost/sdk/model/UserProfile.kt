package com.animevost.sdk.model

data class UserProfile(
    val userId: Int?,
    val username: String,
    val avatarUrl: String?,
    val allowHash: String?,
    val fullName: String?,
    val location: String?,
    val email: String?,
    val info: String?,
) {
    val canEdit: Boolean
        get() = userId != null && !allowHash.isNullOrBlank()
}

data class UserProfileUpdate(
    val fullName: String? = null,
    val location: String? = null,
    val email: String? = null,
    val info: String? = null,
)
