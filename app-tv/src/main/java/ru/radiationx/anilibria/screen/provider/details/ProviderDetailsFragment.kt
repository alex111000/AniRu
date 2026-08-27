package ru.radiationx.anilibria.screen.provider.details

import android.os.Bundle
import android.view.View
import android.widget.Toast
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
import ru.radiationx.anilibria.screen.animevost.details.AnimeVostDetailsPresenter
import ru.radiationx.anilibria.ui.presenter.CardPresenterSelector
import ru.radiationx.anilibria.ui.presenter.cust.CustomListRowPresenter
import ru.radiationx.anilibria.ui.presenter.cust.CustomListRowViewHolder
import ru.radiationx.quill.inject
import ru.radiationx.quill.viewModel
import ru.radiationx.shared.ktx.android.subscribeTo

class ProviderDetailsFragment : RowsSupportFragment() {
    companion object {
        private const val ARG_PROVIDER = "provider_details_provider"
        private const val ARG_ANIME_ID = "provider_details_anime_id"

        fun newInstance(providerId: String, animeId: String) = ProviderDetailsFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_PROVIDER, providerId)
                putString(ARG_ANIME_ID, animeId)
            }
        }
    }

    private val providerId by lazy { requireArguments().getString(ARG_PROVIDER).orEmpty() }
    private val animeId by lazy { requireArguments().getString(ARG_ANIME_ID).orEmpty() }
    private val viewModel by viewModel<ProviderDetailsViewModel> { ProviderDetailExtra(providerId, animeId) }
    private val backgroundManager by inject<GradientBackgroundManager>()

    private val detailsRow = LibriaDetailsRow(1L, state = DetailsState(loadingProgress = true))
    private val episodesAdapter = ArrayObjectAdapter(CardPresenterSelector(null))
    private val episodesRow = ListRow(2L, HeaderItem(2L, "Серии — быстрый доступ"), episodesAdapter)
    private val rowsPresenter by lazy {
        ClassPresenterSelector().apply {
            addClassPresenter(ListRow::class.java, CustomListRowPresenter())
            addClassPresenter(
                LibriaDetailsRow::class.java,
                AnimeVostDetailsPresenter(viewModel::onPlayClick, viewModel::onFavoriteClick),
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

        setOnItemViewClickedListener { _, item, _, _ ->
            if (item is LibriaCard) viewModel.onCardClick(item)
        }
        setOnItemViewSelectedListener { _, item, rowViewHolder, row ->
            if (item is LibriaCard) {
                backgroundManager.applyImage(item.image)
                if (rowViewHolder is CustomListRowViewHolder) rowViewHolder.setDescription(item.title, item.description)
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
        subscribeTo(viewModel.episodesData) { episodesAdapter.setItems(it, CardDiffCallback) }
        subscribeTo(viewModel.errorData) { message ->
            message?.takeIf { it.isNotBlank() }?.let {
                android.app.AlertDialog.Builder(requireContext()).setTitle("Не удалось загрузить аниме")
                    .setMessage("Источник не ответил. Можно повторить загрузку; остальные разделы приложения доступны.")
                    .setPositiveButton("Повторить") { _, _ -> viewModel.retry() }.setNegativeButton("Закрыть", null).show()
            }
        }
    }

    private fun applyImage(image: String) {
        if (image.isBlank()) return
        backgroundManager.applyImage(image, colorSelector = { null }) { color ->
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(color, hsl)
            hsl[1] = (hsl[1] + 0.05f).coerceAtMost(1f)
            hsl[2] = (hsl[2] + 0.05f).coerceAtMost(1f)
            ColorUtils.HSLToColor(hsl)
        }
    }
}
