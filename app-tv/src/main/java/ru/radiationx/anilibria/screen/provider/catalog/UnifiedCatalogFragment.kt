package ru.radiationx.anilibria.screen.provider.catalog

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import com.github.terrakok.cicerone.Router
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.CardDiffCallback
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.provider.*
import ru.radiationx.anilibria.screen.ProviderDetailsScreen
import ru.radiationx.quill.get
import ru.radiationx.shared_app.imageloader.showImageUrl

/** A real poster grid embedded in BrowseSupportFragment, also usable as a standalone screen. */
class UnifiedCatalogFragment : Fragment(), BrowseSupportFragment.MainFragmentAdapterProvider {
    companion object {
        fun newInstance(mode: String) = UnifiedCatalogFragment().apply { arguments = Bundle().apply { putString("mode", mode) } }
    }
    private val catalog by lazy { get<UnifiedCatalogRepository>() }
    private val router by lazy { get<Router>() }
    private val mode get() = arguments?.getString("mode") ?: "SERIES"
    private val browseAdapter = object : BrowseSupportFragment.MainFragmentAdapter<UnifiedCatalogFragment>(this) {
        override fun isScrolling(): Boolean = (grid?.scrollState ?: 0) != 0
    }
    override fun getMainFragmentAdapter(): BrowseSupportFragment.MainFragmentAdapter<*> = browseAdapter
    private var grid: VerticalGridView? = null
    private var label: TextView? = null
    private var moreButton: Button? = null
    private var cards: ArrayObjectAdapter? = null
    private var sourceItems = emptyList<UnifiedAnime>()
    private var genre: String? = null
    private var year: Int? = null
    private var order = CatalogOrder.ADDED
    private var searchJob: Job? = null
    private var count = 60
    private var query = ""
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(20), 0)
            val controls = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            fun button(text: String, click: () -> Unit) {
                controls.addView(Button(context).apply { this.text = text; textSize = 12f; isAllCaps = false; setOnClickListener { click() } },
                    LinearLayout.LayoutParams(0, dp(46), 1f))
            }
            button(if (mode == "SEARCH") "Поиск" else "Жанр") { if (mode == "SEARCH") searchDialog() else chooseGenre() }
            button("Год") { chooseYear() }
            button("Сортировка") { chooseOrder() }
            button("Сброс") { genre = null; year = null; order = CatalogOrder.ADDED; count = 60; render() }
            button("Обновить") { if (mode == "SEARCH") search(query) else catalog.start(refresh = true) }
            addView(controls)
            label = TextView(context).apply { setTextColor(Color.LTGRAY); textSize = 13f; setPadding(0, dp(6), 0, dp(6)) }.also { addView(it) }
            val adapter = ArrayObjectAdapter(object : Presenter() {
                override fun onCreateViewHolder(parent: ViewGroup): ViewHolder = ViewHolder(ImageCardView(ContextThemeWrapper(parent.context, R.style.AniRuCatalogThemeOverlay)).apply {
                    isFocusable = true; isFocusableInTouchMode = true
                    val width = if (resources.configuration.screenWidthDp >= 960) 140 else 116
                    setMainImageDimensions(dp(width), dp((width * 1.45).toInt()))
                    setMainImageScaleType(ImageView.ScaleType.CENTER_CROP)
                })
                override fun onBindViewHolder(holder: ViewHolder, item: Any?) {
                    val anime = item as? UnifiedAnime ?: return
                    (holder.view as ImageCardView).apply {
                        titleText = anime.primary.title
                        contentText = listOf(anime.year.takeIf { it > 0 }?.toString().orEmpty(), "Источников: ${anime.versions.size}").filter { it.isNotBlank() }.joinToString(" • ")
                        mainImageView?.showImageUrl(anime.versions.firstOrNull { it.posterUrl.isNotBlank() }?.posterUrl.orEmpty())
                        contentDescription = anime.primary.title
                    }
                }
                override fun onUnbindViewHolder(holder: ViewHolder) { (holder.view as ImageCardView).mainImage = null }
            })
            cards = adapter
            val bridge = ItemBridgeAdapter(adapter).apply {
                setAdapterListener(object : ItemBridgeAdapter.AdapterListener() {
                    override fun onBind(holder: ItemBridgeAdapter.ViewHolder) {
                        holder.itemView.setOnClickListener {
                            val anime = holder.item as? UnifiedAnime ?: return@setOnClickListener
                            router.navigateTo(ProviderDetailsScreen(anime.primary.provider.wireId, anime.primary.id))
                        }
                    }
                    override fun onUnbind(holder: ItemBridgeAdapter.ViewHolder) { holder.itemView.setOnClickListener(null) }
                })
            }
            grid = VerticalGridView(context).apply {
                setNumColumns(if (resources.configuration.screenWidthDp >= 960) 6 else 4)
                setHorizontalSpacing(dp(12)); setVerticalSpacing(dp(16))
                this.adapter = bridge
                clipToPadding = false
                var columns = 0
                addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                    val cardWidth = if (resources.configuration.screenWidthDp >= 960) 140 else 116
                    val fitting = ((v.width + dp(12)) / dp(cardWidth + 12)).coerceIn(1, 8)
                    if (fitting != columns) { columns = fitting; setNumColumns(columns) }
                }
            }.also { addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)) }
            moreButton = Button(context).apply {
                text = "Показать ещё"; isAllCaps = false
                visibility = View.GONE
                setOnClickListener {
                    val firstNew = count
                    count += 60; render(); grid?.requestFocus()
                    grid?.setSelectedPositionSmooth(firstNew.coerceAtMost((cards?.size() ?: 1) - 1))
                }
            }.also { addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42))) }
        }
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        browseAdapter.fragmentHost?.apply {
            notifyViewCreated(browseAdapter)
            showTitleView(false)
        }
        genre = state?.getString("genre")
        year = state?.getInt("year")?.takeIf { it > 0 }
        order = state?.getString("order")?.let { runCatching { CatalogOrder.valueOf(it) }.getOrNull() } ?: CatalogOrder.ADDED
        query = state?.getString("query").orEmpty()
        catalog.start()
        if (mode == "SEARCH") {
            if (query.isNotBlank()) search(query) else label?.text = "Введите название аниме — поиск по всем включённым источникам"
        } else viewLifecycleOwner.lifecycleScope.launch { catalog.items.collect { sourceItems = it; render() } }
        viewLifecycleOwner.lifecycleScope.launch { catalog.status.collect { if (mode != "SEARCH") render() } }
        browseAdapter.fragmentHost?.apply { showTitleView(false); notifyDataReady(browseAdapter) }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("genre", genre); outState.putInt("year", year ?: 0)
        outState.putString("order", order.name); outState.putString("query", query)
        super.onSaveInstanceState(outState)
    }
    private fun render() {
        val wanted = if (mode == "MOVIE") AnimeKind.MOVIE else AnimeKind.SERIES
        val filtered = sourceItems.filter { item -> (mode == "SEARCH" || item.kind == wanted) &&
            (genre == null || item.genres.any { it.equals(genre, true) }) && (year == null || item.year == year) }.ordered(order)
        cards?.setItems(filtered.take(count), object : DiffCallback<UnifiedAnime>() {
            override fun areItemsTheSame(oldItem: UnifiedAnime, newItem: UnifiedAnime) = oldItem.key == newItem.key
            override fun areContentsTheSame(oldItem: UnifiedAnime, newItem: UnifiedAnime) = oldItem == newItem
        })
        moreButton?.visibility = if (filtered.size > count) View.VISIBLE else View.GONE
        label?.text = "${if (mode == "MOVIE") "Фильмы" else if (mode == "SEARCH") "Поиск: $query" else "Сериалы"} · ${filtered.size} · ${genre ?: "Все жанры"} · ${year ?: "Все годы"} · ${order.label}\n" +
            if (mode == "SEARCH") "Результаты появляются по мере ответа источников" else catalog.status.value
        browseAdapter.fragmentHost?.apply { showTitleView(false); notifyDataReady(browseAdapter) }
    }
    private fun chooseGenre() {
        val values = listOf("Все жанры") + sourceItems.flatMap { it.genres }.filter { it.isNotBlank() }.distinct().sorted()
        AlertDialog.Builder(requireContext()).setTitle("Жанр").setItems(values.toTypedArray()) { _, index -> genre = values[index].takeUnless { index == 0 }; count = 60; render() }.show()
    }
    private fun chooseYear() {
        val values = sourceItems.map { it.year }.filter { it > 0 }.distinct().sortedDescending()
        AlertDialog.Builder(requireContext()).setTitle("Год выпуска").setItems((listOf("Все годы") + values.map { it.toString() }).toTypedArray()) { _, index -> year = values.getOrNull(index - 1); count = 60; render() }.show()
    }
    private fun chooseOrder() {
        AlertDialog.Builder(requireContext()).setTitle("Сортировка").setItems(CatalogOrder.entries.map { it.label }.toTypedArray()) { _, index -> order = CatalogOrder.entries[index]; render() }.show()
    }
    private fun searchDialog() {
        val input = EditText(requireContext()).apply { setSingleLine(); setText(query); hint = "Название аниме" }
        AlertDialog.Builder(requireContext()).setTitle("Поиск по всем источникам").setView(input)
            .setPositiveButton("Найти") { _, _ -> search(input.text.toString()) }.setNegativeButton("Отмена", null).show()
    }
    private fun search(text: String) {
        query = text.trim()
        if (query.length < 2) { label?.text = "Введите хотя бы 2 символа"; return }
        searchJob?.cancel(); count = 60
        searchJob = viewLifecycleOwner.lifecycleScope.launch { catalog.search(query) { sourceItems = it; render() } }
    }
    override fun onDestroyView() {
        searchJob?.cancel(); grid?.adapter = null; grid = null; cards = null; label = null; moreButton = null
        super.onDestroyView()
    }
}
