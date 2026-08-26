# AniRu v1.2.2 — AnimeVost runtime catalog fix

This cumulative overlay contains the v1.2.1 AnimeVost runtime catalog repair plus the compile hotfix required by CI.

## Fixes
- Imports `CatalogSort` in `AnimeVostClient.kt` (fixes `Unresolved reference 'CatalogSort'`).
- Default/date catalog pages are fetched with GET rather than relying on the DLE sort POST response.
- Non-default DLE sorting keeps the sort POST only to establish cookies, then fetches the catalog page with GET.
- TV curated rows can rank a recent AnimeVost pool locally, reducing simultaneous DLE sort-cookie requests.
- Anime list parser includes fallback selectors for AnimeVost markup variations.
- Preserves playlist, long-series, player, history and unified-search work.
- Version: 1.2.2, versionCode 14.
