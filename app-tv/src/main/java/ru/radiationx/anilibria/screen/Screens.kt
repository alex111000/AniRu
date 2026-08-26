package ru.radiationx.anilibria.screen

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import com.github.terrakok.cicerone.androidx.FragmentScreen
import ru.radiationx.anilibria.common.fragment.FakeGuidedStepFragment
import ru.radiationx.anilibria.common.fragment.GuidedAppScreen
import ru.radiationx.anilibria.screen.auth.credentials.AuthCredentialsGuidedFragment
import ru.radiationx.anilibria.screen.animevost.catalog.AnimeVostCatalogFragment
import ru.radiationx.anilibria.screen.animevost.details.AnimeVostDetailsFragment
import ru.radiationx.anilibria.screen.animevost.episodes.AnimeVostEpisodesGuidedFragment
import ru.radiationx.anilibria.screen.animevost.player.AnimeVostPlayerFragment
import ru.radiationx.anilibria.screen.auth.main.AuthGuidedFragment
import ru.radiationx.anilibria.screen.auth.otp.AuthOtpGuidedFragment
import ru.radiationx.anilibria.screen.config.ConfigFragment
import ru.radiationx.anilibria.screen.catalog.CatalogSourcesGuidedFragment
import ru.radiationx.anilibria.screen.details.DetailFragment
import ru.radiationx.anilibria.screen.details.other.DetailOtherGuidedFragment
import ru.radiationx.anilibria.screen.mainpages.MainPagesFragment
import ru.radiationx.anilibria.screen.player.PlayerFragment
import ru.radiationx.anilibria.screen.player.end_episode.EndEpisodeGuidedFragment
import ru.radiationx.anilibria.screen.player.end_season.EndSeasonGuidedFragment
import ru.radiationx.anilibria.screen.player.episodes.PlayerEpisodesGuidedFragment
import ru.radiationx.anilibria.screen.player.putIds
import ru.radiationx.anilibria.screen.player.quality.PlayerQualityGuidedFragment
import ru.radiationx.anilibria.screen.player.speed.PlayerSpeedGuidedFragment
import ru.radiationx.anilibria.screen.schedule.ScheduleFragment
import ru.radiationx.anilibria.screen.search.SearchFragment
import ru.radiationx.anilibria.screen.search.completed.SearchCompletedGuidedFragment
import ru.radiationx.anilibria.screen.search.genre.SearchGenreGuidedFragment
import ru.radiationx.anilibria.screen.search.putValues
import ru.radiationx.anilibria.screen.search.season.SearchSeasonGuidedFragment
import ru.radiationx.anilibria.screen.search.sort.SearchSortGuidedFragment
import ru.radiationx.anilibria.screen.search.year.SearchYearGuidedFragment
import ru.radiationx.anilibria.screen.suggestions.SuggestionsFragment
import ru.radiationx.anilibria.screen.trash.TestFragment
import ru.radiationx.anilibria.screen.update.UpdateFragment
import ru.radiationx.anilibria.screen.update.source.UpdateSourceGuidedFragment
import ru.radiationx.anilibria.screen.update.warning.UpdateWarningGuidedFragment
import ru.radiationx.anilibria.screen.provider.catalog.ProviderCatalogFragment
import ru.radiationx.anilibria.screen.provider.details.ProviderDetailsFragment
import ru.radiationx.anilibria.screen.provider.episodes.ProviderEpisodesGuidedFragment
import ru.radiationx.anilibria.screen.provider.episodes.ProviderSourcesGuidedFragment
import ru.radiationx.anilibria.screen.provider.player.ProviderPlayerFragment
import ru.radiationx.data.entity.domain.search.SearchForm
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.entity.domain.updater.UpdateData




class ProviderDetailsScreen(
    private val providerId: String,
    private val animeId: String,
) : FragmentScreen {
    override fun createFragment(factory: FragmentFactory): Fragment =
        ProviderDetailsFragment.newInstance(providerId, animeId)
}

class ProviderPlayerScreen(
    private val providerId: String,
    private val animeId: String,
    private val episodeId: String,
    private val sourceId: String?,
) : FragmentScreen {
    override fun createFragment(factory: FragmentFactory): Fragment =
        ProviderPlayerFragment.newInstance(providerId, animeId, episodeId, sourceId)
}

class ProviderCatalogScreen(
    private val providerId: String,
    private val title: String,
) : FragmentScreen {
    override fun createFragment(factory: FragmentFactory): Fragment =
        ProviderCatalogFragment.newInstance(providerId, title)
}

class ProviderEpisodesGuidedScreen(
    private val providerId: String,
    private val animeId: String,
    private val currentEpisodeId: String?,
    private val replacePlayer: Boolean,
) : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): FakeGuidedStepFragment =
        ProviderEpisodesGuidedFragment.newInstance(providerId, animeId, currentEpisodeId, replacePlayer)
}

class ProviderSourcesGuidedScreen(
    private val providerId: String,
    private val animeId: String,
    private val episodeId: String,
    private val replacePlayer: Boolean,
    private val currentSourceId: String?,
) : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): FakeGuidedStepFragment =
        ProviderSourcesGuidedFragment.newInstance(providerId, animeId, episodeId, replacePlayer, currentSourceId)
}

class AnimeVostCatalogScreen(
    private val path: String?,
    private val title: String,
) : FragmentScreen {
    override fun createFragment(factory: FragmentFactory): Fragment {
        return AnimeVostCatalogFragment.newInstance(path, title)
    }
}

class AnimeVostDetailsScreen(
    private val animeUrl: String,
) : FragmentScreen {
    override fun createFragment(factory: FragmentFactory): Fragment {
        return AnimeVostDetailsFragment.newInstance(animeUrl)
    }
}

