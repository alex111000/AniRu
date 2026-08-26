package ru.radiationx.anilibria.common

import com.github.terrakok.cicerone.Router
import ru.radiationx.anilibria.screen.DetailsScreen
import ru.radiationx.anilibria.screen.AnimeVostCatalogScreen
import ru.radiationx.anilibria.screen.AnimeVostDetailsScreen
import ru.radiationx.anilibria.screen.AnimeVostPlayerScreen
import ru.radiationx.anilibria.screen.ProviderDetailsScreen
import ru.radiationx.anilibria.screen.ProviderPlayerScreen
import ru.radiationx.anilibria.screen.ProviderSourcesGuidedScreen
import ru.radiationx.anilibria.common.fragment.GuidedRouter
import ru.radiationx.shared_app.common.SystemUtils
import javax.inject.Inject

class LibriaCardRouter @Inject constructor(
    private val router: Router,
    private val guidedRouter: GuidedRouter,
    private val systemUtils: SystemUtils
) {

    fun navigate(libriaCard: LibriaCard) {
        when (val type = libriaCard.type) {
            is LibriaCard.Type.Release -> {
                router.navigateTo(DetailsScreen(type.releaseId))
            }

            is LibriaCard.Type.Youtube -> {
                systemUtils.externalLink(type.link)
            }

            is LibriaCard.Type.AnimeVost -> {
                router.navigateTo(AnimeVostDetailsScreen(type.animeUrl))
            }


            is LibriaCard.Type.Provider -> {
                router.navigateTo(ProviderDetailsScreen(type.providerId, type.animeId))
            }

            is LibriaCard.Type.ProviderEpisode -> {
                if (type.directPlay) {
                    router.navigateTo(
                        ProviderPlayerScreen(
                            providerId = type.providerId,
                            animeId = type.animeId,
                            episodeId = type.episodeId,
                            sourceId = type.sourceId,
                        )
                    )
                } else {
                    guidedRouter.open(
                        ProviderSourcesGuidedScreen(
                            providerId = type.providerId,
                            animeId = type.animeId,
                            episodeId = type.episodeId,
                            replacePlayer = false,
                            currentSourceId = type.sourceId,
                        )
                    )
                }
            }

            is LibriaCard.Type.AnimeVostCatalog -> {
                router.navigateTo(AnimeVostCatalogScreen(type.path, type.title))
            }

            is LibriaCard.Type.AnimeVostEpisode -> {
                router.navigateTo(
                    AnimeVostPlayerScreen(
                        animeUrl = type.animeUrl,
                        videoId = type.videoId,
                        episodeName = type.episodeName,
                    )
                )
            }
        }
    }
}