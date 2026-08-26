package ru.radiationx.anilibria.animevost

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class AnimeVostFavoriteItem(
    val animeUrl: String,
    val animeTitle: String,
    val posterUrl: String,
    val originalTitle: String,
    val savedAt: Long,
)

class AnimeVostFavoritesRepository @Inject constructor(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun isFavorite(animeUrl: String): Boolean =
        getFavorites().any { it.animeUrl == animeUrl }

    @Synchronized
    fun toggle(item: AnimeVostFavoriteItem): Boolean {
        val current = getFavorites().toMutableList()
        val existing = current.indexOfFirst { it.animeUrl == item.animeUrl }
        val isNowFavorite = if (existing >= 0) {
            current.removeAt(existing)
            false
        } else {
            current.add(0, item)
            true
        }
        save(current)
        return isNowFavorite
    }

    @Synchronized
    fun getFavorites(): List<AnimeVostFavoriteItem> {
        val raw = preferences.getString(KEY_FAVORITES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index) ?: continue
                    val animeUrl = json.optString("animeUrl")
                    val animeTitle = json.optString("animeTitle")
                    if (animeUrl.isBlank() || animeTitle.isBlank()) continue
                    add(
                        AnimeVostFavoriteItem(
                            animeUrl = animeUrl,
                            animeTitle = animeTitle,
                            posterUrl = json.optString("posterUrl"),
                            originalTitle = json.optString("originalTitle"),
                            savedAt = json.optLong("savedAt"),
                        )
                    )
                }
            }.sortedByDescending { it.savedAt }
        }.getOrDefault(emptyList())
    }

    private fun save(items: List<AnimeVostFavoriteItem>) {
        val array = JSONArray()
        items.distinctBy { it.animeUrl }.take(MAX_ITEMS).forEach { item ->
            array.put(
                JSONObject().apply {
                    put("animeUrl", item.animeUrl)
                    put("animeTitle", item.animeTitle)
                    put("posterUrl", item.posterUrl)
                    put("originalTitle", item.originalTitle)
                    put("savedAt", item.savedAt)
                }
            )
        }
        preferences.edit().putString(KEY_FAVORITES, array.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "aniru_animevost_favorites"
        const val KEY_FAVORITES = "favorites"
        const val MAX_ITEMS = 250
    }
}
