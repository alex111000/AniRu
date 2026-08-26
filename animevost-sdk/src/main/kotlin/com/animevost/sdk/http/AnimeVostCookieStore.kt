package com.animevost.sdk.http

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.atomic.AtomicLong

interface AnimeVostCookieStore {
    fun save(url: HttpUrl, cookies: List<Cookie>)

    fun load(url: HttpUrl): List<Cookie>

    fun get(name: String): String?

    fun clear()
}

class InMemoryAnimeVostCookieStore : AnimeVostCookieStore {
    private val lock = Any()
    private val cookies = mutableListOf<Cookie>()

    override fun save(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            cookies.forEach { cookie ->
                this.cookies.removeAll {
                    it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path
                }
                if (cookie.expiresAt > now) {
                    this.cookies += cookie
                }
            }
            this.cookies.removeAll { it.expiresAt <= now }
        }
    }

    override fun load(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            cookies.removeAll { it.expiresAt <= now }
            return cookies.filter { it.matches(url) }
        }
    }

    override fun get(name: String): String? {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            return cookies.asSequence()
                .firstOrNull { it.name == name && it.expiresAt > now }
                ?.value
                ?.takeIf { it != "deleted" }
        }
    }

    override fun clear() {
        synchronized(lock) {
            cookies.clear()
        }
    }
}

internal class AnimeVostCookieJar(
    private val store: AnimeVostCookieStore,
) : CookieJar {
    private val sessionGeneration = AtomicLong()
    private val requestGeneration = ThreadLocal<Long>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val generationAtRequestStart = requestGeneration.get()
        requestGeneration.remove()
        if (generationAtRequestStart == null || generationAtRequestStart == sessionGeneration.get()) {
            store.save(url, cookies)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        requestGeneration.set(sessionGeneration.get())
        return store.load(url)
    }

    fun clear() {
        sessionGeneration.incrementAndGet()
        store.clear()
    }
}
