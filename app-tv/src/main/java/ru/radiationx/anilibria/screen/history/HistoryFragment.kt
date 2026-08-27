package ru.radiationx.anilibria.screen.history

import android.os.Bundle
import android.view.View
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ListRow
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

class HistoryFragment : RowsSupportFragment() {

    companion object {
        fun newHomeInstance() = HistoryFragment().apply { arguments = Bundle().apply { putBoolean("home", true) } }
        private const val CONTINUE_ROW_ID = 1L
        private const val ANILIBRIA_HISTORY_ROW_ID = 2L
        private const val ANIMEVOST_HISTORY_ROW_ID = 3L
        private const val FAVORITES_ROW_ID = 4L
        private const val PROVIDER_HISTORY_ROW_ID = 5L
    }

    private val rowsPresenter by lazy { CustomListRowPresenter() }
    private val rowsAdapter by lazy { ArrayObjectAdapter(rowsPresenter) }
    private val backgroundManager by inject<GradientBackgroundManager>()

    private val continueViewModel by quillParentViewModel<UnifiedContinueViewModel>()
    private val aniLibriaHistoryViewModel by quillParentViewModel<AniLibriaHistoryViewModel>()
    private val animeVostHistoryViewModel by quillParentViewModel<AnimeVostHistoryViewModel>()
    private val localFavoritesViewModel by quillParentViewModel<LocalFavoritesViewModel>()
    private val providerHistoryViewModel by quillParentViewModel<ProviderHistoryViewModel>()

    private fun getViewModel(rowId: Long): BaseCardsViewModel? = when (rowId) {
        CONTINUE_ROW_ID -> continueViewModel
        ANILIBRIA_HISTORY_ROW_ID -> aniLibriaHistoryViewModel
        ANIMEVOST_HISTORY_ROW_ID -> animeVostHistoryViewModel
        FAVORITES_ROW_ID -> localFavoritesViewModel
        PROVIDER_HISTORY_ROW_ID -> providerHistoryViewModel
        else -> null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val home = arguments?.getBoolean("home") == true
        (if (home) listOf(continueViewModel, localFavoritesViewModel) else listOf(continueViewModel, localFavoritesViewModel, aniLibriaHistoryViewModel, animeVostHistoryViewModel, providerHistoryViewModel)).forEach {
            viewLifecycleOwner.lifecycle.addObserver(it)
        }

        adapter = rowsAdapter

        setOnItemViewClickedListener { _, item, _, row ->
            val viewModel = getViewModel((row as ListRow).id)
            when (item) {
                is LibriaCard -> viewModel?.onLibriaCardClick(item)
                is LinkCard -> viewModel?.onLinkCardClick()
                is LoadingCard -> viewModel?.onLoadingCardClick()
            }
        }

        setOnItemViewSelectedListener { _, item, rowViewHolder, _ ->
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

        if (rowsAdapter.size() == 0) {
            (if (home) listOf(CONTINUE_ROW_ID, FAVORITES_ROW_ID) else listOf(CONTINUE_ROW_ID, FAVORITES_ROW_ID, ANILIBRIA_HISTORY_ROW_ID, ANIMEVOST_HISTORY_ROW_ID, PROVIDER_HISTORY_ROW_ID)).forEach { rowId ->
                rowsAdapter.add(createCardsRowBy(rowId, rowsAdapter, requireNotNull(getViewModel(rowId))))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mainFragmentAdapter.fragmentHost.notifyDataReady(mainFragmentAdapter)
    }
}
