package ru.radiationx.anilibria.extension

import androidx.fragment.app.Fragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.CardDiffCallback
import ru.radiationx.anilibria.ui.presenter.CardPresenterSelector
import ru.radiationx.shared.ktx.android.subscribeTo

fun Fragment.createCardsRowBy(
    rowId: Long,
    rowsAdapter: ArrayObjectAdapter,
    viewModel: BaseCardsViewModel,
    existingRow: ListRow? = null,
): ListRow {
    val cardsPresenter = CardPresenterSelector {
        viewModel.onLinkCardBind()
    }
    val cardsAdapter = existingRow?.adapter as? ArrayObjectAdapter ?: ArrayObjectAdapter(cardsPresenter)
    val row = existingRow ?: ListRow(rowId, HeaderItem(viewModel.defaultTitle), cardsAdapter)
    subscribeTo(viewModel.cardsData) {
        cardsAdapter.setItems(it, CardDiffCallback)
    }
    subscribeTo(viewModel.rowTitle) {
        val position = rowsAdapter.indexOf(row)
        row.headerItem = HeaderItem(it)
        if (position >= 0) rowsAdapter.notifyArrayItemRangeChanged(position, 1)
    }
    return row
}
