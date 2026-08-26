# AniRu v1.2.4 — Clean Rebuild

This is a complete source replacement based on the current full AniRu v1.2.3 repository archive supplied by the user. It is not an overlay and does not require v1.2.1, v1.2.2 or v1.2.3 patch archives to be applied first.

## Preserved

- AniRu Android TV branding and application id.
- AniLibria native flow.
- AnimeVost catalog, schedule, details, long playlists, player fallback, favorites and history.
- Unified search.
- Multi-provider engine and the existing YummyAnime / SameBand provider code.
- AniRu logo as the fallback whenever no real poster can be resolved.

## Fixed in 1.2.4

### AnimeVost posters

AnimeVost artwork is no longer limited to a plain `img src` attribute. The SDK now resolves poster URLs from:

- `data-src`
- `data-original`
- `data-lazy-src`
- `data-url`
- `data-image`
- `src`
- `data-srcset`
- `srcset`
- detail-page Open Graph / Twitter image metadata as a final fallback

Common loading/blank placeholder images are rejected so they do not replace the AniRu fallback.

The same resolver is used by AnimeVost catalog, details, favorites, random-title and schedule parsing.

### AnimeVost schedule stability

- Schedule still prefers a real AnimeVost poster whenever one can be found.
- Missing artwork is first matched against the recent catalog by URL/title.
- Remaining detail-page lookups are de-duplicated and limited to 4 in parallel.
- Poster enrichment has a 12-second total budget so a slow mirror cannot keep the entire schedule row loading indefinitely.
- A failed poster lookup is only negatively cached for 60 seconds instead of 30 minutes; successful posters remain cached for 30 minutes.
- Per-detail poster lookup timeout was reduced to 6 seconds.
- If artwork still cannot be resolved, the existing AniRu logo remains the fallback.

### SameBand artwork

SameBand cards/details also accept common lazy-image attributes (`data-src`, `data-original`, `data-lazy-src`) in addition to normal `src`.

### Repository cleanup

Removed stale root APK/ZIP release artifacts and old patch-application instructions from the full source tree. Root APK/ZIP release files are now ignored by Git.

## Version

- `versionCode = 16`
- `versionName = 1.2.4`
- RuStore flavor: `1.2.4-rustore`
- GitHub Actions artifact: `AniRu-TV-v1.2.4-debug`

## Verification

The new shared poster URL resolver was syntax-compiled locally with Kotlin 1.9 using minimal Jsoup stubs. New parser regression tests were added for lazy `data-src`, detail-page `og:image`, and `srcset` poster handling.

A full Android Gradle build cannot be performed in the artifact container because Gradle dependencies/Android SDK downloads are unavailable there. The repository workflow remains configured to run SDK tests, TV provider-engine tests, lint and `assembleAppDebug` on GitHub Actions.
