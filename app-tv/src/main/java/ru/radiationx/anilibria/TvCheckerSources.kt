package ru.radiationx.anilibria

import ru.radiationx.data.datasource.remote.common.CheckerReserveSources
import javax.inject.Inject

class TvCheckerSources @Inject constructor() : CheckerReserveSources {

    // AniRu updates are built from this repository; upstream AniLibria update feeds are disabled.
    override val sources: List<String> = emptyList()
}