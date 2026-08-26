package ru.radiationx.anilibria.screen.anilibria

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
import ru.radiationx.anilibria.screen.main.MainFeedViewModel
import ru.radiationx.anilibria.screen.main.MainScheduleViewModel
import ru.radiationx.anilibria.screen.main.MainYouTubeViewModel
import ru.radiationx.anilibria.ui.presenter.cust.CustomListRowPresenter
import ru.radiationx.anilibria.ui.presenter.cust.CustomListRowViewHolder
import ru.radiationx.quill.inject
import ru.radiationx.shared_app.di.quillParentViewModel

class AniLibriaFragment : RowsSupportFragment() {

    companion object {
        private const val RELEASES_ROW_ID = 1L
        private const val SCHEDULE_ROW_ID = 2L
        private const val YOUTUBE_ROW_ID = 3L
    }

    private val rowsPresenter by lazy { CustomListRowPresenter() }
    private val rowsAdapter by lazy { ArrayObjectAdapter(rowsPresenter) }
    private val backgroundManager by inject<GradientBackgroundManager>()

    private val feedViewModel by quillParentViewModel<MainFeedViewModel>()
    private val scheduleViewModel by quillParentViewModel<MainScheduleViewModel>()
    private val youtubeViewModel by quillParentViewModel<MainYouTubeViewModel>()

    private fun getViewModel(rowId: Long): BaseCardsViewModel? = when (rowId) {
        RELEASES_ROW_ID -> feedViewModel
        SCHEDULE_ROW_ID -> scheduleViewModel
        YOUTUBE_ROW_ID -> youtubeViewModel
        else -> null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listOf(feedViewModel, scheduleViewModel, youtubeViewModel).forEach {
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
            listOf(RELEASES_ROW_ID, SCHEDULE_ROW_ID, YOUTUBE_ROW_ID).forEach { rowId ->
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
