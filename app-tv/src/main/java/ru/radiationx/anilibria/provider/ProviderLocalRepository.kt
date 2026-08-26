package ru.radiationx.anilibria.provider

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/** Local-only favorites and playback progress for generic providers. */
class ProviderLocalRepository @Inject constructor(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class Favorite(
        val provider: ProviderId,
        val animeId: String,
        val title: String,
        val posterUrl: String,
        val extra: String,
        val savedAt: Long,
    )

    data class History(
        val provider: ProviderId,
        val animeId: String,
        val animeTitle: String,
        val posterUrl: String,
        val episodeId: String,
        val episodeNumber: Int?,
        val episodeTitle: String,
        val sourceId: String?,
        val positionMs: Long,
        val durationMs: Long,
        val watchedAt: Long,
    ) {
        val isCompleted: Boolean
            get() = durationMs > 0 && positionMs >= durationMs - COMPLETION_GUARD_MS
        val progressPercent: Int
            get() = if (durationMs <= 0L) 0 else ((positionMs * 100L) / durationMs).toInt().coerceIn(0, 100)
    }

    @Synchronized
    fun isFavorite(provider: ProviderId, animeId: String): Boolean =
        getFavorites().any { it.provider == provider && it.animeId == animeId }

    @Synchronized
    fun toggleFavorite(item: Favorite): Boolean {
        val items = getFavorites().toMutableList()
        val index = items.indexOfFirst { it.provider == item.provider && it.animeId == item.animeId }
        val nowFavorite = index < 0
        if (index >= 0) items.removeAt(index) else items.add(0, item)
        saveFavorites(items.sortedByDescending { it.savedAt }.take(MAX_FAVORITES))
        return nowFavorite
    }

    @Synchronized
    fun getFavorites(): List<Favorite> = readArray(KEY_FAVORITES).mapNotNull { obj ->
        val provider = ProviderId.fromWireId(obj.optString("provider")) ?: return@mapNotNull null
        val animeId = obj.optString("animeId")
        if (animeId.isBlank()) return@mapNotNull null
        Favorite(
            provider = provider,
            animeId = animeId,
            title = obj.optString("title"),
            posterUrl = obj.optString("posterUrl"),
            extra = obj.optString("extra"),
            savedAt = obj.optLong("savedAt", 0L),
        )
    }.sortedByDescending { it.savedAt }

    @Synchronized
    fun updateProgress(item: History) {
        if (item.animeId.isBlank() || item.episodeId.isBlank()) return
        val items = getHistory().toMutableList()
        items.removeAll {
            it.provider == item.provider && it.animeId == item.animeId && it.episodeId == item.episodeId
        }
        items.add(0, item.copy(
            positionMs = item.positionMs.coerceAtLeast(0L),
            durationMs = item.durationMs.coerceAtLeast(0L),
            watchedAt = item.watchedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
        ))
        saveHistory(items.sortedByDescending { it.watchedAt }.take(MAX_HISTORY))
    }

    @Synchronized
    fun getProgress(provider: ProviderId, animeId: String, episodeId: String): History? =
        getHistory().firstOrNull {
            it.provider == provider && it.animeId == animeId && it.episodeId == episodeId
        }

    @Synchronized
    fun getHistory(): List<History> = readArray(KEY_HISTORY).mapNotNull { obj ->
        val provider = ProviderId.fromWireId(obj.optString("provider")) ?: return@mapNotNull null
        val animeId = obj.optString("animeId")
        val episodeId = obj.optString("episodeId")
        if (animeId.isBlank() || episodeId.isBlank()) return@mapNotNull null
        History(
            provider = provider,
            animeId = animeId,
            animeTitle = obj.optString("animeTitle"),
            posterUrl = obj.optString("posterUrl"),
            episodeId = episodeId,
            episodeNumber = obj.optInt("episodeNumber", Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE },
            episodeTitle = obj.optString("episodeTitle"),
            sourceId = obj.optString("sourceId").takeIf { it.isNotBlank() },
            positionMs = obj.optLong("positionMs", 0L),
            durationMs = obj.optLong("durationMs", 0L),
            watchedAt = obj.optLong("watchedAt", 0L),
        )
    }.sortedByDescending { it.watchedAt }

    private fun saveFavorites(items: List<Favorite>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("provider", item.provider.wireId)
                put("animeId", item.animeId)
                put("title", item.title)
                put("posterUrl", item.posterUrl)
                put("extra", item.extra)
                put("savedAt", item.savedAt)
            })
        }
        prefs.edit().putString(KEY_FAVORITES, array.toString()).apply()
    }

    private fun saveHistory(items: List<History>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("provider", item.provider.wireId)
                put("animeId", item.animeId)
                put("animeTitle", item.animeTitle)
                put("posterUrl", item.posterUrl)
                put("episodeId", item.episodeId)
                item.episodeNumber?.let { put("episodeNumber", it) }
                put("episodeTitle", item.episodeTitle)
                item.sourceId?.let { put("sourceId", it) }
                put("positionMs", item.positionMs)
                put("durationMs", item.durationMs)
                put("watchedAt", item.watchedAt)
            })
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    private fun readArray(key: String): List<JSONObject> = runCatching {
        val raw = prefs.getString(key, null).orEmpty()
        if (raw.isBlank()) return@runCatching emptyList()
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) array.optJSONObject(index)?.let(::add)
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val PREFS = "aniru_provider_local"
        const val KEY_FAVORITES = "favorites"
        const val KEY_HISTORY = "history"
        const val MAX_FAVORITES = 500
        const val MAX_HISTORY = 1000
        const val COMPLETION_GUARD_MS = 30_000L
    }
}
