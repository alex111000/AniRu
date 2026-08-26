package ru.radiationx.anilibria.contentprovider.suggestions

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import ru.radiationx.anilibria.App
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.contentprovider.SystemSuggestionEntity
import ru.radiationx.anilibria.search.UnifiedSearchRepository
import ru.radiationx.quill.Quill

class SuggestionsContentProvider : ContentProvider() {

    companion object {
        const val INTENT_ACTION = "GLOBALSEARCH"
        const val PREFIX_ANILIBRIA = "al_"
        const val PREFIX_ANIMEVOST = "av_"
        const val PREFIX_PROVIDER = "pv_"

        private val queryProjection = SystemSuggestionEntity.projection + arrayOf(
            SearchManager.SUGGEST_COLUMN_INTENT_ACTION,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID,
        )
        private const val AUTHORITY = "com.aniru.tv.contentprovider.suggestions"
        private const val SEARCH_SUGGEST = 1
        private const val GLOBAL_SEARCH_TIMEOUT_MS = 3_500L

        fun encodeAnimeVostUrl(url: String): String = PREFIX_ANIMEVOST + Base64.encodeToString(
            url.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )

        fun encodeProvider(providerId: String, animeId: String): String = PREFIX_PROVIDER + Base64.encodeToString(
            "$providerId\n$animeId".toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )

        fun decodeProvider(value: String): Pair<String, String>? = runCatching {
            if (!value.startsWith(PREFIX_PROVIDER)) return@runCatching null
            val raw = String(
                Base64.decode(
                    value.removePrefix(PREFIX_PROVIDER),
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
                ),
                Charsets.UTF_8,
            )
            val split = raw.split('\n', limit = 2)
            val provider = split.getOrNull(0).orEmpty()
            val animeId = split.getOrNull(1).orEmpty()
            if (provider.isBlank() || animeId.isBlank()) null else provider to animeId
        }.getOrNull()

        fun decodeAnimeVostUrl(value: String): String? = runCatching {
            if (!value.startsWith(PREFIX_ANIMEVOST)) return@runCatching null
            String(
                Base64.decode(
                    value.removePrefix(PREFIX_ANIMEVOST),
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
                ),
                Charsets.UTF_8,
            )
        }.getOrNull()
    }

    private val uriMatcher by lazy { buildUriMatcher() }
    private val unifiedSearchRepository by lazy {
        Quill.getRootScope().get(UnifiedSearchRepository::class)
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        runBlocking { App.appCreateAction.filter { it }.first() }
        return if (uriMatcher.match(uri) == SEARCH_SUGGEST) {
            search(uri.lastPathSegment.orEmpty())
        } else {
            throw IllegalArgumentException("Unknown Uri: $uri")
        }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("insert is not implemented.")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("update is not implemented.")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("delete is not implemented.")

    private fun search(query: String): Cursor {
        val result = runBlocking {
            withTimeoutOrNull(GLOBAL_SEARCH_TIMEOUT_MS) { unifiedSearchRepository.search(query) }
        }
        val matrixCursor = MatrixCursor(queryProjection)
        if (result == null) return matrixCursor
        result.all.forEachIndexed { index, card ->
            val entity = card.toEntity(index)
            matrixCursor.addRow(
                entity.getRow() + INTENT_ACTION + card.intentDataId()
            )
        }
        return matrixCursor
    }

    private fun LibriaCard.toEntity(index: Int) = SystemSuggestionEntity(
        id = getId().takeIf { it >= 0 } ?: index,
        title = title,
        duration = -1,
        productionYear = -1,
        description = description,
        cardImage = image,
    )

    private fun LibriaCard.intentDataId(): String = when (val cardType = type) {
        is LibriaCard.Type.Release -> PREFIX_ANILIBRIA + cardType.releaseId.id
        is LibriaCard.Type.AnimeVost -> encodeAnimeVostUrl(cardType.animeUrl)
        is LibriaCard.Type.Provider -> encodeProvider(cardType.providerId, cardType.animeId)
        is LibriaCard.Type.ProviderEpisode -> encodeProvider(cardType.providerId, cardType.animeId)
        else -> ""
    }

    private fun buildUriMatcher(): UriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(AUTHORITY, "/search/${SearchManager.SUGGEST_URI_PATH_QUERY}", SEARCH_SUGGEST)
        addURI(AUTHORITY, "/search/${SearchManager.SUGGEST_URI_PATH_QUERY}/*", SEARCH_SUGGEST)
    }
}
