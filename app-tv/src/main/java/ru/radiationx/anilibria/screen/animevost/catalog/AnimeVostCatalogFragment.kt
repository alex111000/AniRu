package ru.radiationx.anilibria.screen.animevost.catalog

import android.os.Bundle
import android.view.View
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.VerticalGridPresenter
import ru.radiationx.anilibria.common.CardDiffCallback
import ru.radiationx.anilibria.common.GradientBackgroundManager
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.common.fragment.BaseVerticalGridFragment
import ru.radiationx.anilibria.extension.applyCard
import ru.radiationx.anilibria.ui.presenter.CardPresenterSelector
import ru.radiationx.quill.inject
import ru.radiationx.quill.viewModel
import ru.radiationx.shared.ktx.android.subscribeTo

class AnimeVostCatalogFragment : BaseVerticalGridFragment() {

    companion object {
        private const val ARG_PATH = "animevost_catalog_path"
        private const val ARG_TITLE = "animevost_catalog_title"

        fun newInstance(path: String?, title: String) = AnimeVostCatalogFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_PATH, path)
                putString(ARG_TITLE, title)
            }
        }
    }

    private val catalogPath by lazy { arguments?.getString(ARG_PATH) }
    private val catalogTitle by lazy { arguments?.getString(ARG_TITLE).orEmpty().ifBlank { "AnimeVost" } }
    private val viewModel by viewModel<AnimeVostCatalogViewModel> {
        AnimeVostCatalogExtra(catalogPath, catalogTitle)
    }
    private val backgroundManager by inject<GradientBackgroundManager>()
    private val cardsPresenter = CardPresenterSelector { viewModel.onLinkCardBind() }
    private val cardsAdapter = ArrayObjectAdapter(cardsPresenter)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = catalogTitle
        setGridPresenter(VerticalGridPresenter().apply { numberOfColumns = 6 })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycle.addObserver(viewModel)
        adapter = cardsAdapter
        backgroundManager.clearGradient()

        setOnItemViewSelectedListener { _, item, _, _ ->
            backgroundManager.applyCard(item)
            when (item) {
                is LibriaCard -> setDescription(item.title, item.description)
                is LinkCard -> setDescription(item.title, "")
                is LoadingCard -> setDescription(item.title, item.description)
                else -> setDescription("", "")
            }
        }
        setOnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is LibriaCard -> viewModel.onLibriaCardClick(item)
                is LinkCard -> viewModel.onLinkCardClick()
                is LoadingCard -> viewModel.onLoadingCardClick()
            }
        }
        prepareEntranceTransition()
        subscribeTo(viewModel.cardsData) {
            cardsAdapter.setItems(it, CardDiffCallback)
            setDescriptionVisible(it.isNotEmpty())
            startEntranceTransition()
        }
    }
}