class AnimeVostPlayerScreen(
    private val animeUrl: String,
    private val videoId: String,
    private val episodeName: String,
) : FragmentScreen {
    override fun createFragment(factory: FragmentFactory): Fragment {
        return AnimeVostPlayerFragment.newInstance(
            animeUrl = animeUrl,
            videoId = videoId,
            episodeName = episodeName,
        )
    }
}


class AnimeVostEpisodesGuidedScreen(
    private val animeUrl: String,
    private val currentVideoId: String?,
    private val replacePlayer: Boolean,
) : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): FakeGuidedStepFragment {
        return AnimeVostEpisodesGuidedFragment.newInstance(
            animeUrl = animeUrl,
            currentVideoId = currentVideoId,
            replacePlayer = replacePlayer,
        )
    }
}

class CatalogSourcesGuidedScreen : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): FakeGuidedStepFragment {
        return CatalogSourcesGuidedFragment()
    }
}

class ConfigScreen : FragmentScreen {
    override fun createFragment(factory: FragmentFactory): Fragment {
        return ConfigFragment()
    }
}

class MainPagesScreen : FragmentScreen {
    override fun createFragment(factory: FragmentFactory): Fragment {
        return MainPagesFragment()
    }
}

class DetailsScreen(private val releaseId: ReleaseId) : FragmentScreen {
    override fun createFragment(factory: FragmentFactory): Fragment {
        return DetailFragment.newInstance(releaseId)
    }
}

class DetailOtherGuidedScreen(private val releaseId: ReleaseId) : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): FakeGuidedStepFragment {
        return DetailOtherGuidedFragment.newInstance(releaseId)
    }
}

class ScheduleScreen : FragmentScreen {
    override fun createFragment(factory: FragmentFactory): Fragment {
        return ScheduleFragment()
    }
}

class UpdateScreen : FragmentScreen {
    override fun createFragment(factory: FragmentFactory): Fragment {
        return UpdateFragment()
    }
}

class UpdateSourceScreen : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): FakeGuidedStepFragment {
        return UpdateSourceGuidedFragment()
    }
}

class UpdateWarningScreen(private val link: UpdateData.UpdateLink) : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): FakeGuidedStepFragment {
        return UpdateWarningGuidedFragment.newInstance(link)
    }
}

class SuggestionsScreen : FragmentScreen {
    override fun createFragment(factory: FragmentFactory): Fragment {
        return SuggestionsFragment()
    }
}

class SearchScreen : FragmentScreen {
    override fun createFragment(factory: FragmentFactory): Fragment {
        return SearchFragment()
    }
}

class SearchYearGuidedScreen(private val values: List<String>) : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): FakeGuidedStepFragment {
        return SearchYearGuidedFragment().putValues(values)
    }
}

class SearchSeasonGuidedScreen(private val values: List<String>) : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): FakeGuidedStepFragment {
        return SearchSeasonGuidedFragment().putValues(values)
    }
}

class SearchGenreGuidedScreen(private val values: List<String>) : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): FakeGuidedStepFragment {
        return SearchGenreGuidedFragment().putValues(values)
    }
}

class SearchSortGuidedScreen(private val sort: SearchForm.Sort) : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): FakeGuidedStepFragment {
        return SearchSortGuidedFragment.newInstance(sort)
    }
}

class SearchCompletedGuidedScreen(private val onlyCompleted: Boolean) : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): FakeGuidedStepFragment {
        return SearchCompletedGuidedFragment.newInstance(onlyCompleted)
    }
}

class TestScreen : FragmentScreen {
    override fun createFragment(factory: FragmentFactory): Fragment {
        return TestFragment()
    }
}

class AuthGuidedScreen : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): FakeGuidedStepFragment {
        return AuthGuidedFragment()
    }
}

class AuthCredentialsGuidedScreen : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): FakeGuidedStepFragment {
        return AuthCredentialsGuidedFragment()
    }
}

class AuthOtpGuidedScreen : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): FakeGuidedStepFragment {
        return AuthOtpGuidedFragment()
    }
}

class PlayerScreen(
    private val releaseId: ReleaseId,
    private val episodeId: EpisodeId?,
) : FragmentScreen {
    override fun createFragment(factory: FragmentFactory): Fragment {
        return PlayerFragment.newInstance(releaseId, episodeId)
    }
}

class PlayerQualityGuidedScreen(
    private val releaseId: ReleaseId,
    private val episodeId: EpisodeId?,
) : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): FakeGuidedStepFragment {
        return PlayerQualityGuidedFragment().putIds(releaseId, episodeId)
    }
}

class PlayerSpeedGuidedScreen(
    private val releaseId: ReleaseId,
    private val episodeId: EpisodeId?,
) : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): FakeGuidedStepFragment {
        return PlayerSpeedGuidedFragment().putIds(releaseId, episodeId)
    }
}

class PlayerEpisodesGuidedScreen(
    private val releaseId: ReleaseId,
    private val episodeId: EpisodeId?,
) : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): FakeGuidedStepFragment {
        return PlayerEpisodesGuidedFragment().putIds(releaseId, episodeId)
    }
}

class PlayerEndEpisodeGuidedScreen(
    private val releaseId: ReleaseId,
    private val episodeId: EpisodeId?,
) : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): FakeGuidedStepFragment {
        return EndEpisodeGuidedFragment().putIds(releaseId, episodeId)
    }
}

class PlayerEndSeasonGuidedScreen(
    private val releaseId: ReleaseId,
    private val episodeId: EpisodeId?,
) : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): FakeGuidedStepFragment {
        return EndSeasonGuidedFragment().putIds(releaseId, episodeId)
    }
}

class TestGuidedStepScreen : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): FakeGuidedStepFragment {
        return DialogExampleFragment()
    }
}