package ru.radiationx.anilibria.di

import android.content.Context
import ru.mintrocket.lib.mintpermissions.MintPermissions
import ru.mintrocket.lib.mintpermissions.flows.MintPermissionsFlow
import ru.radiationx.anilibria.AppBuildConfig
import ru.radiationx.anilibria.AppMigrationExecutor
import ru.radiationx.anilibria.animevost.AnimeVostFavoritesRepository
import ru.radiationx.anilibria.animevost.AnimeVostHistoryRepository
import ru.radiationx.anilibria.animevost.AnimeVostRepository
import ru.radiationx.anilibria.TvCheckerSources
import ru.radiationx.anilibria.favorites.LocalFavoritesRepository
import ru.radiationx.anilibria.search.UnifiedSearchRepository
import ru.radiationx.anilibria.provider.ProviderHttpClient
import ru.radiationx.anilibria.provider.ProviderLocalRepository
import ru.radiationx.anilibria.provider.ProviderRegistry
import ru.radiationx.anilibria.provider.impl.AniLibriaProvider
import ru.radiationx.anilibria.provider.impl.AnimeVostProvider
import ru.radiationx.anilibria.provider.impl.SameBandProvider
import ru.radiationx.anilibria.provider.impl.YummyAnimeProvider
import ru.radiationx.data.SharedBuildConfig
import ru.radiationx.data.analytics.AnalyticsErrorReporter
import ru.radiationx.data.analytics.AnalyticsSender
import ru.radiationx.data.analytics.profile.AnalyticsProfile
import ru.radiationx.data.datasource.remote.common.CheckerReserveSources
import ru.radiationx.data.migration.MigrationExecutor
import ru.radiationx.quill.QuillModule
import ru.radiationx.shared_app.analytics.errors.LoggingErrorReporter
import ru.radiationx.shared_app.analytics.events.LoggingAnalyticsSender
import ru.radiationx.shared_app.analytics.profile.LoggingAnalyticsProfile
import ru.radiationx.shared_app.imageloader.LibriaImageLoader
import ru.radiationx.shared_app.imageloader.impls.CoilLibriaImageLoaderImpl

class AppModule(context: Context) : QuillModule() {


    init {
        instance { context }

        singleImpl<SharedBuildConfig, AppBuildConfig>()
        singleImpl<CheckerReserveSources, TvCheckerSources>()
        singleImpl<MigrationExecutor, AppMigrationExecutor>()

        singleImpl<LibriaImageLoader, CoilLibriaImageLoaderImpl>()
        single<AnimeVostRepository>()
        single<AnimeVostHistoryRepository>()
        single<AnimeVostFavoritesRepository>()
        single<LocalFavoritesRepository>()
        single<UnifiedSearchRepository>()
        single<ProviderHttpClient>()
        single<AniLibriaProvider>()
        single<AnimeVostProvider>()
        single<YummyAnimeProvider>()
        single<SameBandProvider>()
        single<ProviderRegistry>()
        single<ProviderLocalRepository>()

        instance {
            MintPermissions.controller
        }

        instance {
            MintPermissionsFlow.dialogFlow
        }

        // AniRu keeps diagnostics local in logcat and does not send upstream analytics.
        singleImpl<AnalyticsSender, LoggingAnalyticsSender>()
        singleImpl<AnalyticsProfile, LoggingAnalyticsProfile>()
        singleImpl<AnalyticsErrorReporter, LoggingErrorReporter>()
    }

}