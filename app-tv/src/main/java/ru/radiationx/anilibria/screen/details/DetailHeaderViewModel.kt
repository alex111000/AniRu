package ru.radiationx.anilibria.screen.details

import androidx.lifecycle.viewModelScope
import com.github.terrakok.cicerone.Router
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.DetailDataConverter
import ru.radiationx.anilibria.common.DetailsState
import ru.radiationx.anilibria.common.LibriaDetails
import ru.radiationx.anilibria.common.fragment.GuidedRouter
import ru.radiationx.anilibria.favorites.LocalFavoritesRepository
import ru.radiationx.anilibria.screen.DetailOtherGuidedScreen
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.anilibria.screen.PlayerEpisodesGuidedScreen
import ru.radiationx.anilibria.screen.PlayerScreen
import ru.radiationx.anilibria.screen.player.PlayerController
import ru.radiationx.data.entity.domain.release.EpisodeAccess
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.interactors.ReleaseInteractor
import javax.inject.Inject

class DetailHeaderViewModel @Inject constructor(
    argExtra: DetailExtra,
    private val releaseInteractor: ReleaseInteractor,
    private val localFavoritesRepository: LocalFavoritesRepository,
    private val converter: DetailDataConverter,
    private val router: Router,
    private val guidedRouter: GuidedRouter,
    private val playerController: PlayerController,
) : LifecycleViewModel() {

    private val releaseId = argExtra.id

    val releaseData = MutableStateFlow<LibriaDetails?>(null)
    val progressState = MutableStateFlow(DetailsState())

    private var currentRelease: Release? = null
    private var isFullLoaded = false

    private var selectEpisodeJob: Job? = null
    private var favoriteDisposable: Job? = null

    init {
        updateProgress()
        releaseInteractor.getItem(releaseId)?.also {
            updateRelease(it, emptyList())
        }
        combine(
            releaseInteractor.observeFull(releaseId),
            releaseInteractor.observeAccesses(releaseId)
        ) { release, accesses ->
            isFullLoaded = true
            updateRelease(release, accesses)
        }.launchIn(viewModelScope)
    }

    override fun onResume() {
        super.onResume()

        selectEpisodeJob?.cancel()
        selectEpisodeJob = playerController
            .selectEpisodeRelay
            .onEach { episodeId ->
                router.navigateTo(PlayerScreen(releaseId, episodeId))
            }
            .launchIn(viewModelScope)
    }

    override fun onPause() {
        super.onPause()
        selectEpisodeJob?.cancel()
    }

    fun onContinueClick() {
        viewModelScope.launch {
            releaseInteractor.getAccesses(releaseId).maxByOrNull { it.lastAccessRaw }?.also {
                router.navigateTo(PlayerScreen(releaseId, it.id))
            }
        }
    }

    fun onPlayClick() {
        val release = currentRelease ?: return
        if (release.episodes.isEmpty()) return
        if (release.episodes.size == 1) {
            router.navigateTo(PlayerScreen(releaseId, null))
        } else {
            viewModelScope.launch {
                val episodeId =
                    releaseInteractor.getAccesses(releaseId).maxByOrNull { it.lastAccessRaw }?.id
                guidedRouter.open(PlayerEpisodesGuidedScreen(releaseId, episodeId))
            }
        }
    }

    fun onFavoriteClick() {
        val release = currentRelease ?: return

        favoriteDisposable?.cancel()
        favoriteDisposable = viewModelScope.launch {
            val isFavorite = localFavoritesRepository.toggle(releaseId)
            currentRelease?.also { data ->
                val newData = data.copy(
                    favoriteInfo = data.favoriteInfo.copy(isAdded = isFavorite)
                )
                currentRelease = newData
                releaseInteractor.updateFullCache(newData)
                releaseData.value = converter.toDetail(newData, isFullLoaded, releaseInteractor.getAccesses(releaseId))
            }
        }.apply {
            invokeOnCompletion { updateProgress() }
        }

        updateProgress()
    }

    fun onDescriptionClick() {

    }

    fun onOtherClick() {
        guidedRouter.open(DetailOtherGuidedScreen(releaseId))
    }

    private fun updateRelease(release: Release, accesses: List<EpisodeAccess>) {
        val localRelease = release.copy(
            favoriteInfo = release.favoriteInfo.copy(
                isAdded = localFavoritesRepository.isFavorite(releaseId)
            )
        )
        currentRelease = localRelease
        releaseData.value = converter.toDetail(localRelease, isFullLoaded, accesses)
        updateProgress()
    }

    private fun updateProgress() {
        progressState.value = DetailsState(
            currentRelease == null,
            currentRelease == null || favoriteDisposable?.isActive ?: false
        )
    }
}