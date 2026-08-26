package ru.radiationx.anilibria.screen.animevost.home

import com.animevost.sdk.model.AnimePreview
import com.animevost.sdk.model.CatalogLink
import com.animevost.sdk.model.NavigationData
import ru.radiationx.anilibria.common.LibriaCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AnimeVostExpandedCatalogTest {

    @Test
    fun navigationExpandsEveryCategoryAndRemovesDuplicatePaths() {
        val years = (2000..2026).reversed().map { year -> link(year.toString(), "/god/$year/") }
        val navigation = NavigationData(
            sections = listOf(link("Онгоинги", "/ongoing/")),
            genres = listOf(
                link("Боевые искусства", "/zhanr/boevye-iskusstva/"),
                link("Повтор", "/ongoing/"),
            ),
            types = listOf(link("ТВ", "/tip/tv/")),
            years = years,
        )

        val definitions = navigation.toCategoryDefinitions()

        assertEquals("Онгоинги", definitions.first().title)
        assertEquals("Боевые искусства", definitions[1].title)
        assertEquals("ТВ", definitions[2].title)
        assertEquals("2006", definitions.first { it.title == "2006" }.title)
        assertEquals(30, definitions.size)
        assertEquals(definitions.size, definitions.map { it.path }.toSet().size)
    }

    @Test
    fun previewBecomesDirectAnimeCardWithRealPoster() {
        val preview = AnimePreview(
            id = 42,
            title = "Расхититель гробниц",
            originalTitle = "Dogulwang",
            episodeInfo = "9 серия",
            url = "https://animevost.org/tip/tv/42-title.html",
            posterUrl = "https://animevost.org/uploads/posts/poster.jpg",
            publishedDate = null,
            viewCount = null,
            commentCount = null,
            rating = 4.0,
            voteCount = null,
            categories = emptyList(),
        )

        val card = preview.toAnimeVostCard()

        assertEquals(preview.posterUrl, card.image)
        assertEquals("Dogulwang • 9 серия • ★ 4.0", card.description)
        val type = assertIs<LibriaCard.Type.AnimeVost>(card.type)
        assertEquals(preview.url, type.animeUrl)
    }

    @Test
    fun orphanedLoadingRowCanRestartInsteadOfSpinningForever() {
        val row = AnimeVostCategoryRowState(
            id = AnimeVostExpandedCatalogViewModel.CATEGORY_ROW_ID_BASE + 1,
            title = "Онгоинги",
            path = "ongoing/",
            cards = emptyList(),
            loadState = AnimeVostCategoryLoadState.LOADING,
        )

        assertFalse(row.shouldLoadOnSelection(activeRowIds = setOf(row.id)))
        assertTrue(row.shouldLoadOnSelection(activeRowIds = emptySet()))
        assertTrue(row.shouldLoadOnSelection(activeRowIds = setOf(row.id + 1)))
    }

    @Test
    fun movingFocusCanStartAnotherRowWithoutRestartingTheActiveRow() {
        val activeRow = categoryRow(
            id = AnimeVostExpandedCatalogViewModel.CATEGORY_ROW_ID_BASE + 1,
            loadState = AnimeVostCategoryLoadState.LOADING,
        )
        val nextRow = categoryRow(
            id = activeRow.id + 1,
            loadState = AnimeVostCategoryLoadState.NOT_LOADED,
        )
        val activeRowIds = setOf(activeRow.id)

        assertFalse(activeRow.shouldLoadOnSelection(activeRowIds))
        assertTrue(nextRow.shouldLoadOnSelection(activeRowIds))
    }

    private fun categoryRow(
        id: Long,
        loadState: AnimeVostCategoryLoadState,
    ) = AnimeVostCategoryRowState(
        id = id,
        title = "Категория",
        path = "category/",
        cards = emptyList(),
        loadState = loadState,
    )

    private fun link(title: String, path: String) = CatalogLink(
        title = title,
        url = "https://animevost.org$path",
        path = path,
    )
}
