package ru.radiationx.anilibria.screen.animevost.home

import android.os.Bundle
import android.view.View
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.CardDiffCallback
import ru.radiationx.anilibria.common.GradientBackgroundManager
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.common.RowDiffCallback
import ru.radiationx.anilibria.extension.applyCard
import ru.radiationx.anilibria.extension.createCardsRowBy
import ru.radiationx.anilibria.ui.presenter.CardPresenterSelector
import ru.radiationx.anilibria.ui.presenter.cust.CustomListRowPresenter
import ru.radiationx.anilibria.ui.presenter.cust.CustomListRowViewHolder
import ru.radiationx.quill.inject
import ru.radiationx.shared_app.di.quillParentViewModel
import ru.radiationx.shared.ktx.android.subscribeTo

class AnimeVostFragment : RowsSupportFragment() {

    companion object {
        private const val LATEST_ROW_ID = 1L
        private const val POPULAR_ROW_ID = 2L
        private const val RATING_ROW_ID = 3L
        private const val DISCUSSED_ROW_ID = 4L
        private const val SCHEDULE_ROW_ID = 6L
    }

    private val rowsPresenter by lazy { CustomListRowPresenter() }
    private val rowsAdapter by lazy { ArrayObjectAdapter(rowsPresenter) }
    private val backgroundManager by inject<GradientBackgroundManager>()

    private val latestViewModel by quillParentViewModel<AnimeVostLatestViewModel>()
    private val popularViewModel by quillParentViewModel<AnimeVostPopularViewModel>()
    private val ratingViewModel by quillParentViewModel<AnimeVostRatingViewModel>()
    private val discussedViewModel by quillParentViewModel<AnimeVostDiscussedViewModel>()
    private val expandedCatalogViewModel by quillParentViewModel<AnimeVostExpandedCatalogViewModel>()
    private val scheduleViewModel by quillParentViewModel<AnimeVostScheduleViewModel>()

    private val categoryRows = mutableMapOf<Long, ListRow>()
    private val fixedRows = mutableMapOf<Long, ListRow>()

    private fun getViewModel(rowId: Long): BaseCardsViewModel? = when (rowId) {
        LATEST_ROW_ID -> latestViewModel
        POPULAR_ROW_ID -> popularViewModel
        RATING_ROW_ID -> ratingViewModel
        DISCUSSED_ROW_ID -> discussedViewModel
        SCHEDULE_ROW_ID -> scheduleViewModel
        else -> null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listOf(latestViewModel, popularViewModel, ratingViewModel, discussedViewModel, expandedCatalogViewModel, scheduleViewModel).forEach {
            viewLifecycleOwner.lifecycle.addObserver(it)
        }

        adapter = rowsAdapter
        onItemViewSelectedListener = ItemViewSelectedListener()

        setOnItemViewClickedListener { _, item, _, row ->
            val viewModel = getViewModel((row as ListRow).id)
            when (item) {
                is LibriaCard -> {
                    if (viewModel != null) viewModel.onLibriaCardClick(item)
                    else expandedCatalogViewModel.onLibriaCardClick(item)
                }
                is LinkCard -> {
                    if (viewModel != null) viewModel.onLinkCardClick()
                    else expandedCatalogViewModel.onLinkCardClick(row.id)
                }
                is LoadingCard -> {
                    if (viewModel != null) viewModel.onLoadingCardClick()
                    else expandedCatalogViewModel.onLoadingCardClick(row.id)
                }
            }
        }

        // Subscriptions belong to the view, even when adapters survive a return from details.
        listOf(LATEST_ROW_ID, POPULAR_ROW_ID, RATING_ROW_ID, DISCUSSED_ROW_ID, SCHEDULE_ROW_ID)
            .forEach { rowId ->
                fixedRows[rowId] = createCardsRowBy(
                    rowId, rowsAdapter, requireNotNull(getViewModel(rowId)), fixedRows[rowId],
                )
            }

        val renderedStates = mutableMapOf<Long, AnimeVostCategoryRowState>()
        subscribeTo(expandedCatalogViewModel.rowsData) { states ->
            val catalogRows = states.map { state ->
                val row = categoryRows.getOrPut(state.id) {
                    ListRow(
                        state.id,
                        HeaderItem(state.title),
                        ArrayObjectAdapter(CardPresenterSelector(null)),
                    )
                }
                if (row.headerItem.name != state.title) {
                    row.headerItem = HeaderItem(state.title)
                    val position = rowsAdapter.indexOf(row)
                    if (position >= 0) rowsAdapter.notifyArrayItemRangeChanged(position, 1)
                }
                if (renderedStates[state.id]?.cards != state.cards) {
                    (row.adapter as ArrayObjectAdapter).setItems(state.cards, CardDiffCallback)
                }
                renderedStates[state.id] = state
                row
            }
            val allRows = listOf(
                requireNotNull(fixedRows[LATEST_ROW_ID]),
                requireNotNull(fixedRows[POPULAR_ROW_ID]),
                requireNotNull(fixedRows[RATING_ROW_ID]),
                requireNotNull(fixedRows[DISCUSSED_ROW_ID]),
            ) + catalogRows + requireNotNull(fixedRows[SCHEDULE_ROW_ID])
            // A completed request changes one row, not the entire Leanback screen.
            if (rowsAdapter.size() != allRows.size || allRows.indices.any { rowsAdapter[it] !== allRows[it] }) {
                rowsAdapter.setItems(allRows, RowDiffCallback)
            }
            val ids = states.map { it.id }.toSet()
            categoryRows.keys.retainAll(ids)
            renderedStates.keys.retainAll(ids)
        }

    }

    override fun onResume() {
        super.onResume()
        mainFragmentAdapter.fragmentHost.notifyDataReady(mainFragmentAdapter)
    }

    private inner class ItemViewSelectedListener : OnItemViewSelectedListener {
        override fun onItemSelected(
            itemViewHolder: Presenter.ViewHolder?,
            item: Any?,
            rowViewHolder: RowPresenter.ViewHolder,
            row: Row,
        ) {
            expandedCatalogViewModel.onRowSelected(row.id)
            if (rowViewHolder is CustomListRowViewHolder) {
                backgroundManager.applyCard(item)
                when (item) {
                    is LibriaCard -> rowViewHolder.setDescription(item.title, item.description)
                    is LinkCard -> rowViewHolder.setDescription(item.title, "")
                    is LoadingCard -> rowViewHolder.setDescription(item.title, item.description)
                    else -> rowViewHolder.setDescription("", "")
                }
            }
        }
    }
}
