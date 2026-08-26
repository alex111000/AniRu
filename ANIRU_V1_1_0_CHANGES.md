# AniRu TV v1.1.0

Major integration and reliability update for the Android TV build.

## Search
- Unified title search now queries AniLibria and AnimeVost in parallel.
- Results are displayed in separate provider rows so the source is always clear.
- Android TV/global search suggestions also include both providers.
- AnimeVost search can collect multiple pages instead of only the first page.

## AnimeVost long series / One Piece
- Added support for the AnimeVost full playlist endpoint (`api.animevost.org/v1/playlist`).
- The full playlist is merged with legacy page metadata, so very long series are no longer limited by the page's `var data` block.
- Added a regression test that parses a 1,200-episode playlist without truncation.
- Long shows use 100-episode ranges instead of trying to render 1,000+ actions at once.
- The details screen only shows a quick-access subset of the newest episodes; `Смотреть` opens the complete list.
- Episode chooser marks started/completed episodes and remembered progress.

## Playback
- Direct full-playlist HD/SD links are preferred, with the old AnimeVost frame source kept as fallback.
- Failed/stalled sources automatically fall through to the next candidate and refresh once before giving up.
- AnimeVost playback position is stored per episode, not only per title.
- Resume playback restores the saved position.
- Continue Watching merges AniLibria and AnimeVost by latest activity.
- Completed AnimeVost episodes are not kept in the Continue Watching row.
- Playback speed and screen resize mode are remembered locally.
- Resize modes: fit, fill/crop, stretch.

## AnimeVost browsing
- Added AnimeVost catalog/navigation rows (sections, genres, types and years).
- Added AnimeVost schedule row.
- The top `Каталог` button now asks whether to browse AniLibria or AnimeVost.
- Metadata-only catalog/schedule cards get a local fallback visual instead of appearing blank.
- AnimeVost details show more metadata and related series.

## Local data / privacy
- Favorites remain local and do not require AniLibria login.
- AnimeVost per-episode progress is stored locally for long series.
- Removed initialization of the upstream AniLibria AppMetrica key; diagnostics remain local in Logcat.
- Upstream AniLibria update checking is disabled and its update module is no longer installed by the TV activity.

## Branding
- Modern Android adaptive icon now uses AniRu artwork.
- Remaining TV badge/splash references were switched away from the old AniLiberty foreground asset.

## CI
GitHub Actions now:
1. runs AnimeVost SDK unit tests (including the 1,200-episode playlist regression test),
2. runs Android lint as diagnostics,
3. builds the App Debug TV APK,
4. uploads it as `AniRu-TV-v1.1.0-debug`.

## Important limitation
AniRu can only play streams that the selected upstream provider currently makes available. The player now retries and falls back more intelligently, but an unavailable upstream video cannot be reconstructed by the app.
