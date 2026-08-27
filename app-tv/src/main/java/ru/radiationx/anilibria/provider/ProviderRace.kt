package ru.radiationx.anilibria.provider

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Return the first usable value without waiting for unavailable providers. */
internal suspend fun <T : Any> firstAvailable(
    tasks: List<suspend () -> T?>,
    budgetMs: Long,
    concurrency: Int = 3,
): T? = supervisorScope {
    val results = Channel<T?>(Channel.UNLIMITED)
    val limiter = Semaphore(concurrency)
    val jobs = tasks.map { task -> launch {
        val result = try { limiter.withPermit { task() } }
        catch (error: Exception) { currentCoroutineContext().ensureActive(); null }
        results.send(result)
    } }
    try {
        withTimeoutOrNull(budgetMs) {
            repeat(tasks.size) { results.receive()?.let { return@withTimeoutOrNull it } }
            null
        }
    } finally {
        jobs.forEach { it.cancel() }
        results.cancel()
    }
}
