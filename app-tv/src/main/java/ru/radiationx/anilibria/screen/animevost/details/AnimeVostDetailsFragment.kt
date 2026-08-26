package ru.radiationx.anilibria.screen.animevost.details

import android.os.Bundle
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import ru.radiationx.anilibria.common.CardDiffCallback
import ru.radiationx.anilibria.common.DetailsState
import ru.radiationx.anilibria.common.GradientBackgroundManager
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaDetailsRow
import ru.radiationx.anilibria.ui.presenter.CardPresenterSelector
import ru.radiationx.anilibria.ui.presenter.cust.CustomListRowPresenter
import ru.radiationx.anilibria.ui.presenter.cust.CustomListRowViewHolder
import ru.radiationx.quill.inject
import ru.radiationx.quill.viewModel
import ru.radiationx.shared.ktx.android.subscribeTo

class AnimeVostDetailsFragment : RowsSupportFragment() {

    companion object {
        private const val ARG_ANIME_URL = "animevost_url"

        fun newInstance(animeUrl: String) = AnimeVostDetailsFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_ANIME_URL, animeUrl)
            }
        }
    }

    private val animeUrl: String by lazy {
        requireArguments().getString(ARG_ANIME_URL)
            ?: error("AnimeVost URL is required")
    }

    private val viewModel by viewModel<AnimeVostDetailsViewModel> {
        AnimeVostDetailExtra(animeUrl)
    }

    private val backgroundManager by inject<GradientBackgroundManager>()

    private val detailsRow = LibriaDetailsRow(
        id = 1L,
        state = DetailsState(loadingProgress = true),
    )

    private val episodesAdapter = ArrayObjectAdapter(CardPresenterSelector(null))
    private val episodesRow = ListRow(
        2L,
        HeaderItem(2L, "Серии — быстрый доступ"),
        episodesAdapter,
    )

    private val relatedAdapter = ArrayObjectAdapter(CardPresenterSelector(null))
    private val relatedRow = ListRow(
        3L,
        HeaderItem(3L, "Связанные"),
        relatedAdapter,
    )

    private val rowsPresenter by lazy {
        ClassPresenterSelector().apply {
            addClassPresenter(ListRow::class.java, CustomListRowPresenter())
            addClassPresenter(
                LibriaDetailsRow::class.java,
                AnimeVostDetailsPresenter(
                    playClickListener = viewModel::onPlayClick,
                    favoriteClickListener = viewModel::onFavoriteClick,
                ),
            )
        }
    }

    private val rowsAdapter by lazy { ArrayObjectAdapter(rowsPresenter) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycle.addObserver(viewModel)
        adapter = rowsAdapter
        rowsAdapter.add(detailsRow)
        rowsAdapter.add(episodesRow)
        rowsAdapter.add(relatedRow)

        setOnItemViewClickedListener { _, item, _, _ ->
            if (item is LibriaCard) {
                viewModel.onCardClick(item)
            }
        }

        setOnItemViewSelectedListener { _, item, rowViewHolder, row ->
            if (item is LibriaCard) {
                backgroundManager.applyImage(item.image)
                if (rowViewHolder is CustomListRowViewHolder) {
                    rowViewHolder.setDescription(item.title, item.description)
                }
            } else if (row is LibriaDetailsRow) {
                row.details?.image?.let(::applyImage)
            }
        }

        subscribeTo(viewModel.loadingData) { loading ->
            detailsRow.state = DetailsState(loadingProgress = loading)
            rowsAdapter.notifyArrayItemRangeChanged(0, 1)
        }

        subscribeTo(viewModel.detailsData) { details ->
            if (details != null) {
                detailsRow.details = details
                rowsAdapter.notifyArrayItemRangeChanged(0, 1)
                applyImage(details.image)
            }
        }

        subscribeTo(viewModel.episodesData) { episodes ->
            episodesAdapter.setItems(episodes, CardDiffCallback)
        }

        subscribeTo(viewModel.relatedData) { related ->
            relatedAdapter.setItems(related, CardDiffCallback)
        }
    }

    private fun applyImage(image: String) {
        backgroundManager.applyImage(image, colorSelector = { null }) { color ->
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(color, hsl)
            hsl[1] = (hsl[1] + 0.05f).coerceAtMost(1.0f)
            hsl[2] = (hsl[2] + 0.05f).coerceAtMost(1.0f)
            ColorUtils.HSLToColor(hsl)
        }
    }
}
