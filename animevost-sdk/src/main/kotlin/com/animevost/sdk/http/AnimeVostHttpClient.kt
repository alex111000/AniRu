package com.animevost.sdk.http

interface AnimeVostHttpClient {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String

    suspend fun post(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
    ): String

    suspend fun postMultipart(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
    ): String

    fun getCookie(name: String): String?

    fun clearCookies()
}
