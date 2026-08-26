# AniRu v1.2.3 — AnimeVost cumulative runtime + visual fixes

This archive is cumulative for users already on AniRu v1.2.x and includes the v1.2.1 runtime catalog repair, the v1.2.2 visual work, and the new schedule-poster resolution fix.

## Fixed

- AnimeVost catalog loading remains on the stable runtime path introduced in v1.2.1.
- Curated rows (`Новые серии`, `Популярные`, `Лучшие`, `Обсуждают`) use a larger recent catalog pool for better coverage.
- `Расписание AnimeVost` now resolves real posters in three stages:
  1. poster embedded in schedule markup, when available;
  2. poster from the cached/recent AnimeVost catalog by URL/title;
  3. lightweight AnimeVost details-page lookup for only the titles still missing artwork.
- Details-page poster lookup does **not** call the playlist endpoint, so artwork resolution does not trigger large-series playlist loading.
- Missing poster lookups are de-duplicated, cached, and limited to 3 parallel requests.
- If AnimeVost still provides no usable poster or a request fails, the existing AniRu logo remains the fallback image.
- Schedule model/parser can now preserve poster artwork when a mirror/theme includes an image in the schedule markup.
- Added schedule parser coverage for embedded poster URLs.
- Version updated to 1.2.3 / versionCode 15.
- GitHub Actions artifact updated to `AniRu-TV-v1.2.3-debug`.

## Intentionally unchanged

- Catalog/navigation category cards without real artwork continue to use the AniRu logo fallback.
- AnimeVost full-playlist handling, player fallback, history, favorites and unified search are preserved.
