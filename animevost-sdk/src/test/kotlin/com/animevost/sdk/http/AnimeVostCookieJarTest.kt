package com.animevost.sdk.http

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnimeVostCookieJarTest {

    private val url = "https://example.test/".toHttpUrl()

    @Test
    fun `late response cannot restore cookies after session clear`() {
        val store = InMemoryAnimeVostCookieStore()
        val cookieJar = AnimeVostCookieJar(store)

        cookieJar.loadForRequest(url)
        cookieJar.clear()
        cookieJar.saveFromResponse(url, listOf(authCookie("old_session")))

        assertNull(store.get("dle_hash"))
    }

    @Test
    fun `new request can save cookies after session clear`() {
        val store = InMemoryAnimeVostCookieStore()
        val cookieJar = AnimeVostCookieJar(store)

        cookieJar.clear()
        cookieJar.loadForRequest(url)
        cookieJar.saveFromResponse(url, listOf(authCookie("new_session")))

        assertEquals("new_session", store.get("dle_hash"))
    }

    @Test
    fun `store preserves cookies with same name on different paths`() {
        val store = InMemoryAnimeVostCookieStore()
        val root = "https://example.test/".toHttpUrl()
        val account = "https://example.test/account/page".toHttpUrl()
        store.save(
            root,
            listOf(
                authCookie("root"),
                Cookie.Builder()
                    .name("dle_hash")
                    .value("account")
                    .hostOnlyDomain(root.host)
                    .path("/account")
                    .build(),
            ),
        )

        assertEquals(listOf("root"), store.load(root).map { cookie -> cookie.value })
        assertTrue(store.load(account).map { cookie -> cookie.value }.containsAll(listOf("root", "account")))
    }

    private fun authCookie(value: String): Cookie =
        Cookie.Builder()
            .name("dle_hash")
            .value(value)
            .hostOnlyDomain(url.host)
            .path("/")
            .build()
}
