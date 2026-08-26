package ru.radiationx.anilibria.favorites

import android.content.Context
import org.json.JSONArray
import ru.radiationx.data.entity.domain.types.ReleaseId
import javax.inject.Inject

class LocalFavoritesRepository @Inject constructor(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun isFavorite(releaseId: ReleaseId): Boolean = getIds().any { it == releaseId }

    @Synchronized
    fun toggle(releaseId: ReleaseId): Boolean {
        val ids = getIds().toMutableList()
        val isNowFavorite = if (ids.remove(releaseId)) {
            false
        } else {
            ids.add(0, releaseId)
            true
        }
        saveIds(ids)
        return isNowFavorite
    }

    @Synchronized
    fun getIds(): List<ReleaseId> {
        val raw = preferences.getString(KEY_IDS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val id = array.optInt(index, -1)
                    if (id >= 0) add(ReleaseId(id))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveIds(ids: List<ReleaseId>) {
        val array = JSONArray()
        ids.distinct().take(MAX_ITEMS).forEach { array.put(it.id) }
        preferences.edit().putString(KEY_IDS, array.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "aniru_local_favorites"
        const val KEY_IDS = "anilibria_release_ids"
        const val MAX_ITEMS = 250
    }
}
