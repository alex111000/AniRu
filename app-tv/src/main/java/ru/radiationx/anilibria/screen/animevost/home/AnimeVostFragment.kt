package ru.radiationx.anilibria.screen.animevost.home

import android.os.Bundle
import android.view.View
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.GradientBackgroundManager
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.extension.applyCard
import ru.radiationx.anilibria.extension.createCardsRowBy
import ru.radiationx.anilibria.ui.presenter.cust.CustomListRowPresenter
import ru.radiationx.anilibria.ui.presenter.cust.CustomListRowViewHolder
import ru.radiationx.quill.inject
import ru.radiationx.shared_app.di.quillParentViewModel

class AnimeVostFragment : RowsSupportFragment() {

    companion object {
        private const val LATEST_ROW_ID = 1L
        private const val POPULAR_ROW_ID = 2L
        private const val RATING_ROW_ID = 3L
        private const val DISCUSSED_ROW_ID = 4L
        private const val CATALOG_ROW_ID = 5L
        private const val SCHEDULE_ROW_ID = 6L
    }

    private val rowsPresenter by lazy { CustomListRowPresenter() }
    private val rowsAdapter by lazy { ArrayObjectAdapter(rowsPresenter) }
    private val backgroundManager by inject<GradientBackgroundManager>()

    private val latestViewModel by quillParentViewModel<AnimeVostLatestViewModel>()
    private val popularViewModel by quillParentViewModel<AnimeVostPopularViewModel>()
    private val ratingViewModel by quillParentViewModel<AnimeVostRatingViewModel>()
    private val discussedViewModel by quillParentViewModel<AnimeVostDiscussedViewModel>()
    private val navigationViewModel by quillParentViewModel<AnimeVostNavigationViewModel>()
    private val scheduleViewModel by quillParentViewModel<AnimeVostScheduleViewModel>()

    private fun getViewModel(rowId: Long): BaseCardsViewModel? = when (rowId) {
        LATEST_ROW_ID -> latestViewModel
        POPULAR_ROW_ID -> popularViewModel
        RATING_ROW_ID -> ratingViewModel
        DISCUSSED_ROW_ID -> discussedViewModel
        CATALOG_ROW_ID -> navigationViewModel
        SCHEDULE_ROW_ID -> scheduleViewModel
        else -> null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listOf(latestViewModel, popularViewModel, ratingViewModel, discussedViewModel, navigationViewModel, scheduleViewModel).forEach {
            viewLifecycleOwner.lifecycle.addObserver(it)
        }

        adapter = rowsAdapter
        onItemViewSelectedListener = ItemViewSelectedListener()

        setOnItemViewClickedListener { _, item, _, row ->
            val viewModel = getViewModel((row as ListRow).id)
            when (item) {
                is LibriaCard -> viewModel?.onLibriaCardClick(item)
                is LinkCard -> viewModel?.onLinkCardClick()
                is LoadingCard -> viewModel?.onLoadingCardClick()
            }
        }

        if (rowsAdapter.size() == 0) {
            listOf(LATEST_ROW_ID, POPULAR_ROW_ID, RATING_ROW_ID, DISCUSSED_ROW_ID, CATALOG_ROW_ID, SCHEDULE_ROW_ID).forEach { rowId ->
                rowsAdapter.add(createCardsRowBy(rowId, rowsAdapter, requireNotNull(getViewModel(rowId))))
            }
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
