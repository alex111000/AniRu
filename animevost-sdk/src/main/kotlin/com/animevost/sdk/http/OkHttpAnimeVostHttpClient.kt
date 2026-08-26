package com.animevost.sdk.http

import com.animevost.sdk.error.AnimeVostHttpException
import com.animevost.sdk.error.AnimeVostNetworkException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.CookieJar
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OkHttpAnimeVostHttpClient(
    private val cookieStore: AnimeVostCookieStore = InMemoryAnimeVostCookieStore(),
    client: OkHttpClient? = null,
) : AnimeVostHttpClient {

    private val cookieJar = AnimeVostCookieJar(cookieStore)
    private val client = client
        ?.newBuilder()
        ?.cookieJar(cookieJar)
        ?.build()
        ?: defaultClient(cookieJar)

    override suspend fun get(url: String, headers: Map<String, String>): String {
        val request = Request.Builder()
            .url(url)
            .apply {
                headers.forEach { (name, value) -> header(name, value) }
            }
            .build()
        return execute(request)
    }

    override suspend fun post(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String>,
    ): String {
        val body = FormBody.Builder()
            .apply { form.forEach { (name, value) -> add(name, value) } }
            .build()
        val request = Request.Builder()
            .url(url)
            .post(body)
            .apply {
                headers.forEach { (name, value) -> header(name, value) }
            }
            .build()
        return execute(request)
    }

    override suspend fun postMultipart(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String>,
    ): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .apply { form.forEach { (name, value) -> addFormDataPart(name, value) } }
            .build()
        val request = Request.Builder()
            .url(url)
            .post(body)
            .apply {
                headers.forEach { (name, value) -> header(name, value) }
            }
            .build()
        return execute(request)
    }

    override fun getCookie(name: String): String? =
        cookieStore.get(name)

    override fun clearCookies() {
        cookieJar.clear()
    }

    private suspend fun execute(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, exception: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(AnimeVostNetworkException(exception))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val body = response.use {
                            if (!it.isSuccessful) {
                                throw AnimeVostHttpException(it.code, it.message)
                            }
                            it.body?.string().orEmpty()
                        }
                        if (continuation.isActive) continuation.resume(body)
                    } catch (exception: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(exception)
                    }
                }
            }
        )
    }

    private companion object {
        fun defaultClient(cookieJar: CookieJar): OkHttpClient =
            OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
    }
}
