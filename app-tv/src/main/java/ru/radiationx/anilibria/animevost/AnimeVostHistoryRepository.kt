package ru.radiationx.anilibria.animevost

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class AnimeVostHistoryItem(
    val animeUrl: String,
    val animeTitle: String,
    val posterUrl: String,
    val videoId: String,
    val episodeName: String,
    val episodeNumber: Int?,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val watchedAt: Long,
) {
    val progressPercent: Int
        get() = if (durationMs > 0L) {
            ((positionMs * 100L) / durationMs).toInt().coerceIn(0, 100)
        } else {
            0
        }

    val isCompleted: Boolean
        get() = durationMs > 0L && positionMs >= (durationMs - COMPLETED_GUARD_MS).coerceAtLeast(0L)

    private companion object {
        const val COMPLETED_GUARD_MS = 30_000L
    }
}

/**
 * Local AnimeVost watch history.
 *
 * We keep two small stores:
 *  - one latest entry per title for the "Continue watching" UI;
 *  - per-episode progress so long shows do not lose progress when the user changes episodes.
 */
class AnimeVostHistoryRepository @Inject constructor(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Keep one latest continue-watching entry per title. */
    @Synchronized
    fun save(item: AnimeVostHistoryItem) {
        val current = getHistory().toMutableList()
        current.removeAll { it.animeUrl == item.animeUrl }
        current.add(0, item)
        saveLatest(current)
    }

    @Synchronized
    fun updateProgress(
        animeUrl: String,
        animeTitle: String,
        posterUrl: String,
        videoId: String,
        episodeName: String,
        episodeNumber: Int?,
        positionMs: Long,
        durationMs: Long,
    ) {
        val previous = getProgress(animeUrl, videoId)
        val normalizedDuration = durationMs.takeIf { it > 0L } ?: previous?.durationMs ?: 0L
        val item = AnimeVostHistoryItem(
            animeUrl = animeUrl,
            animeTitle = animeTitle,
            posterUrl = posterUrl,
            videoId = videoId,
            episodeName = episodeName,
            episodeNumber = episodeNumber,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = normalizedDuration,
            watchedAt = System.currentTimeMillis(),
        )
        save(item)
        saveEpisodeProgress(item)
    }

    @Synchronized
    fun getProgress(animeUrl: String, videoId: String): AnimeVostHistoryItem? =
        getEpisodeProgress(animeUrl).firstOrNull { it.videoId == videoId }
            ?: getHistory().firstOrNull { it.animeUrl == animeUrl && it.videoId == videoId }

    @Synchronized
    fun getEpisodeProgress(animeUrl: String): List<AnimeVostHistoryItem> =
        readItems(KEY_EPISODE_PROGRESS)
            .filter { it.animeUrl == animeUrl }
            .sortedWith(
                compareBy<AnimeVostHistoryItem> { it.episodeNumber ?: Int.MAX_VALUE }
                    .thenByDescending { it.watchedAt }
            )

    @Synchronized
    fun remove(animeUrl: String) {
        saveLatest(getHistory().filterNot { it.animeUrl == animeUrl })
        saveEpisodeProgressList(readItems(KEY_EPISODE_PROGRESS).filterNot { it.animeUrl == animeUrl })
    }

    @Synchronized
    fun clear() {
        preferences.edit()
            .remove(KEY_HISTORY)
            .remove(KEY_EPISODE_PROGRESS)
            .apply()
    }

    @Synchronized
    fun getHistory(): List<AnimeVostHistoryItem> =
        readItems(KEY_HISTORY)
            .distinctBy { it.animeUrl }
            .sortedByDescending { it.watchedAt }

    private fun saveEpisodeProgress(item: AnimeVostHistoryItem) {
        val current = readItems(KEY_EPISODE_PROGRESS).toMutableList()
        current.removeAll { it.animeUrl == item.animeUrl && it.videoId == item.videoId }
        current.add(0, item)
        saveEpisodeProgressList(current)
    }

    private fun saveLatest(items: List<AnimeVostHistoryItem>) {
        preferences.edit()
            .putString(KEY_HISTORY, encode(items.distinctBy { it.animeUrl }.take(MAX_TITLES)))
            .apply()
    }

    private fun saveEpisodeProgressList(items: List<AnimeVostHistoryItem>) {
        preferences.edit()
            .putString(
                KEY_EPISODE_PROGRESS,
                encode(items.distinctBy { "${it.animeUrl}|${it.videoId}" }.take(MAX_EPISODE_PROGRESS)),
            )
            .apply()
    }

    private fun readItems(key: String): List<AnimeVostHistoryItem> {
        val raw = preferences.getString(key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val animeUrl = item.optString("animeUrl")
                    val animeTitle = item.optString("animeTitle")
                    val videoId = item.optString("videoId")
                    val episodeName = item.optString("episodeName")
                    if (animeUrl.isBlank() || animeTitle.isBlank() || videoId.isBlank()) continue
                    add(
                        AnimeVostHistoryItem(
                            animeUrl = animeUrl,
                            animeTitle = animeTitle,
                            posterUrl = item.optString("posterUrl"),
                            videoId = videoId,
                            episodeName = episodeName,
                            episodeNumber = if (item.has("episodeNumber")) item.optInt("episodeNumber") else null,
                            positionMs = item.optLong("positionMs", 0L),
                            durationMs = item.optLong("durationMs", 0L),
                            watchedAt = item.optLong("watchedAt"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encode(items: List<AnimeVostHistoryItem>): String {
        val array = JSONArray()
        items.forEach { historyItem ->
            array.put(
                JSONObject().apply {
                    put("animeUrl", historyItem.animeUrl)
                    put("animeTitle", historyItem.animeTitle)
                    put("posterUrl", historyItem.posterUrl)
                    put("videoId", historyItem.videoId)
                    put("episodeName", historyItem.episodeName)
                    historyItem.episodeNumber?.let { put("episodeNumber", it) }
                    put("positionMs", historyItem.positionMs)
                    put("durationMs", historyItem.durationMs)
                    put("watchedAt", historyItem.watchedAt)
                }
            )
        }
        return array.toString()
    }

    private companion object {
        const val PREFS_NAME = "aniru_animevost_history"
        const val KEY_HISTORY = "history"
        const val KEY_EPISODE_PROGRESS = "episode_progress"
        const val MAX_TITLES = 500
        const val MAX_EPISODE_PROGRESS = 5_000
    }
}
