package ru.radiationx.anilibria.screen.provider.catalog

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

class ProviderCatalogFragment : BaseVerticalGridFragment() {
    companion object {
        private const val ARG_PROVIDER = "provider_catalog_provider"
        private const val ARG_TITLE = "provider_catalog_title"
        fun newInstance(providerId: String, title: String) = ProviderCatalogFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_PROVIDER, providerId)
                putString(ARG_TITLE, title)
            }
        }
    }

    private val providerId by lazy { requireArguments().getString(ARG_PROVIDER).orEmpty() }
    private val catalogTitle by lazy { requireArguments().getString(ARG_TITLE).orEmpty().ifBlank { "AniRu" } }
    private val viewModel by viewModel<ProviderCatalogViewModel> { ProviderCatalogExtra(providerId, catalogTitle) }
    private val backgroundManager by inject<GradientBackgroundManager>()
    private val cardsAdapter = ArrayObjectAdapter(CardPresenterSelector { viewModel.onLinkCardBind() })

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
