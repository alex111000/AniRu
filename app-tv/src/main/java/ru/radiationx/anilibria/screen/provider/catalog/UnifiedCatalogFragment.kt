package ru.radiationx.anilibria.screen.provider.catalog

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.terrakok.cicerone.Router
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.common.*
import ru.radiationx.anilibria.common.fragment.BaseVerticalGridFragment
import ru.radiationx.anilibria.extension.applyCard
import ru.radiationx.anilibria.provider.*
import ru.radiationx.anilibria.screen.ProviderDetailsScreen
import ru.radiationx.anilibria.ui.presenter.CardPresenterSelector
import ru.radiationx.anilibria.ui.widget.SearchTitleView
import ru.radiationx.quill.get

/** Original 1.2.7 catalog surface and presenters, backed by the unified index. */
class UnifiedCatalogFragment : BaseVerticalGridFragment(), BrowseSupportFragment.MainFragmentAdapterProvider {
    companion object {
        fun newInstance(mode: String) = UnifiedCatalogFragment().apply {
            arguments = Bundle().apply { putString("mode", mode) }
        }
    }
    private val catalog by lazy { get<UnifiedCatalogRepository>() }
    private val router by lazy { get<Router>() }
    private val background by lazy { get<GradientBackgroundManager>() }
    private val mode get() = arguments?.getString("mode") ?: "SERIES"
    private val browseAdapter = BrowseSupportFragment.MainFragmentAdapter(this)
    override fun getMainFragmentAdapter(): BrowseSupportFragment.MainFragmentAdapter<*> = browseAdapter
    private val cards = ArrayObjectAdapter(CardPresenterSelector(null))
    private var sourceItems = emptyList<UnifiedAnime>()
    private var genre: String? = null
    private var year: Int? = null
    private var order = CatalogOrder.ADDED
    private var query = ""
    private var searchJob: Job? = null
    private var renderJob: Job? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        genre = state?.getString("genre")
        year = state?.getInt("year")?.takeIf { it > 0 }
        order = state?.getString("order")?.let { runCatching { CatalogOrder.valueOf(it) }.getOrNull() } ?: CatalogOrder.ADDED
        query = state?.getString("query").orEmpty()
        title = when (mode) { "MOVIE" -> "Фильмы"; "SEARCH" -> "Поиск"; else -> "Сериалы" }
        setGridPresenter(VerticalGridPresenter().apply { numberOfColumns = 6 })
    }

    override fun onInflateTitleView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.lb_search_titleview, parent, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        adapter = cards
        background.clearGradient()
        browseAdapter.fragmentHost?.apply { notifyViewCreated(browseAdapter); showTitleView(false) }
        setOnSearchClickedListener { searchDialog() }
        (titleView as? SearchTitleView)?.apply {
            season = null; onlyCompleted = null
            setYearClickListener { chooseYear() }
            setGenreClickListener { chooseGenre() }
            setSortClickListener { chooseOrder() }
        }
        setOnItemViewSelectedListener { _, item, _, _ ->
            background.applyCard(item)
            when (item) {
                is LibriaCard -> setDescription(item.title, item.description)
                is LinkCard -> setDescription(item.title, "")
                else -> setDescription("", "")
            }
        }
        setOnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is LibriaCard -> (item.type as? LibriaCard.Type.Provider)?.let {
                    router.navigateTo(ProviderDetailsScreen(it.providerId, it.animeId))
                }
                is LinkCard -> Unit
            }
        }
        sourceItems = catalog.items.value
        render()
        catalog.start()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (mode != "SEARCH") launch { catalog.items.collect {
                    sourceItems = it
                    renderJob?.cancel()
                    renderJob = launch { delay(500); render() }
                } }
                launch { catalog.loading.collect { if (sourceItems.isEmpty()) render() } }
            }
        }
        if (mode == "SEARCH" && query.isNotBlank()) search(query)
        browseAdapter.fragmentHost?.notifyDataReady(browseAdapter)
    }

    private fun render() {
        val wanted = if (mode == "MOVIE") AnimeKind.MOVIE else AnimeKind.SERIES
        val normalized = AnimeIdentity.normalize(query)
        val filtered = sourceItems.filter { item ->
            (mode == "SEARCH" || item.kind == wanted) &&
                (genre == null || item.genres.any { it.equals(genre, true) }) &&
                (year == null || item.year == year) &&
                (normalized.isEmpty() || item.versions.any { AnimeIdentity.names(it).any { name -> name.contains(normalized) } })
        }.ordered(order)
        val visible = filtered.mapTo(mutableListOf<CardItem>()) { anime ->
            LibriaCard(anime.primary.title,
                listOf(anime.year.takeIf { it > 0 }?.toString().orEmpty(), anime.primary.description).filter { it.isNotBlank() }.joinToString(" · "),
                anime.versions.firstOrNull { it.posterUrl.isNotBlank() }?.posterUrl.orEmpty(),
                LibriaCard.Type.Provider(anime.primary.provider.wireId, anime.primary.id))
        }
        cards.setItems(visible, CardDiffCallback)
        setDescriptionVisible(visible.isNotEmpty())
        if (visible.isEmpty()) {
            setDescriptionVisible(true)
            setDescription(if (catalog.loading.value) "Первое обновление каталога" else "Ничего не найдено",
                if (catalog.loading.value) "Сохранённые аниме появятся сразу при следующем запуске" else "Измените параметры поиска или проверьте источники в настройках")
        }
        (titleView as? SearchTitleView)?.apply {
            year = this@UnifiedCatalogFragment.year?.toString() ?: "Год"
            genre = this@UnifiedCatalogFragment.genre ?: "Жанр"
            sort = order.label
        }
        browseAdapter.fragmentHost?.apply { showTitleView(false); notifyDataReady(browseAdapter) }
    }

    private fun chooseGenre() {
        val values = listOf("Все жанры") + sourceItems.flatMap { it.genres }.filter { it.isNotBlank() }.distinct().sorted()
        AlertDialog.Builder(requireContext()).setTitle("Жанр").setItems(values.toTypedArray()) { _, i ->
            genre = values[i].takeUnless { i == 0 }; render(); setSelectedPosition(0)
        }.show()
    }
    private fun chooseYear() {
        val values = sourceItems.map { it.year }.filter { it > 0 }.distinct().sortedDescending()
        AlertDialog.Builder(requireContext()).setTitle("Год выпуска").setItems((listOf("Все годы") + values.map { it.toString() }).toTypedArray()) { _, i ->
            year = values.getOrNull(i - 1); render(); setSelectedPosition(0)
        }.show()
    }
    private fun chooseOrder() {
        AlertDialog.Builder(requireContext()).setTitle("Сортировка").setItems(CatalogOrder.entries.map { it.label }.toTypedArray()) { _, i ->
            order = CatalogOrder.entries[i]; render(); setSelectedPosition(0)
        }.show()
    }
    private fun searchDialog() {
        val input = EditText(requireContext()).apply { setSingleLine(); setText(query); hint = "Название аниме" }
        AlertDialog.Builder(requireContext()).setTitle("Поиск").setView(input)
            .setPositiveButton("Найти") { _, _ -> search(input.text.toString()) }
            .setNeutralButton("Сбросить") { _, _ -> search("") }.setNegativeButton("Отмена", null).show()
    }
    private fun search(text: String) {
        query = text.trim(); searchJob?.cancel()
        sourceItems = catalog.items.value; render()
        if (query.length >= 2) searchJob = viewLifecycleOwner.lifecycleScope.launch {
            catalog.search(query) { sourceItems = it; render() }
        }
    }
    override fun onSaveInstanceState(state: Bundle) {
        state.putString("genre", genre); state.putInt("year", year ?: 0)
        state.putString("order", order.name); state.putString("query", query)
        super.onSaveInstanceState(state)
    }
    override fun onDestroyView() {
        searchJob?.cancel()
        renderJob?.cancel()
        super.onDestroyView()
    }
}
