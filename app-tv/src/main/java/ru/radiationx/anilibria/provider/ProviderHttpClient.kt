package ru.radiationx.anilibria.provider

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * One conservative HTTP stack for external providers.
 * Network calls are coroutine-cancellable so provider timeouts actually stop work.
 */
class ProviderHttpClient private constructor(context: Context?, testClient: OkHttpClient?) {
    @Inject constructor(context: Context) : this(context, null)
    internal constructor(client: OkHttpClient) : this(null, client)
    private val client = testClient ?: OkHttpClient.Builder()
        .cache(Cache(File(requireNotNull(context).cacheDir, "aniru_provider_http"), CACHE_BYTES))
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(DefaultHeadersInterceptor())
        .addInterceptor(TransientRetryInterceptor())
        .build()

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        cacheControl: String? = null,
    ): String = execute(
        Request.Builder().url(url).get().apply {
            headers.forEach { (key, value) -> header(key, value) }
            cacheControl?.let { header("Cache-Control", it) }
        }.build()
    )

    suspend fun postForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val body = FormBody.Builder().apply { form.forEach { (key, value) -> add(key, value) } }.build()
        return execute(
            Request.Builder().url(url).post(body).apply {
                headers.forEach { (key, value) -> header(key, value) }
            }.build()
        )
    }

    suspend fun head(url: String, headers: Map<String, String> = emptyMap()): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).head().apply {
                headers.forEach { (key, value) -> header(key, value) }
            }.build()).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private suspend fun execute(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        if (!it.isSuccessful) throw IOException("HTTP ${it.code} for ${request.url}")
                        val text = it.body.string()
                        if (continuation.isActive) continuation.resume(text)
                    } catch (error: Throwable) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            }
        })
    }

    private class DefaultHeadersInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            val request = original.newBuilder()
                .apply {
                    if (original.header("User-Agent") == null) header("User-Agent", USER_AGENT)
                    if (original.header("Accept-Language") == null) header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.7")
                    if (original.header("Accept") == null) header("Accept", "*/*")
                }
                .build()
            return chain.proceed(request)
        }
    }

    private class TransientRetryInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            var response = chain.proceed(request)
            var retry = 0
            while (response.code in RETRYABLE_CODES && retry < MAX_TRANSIENT_RETRIES) {
                response.close()
                retry++
                try {
                    Thread.sleep(RETRY_DELAY_MS * retry)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
                response = chain.proceed(request)
            }
            return response
        }
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 12; Android TV) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36 AniRu/1.2.4"

        private const val CACHE_BYTES = 32L * 1024L * 1024L
        private const val CONNECT_TIMEOUT_SECONDS = 8L
        private const val READ_TIMEOUT_SECONDS = 15L
        private const val WRITE_TIMEOUT_SECONDS = 12L
        private const val CALL_TIMEOUT_SECONDS = 22L
        private const val MAX_TRANSIENT_RETRIES = 2
        private const val RETRY_DELAY_MS = 250L
        private val RETRYABLE_CODES = setOf(500, 502, 503, 504)
    }
}
