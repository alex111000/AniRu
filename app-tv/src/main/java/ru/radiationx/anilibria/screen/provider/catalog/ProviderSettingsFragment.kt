package ru.radiationx.anilibria.screen.provider.catalog

import android.os.Bundle
import android.view.View
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import com.github.terrakok.cicerone.Router
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.provider.*
import ru.radiationx.anilibria.screen.ConfigScreen
import ru.radiationx.anilibria.ui.presenter.CardPresenterSelector
import ru.radiationx.quill.get

class ProviderSettingsFragment : RowsSupportFragment() {
    private val catalog by lazy { get<UnifiedCatalogRepository>() }
    private val rows = ArrayObjectAdapter(ListRowPresenter())
    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        adapter = rows
        setOnItemViewClickedListener { _, _, _, row ->
            val id = (row as? ListRow)?.id?.toInt() ?: return@setOnItemViewClickedListener
            if (id == 100) {
                android.app.AlertDialog.Builder(requireContext()).setTitle("AniRu · источники")
                    .setMessage("Только аниме. Каталог обновляется последовательно. Озвучка запоминается для каждого тайтла.\n\nAnimeLib может требовать авторизацию для части видео. Некоторые плееры ограничены по региону. Закрытые или недоступные потоки не обходятся.\n\nДанные старой истории и избранного сохранены.")
                    .setPositiveButton("ОК", null).show()
            }
            else ProviderId.entries.getOrNull(id)?.let { catalog.setEnabled(it, !catalog.enabled(it)); render() }
        }
        viewLifecycleOwner.lifecycleScope.launch { catalog.providerStatus.collect { render() } }
        render()
        mainFragmentAdapter.fragmentHost?.notifyDataReady(mainFragmentAdapter)
    }
    private fun render() {
        rows.clear()
        ProviderId.entries.forEachIndexed { index, id ->
            val caption = "${if (catalog.enabled(id)) "✓" else "○"} ${id.uiName} — ${catalog.providerStatus.value[id] ?: "Ещё не проверен"}"
            rows.add(ListRow(index.toLong(), HeaderItem(index.toLong(), caption), ArrayObjectAdapter(CardPresenterSelector(null)).apply { add(LinkCard("Включить / выключить")) }))
        }
        rows.add(ListRow(100, HeaderItem(100, "О приложении и доступности источников"), ArrayObjectAdapter(CardPresenterSelector(null)).apply { add(LinkCard("Подробнее")) }))
    }
}
