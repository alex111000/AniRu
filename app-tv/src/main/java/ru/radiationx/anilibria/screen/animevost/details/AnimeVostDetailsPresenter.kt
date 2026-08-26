package ru.radiationx.anilibria.screen.animevost.details

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.leanback.widget.RowPresenter
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.common.LibriaDetails
import ru.radiationx.anilibria.common.LibriaDetailsRow
import ru.radiationx.anilibria.databinding.RowDetailReleaseBinding
import ru.radiationx.shared_app.imageloader.showImageUrl

class AnimeVostDetailsPresenter(
    private val playClickListener: () -> Unit,
    private val favoriteClickListener: () -> Unit,
) : RowPresenter() {

    init {
        headerPresenter = null
    }

    override fun isUsingDefaultSelectEffect(): Boolean = false

    override fun createRowViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.row_detail_release, parent, false)
        return AnimeVostDetailsViewHolder(
            view = view,
            playClickListener = playClickListener,
            favoriteClickListener = favoriteClickListener,
        )
    }

    override fun onBindRowViewHolder(vh: ViewHolder, item: Any) {
        super.onBindRowViewHolder(vh, item)
        (vh as AnimeVostDetailsViewHolder).bind(item as LibriaDetailsRow)
    }
}

private class AnimeVostDetailsViewHolder(
    view: View,
    playClickListener: () -> Unit,
    favoriteClickListener: () -> Unit,
) : RowPresenter.ViewHolder(view) {

    private val binding = RowDetailReleaseBinding.bind(view)

    init {
        binding.rowReleaseActionPlay.setOnClickListener { playClickListener() }
        binding.rowReleaseActionFavorite.setOnClickListener { favoriteClickListener() }
        binding.rowReleaseActionContinue.isVisible = false
        binding.rowReleaseActionFavorite.isVisible = true
        binding.rowReleaseActionOther.isVisible = false
        binding.root.updateLayoutParams {
            height = binding.root.resources.displayMetrics.heightPixels - 1
        }
    }

    fun bind(item: LibriaDetailsRow) {
        val details = item.details
        val loading = item.state?.loadingProgress == true

        binding.rowReleaseRoot.isFocusable = loading
        binding.rowReleaseActions.isInvisible = loading
        binding.rowReleaseImageCard.isInvisible = loading
        binding.rowReleaseLoadingProgress.isVisible = loading
        binding.rowReleaseUpdateProgress.isVisible = false

        if (details != null) {
            bindDetails(details)
        }
    }

    private fun bindDetails(details: LibriaDetails) {
        binding.rowReleaseTitleRu.text = details.titleRu
        binding.rowReleaseTitleEn.text = details.titleEn
        binding.rowReleaseExtra.text = details.extra
        binding.rowReleaseDescription.text = details.description
        binding.rowReleaseAnnounce.text = details.announce
        binding.rowReleaseAnnounce.isVisible = details.announce.isNotBlank()
        binding.rowReleaseFavoriteCount.isVisible = false
        binding.rowReleaseHQMarker.isVisible = details.hasFullHd
        binding.rowReleaseActionPlay.isVisible = details.hasEpisodes
        binding.rowReleaseActionFavorite.isVisible = true
        binding.rowReleaseActionFavorite.text = if (details.isFavorite) {
            "Убрать из избранного"
        } else {
            "Добавить в избранное"
        }
        binding.rowReleaseImageCard.showImageUrl(details.image)

        if (details.hasEpisodes) {
            binding.rowReleaseActionPlay.requestFocus()
        } else {
            binding.rowReleaseActionFavorite.requestFocus()
        }
    }
}
