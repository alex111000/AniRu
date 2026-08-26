package com.animevost.sdk.config

data class AnimeVostConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiBaseUrl: String = DEFAULT_API_BASE_URL,
    val userAgent: String = DEFAULT_USER_AGENT,
) {
    init {
        require(baseUrl.isNotBlank()) { "baseUrl must not be blank" }
        require(apiBaseUrl.isNotBlank()) { "apiBaseUrl must not be blank" }
        require(userAgent.isNotBlank()) { "userAgent must not be blank" }
    }
}

const val DEFAULT_BASE_URL = "https://animevost.org/"
const val DEFAULT_API_BASE_URL = "https://api.animevost.org/"

const val DEFAULT_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
