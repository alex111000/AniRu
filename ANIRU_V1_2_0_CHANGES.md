# AniRu v1.2.0 — Multi Provider Engine

## What changed

AniRu 1.2 turns the TV app into a multi-provider client while keeping the mature native AniLibria and AnimeVost flows.

### Providers
- AniLibria — native AniRu/AniLibria details and player flow.
- AnimeVost — full playlist API + legacy fallback, local favorites/history and resilient playback.
- YummyAnime — native Kotlin provider using the public catalog/search/video API and multi-dub sources.
- SameBand — native Kotlin provider using search/catalog pages and PlayerJS playlist streams.

### Unified search
- TV search now shows separate rows for AniLibria, AnimeVost, YummyAnime and SameBand.
- Android TV global suggestions can route provider results back into AniRu.
- Global-search network work has a hard timeout so the Android search provider cannot wait indefinitely.

### Generic provider engine
- Stable provider IDs prevent collisions between identical numeric IDs from different services.
- Common models for anime, details, episodes, sources and streams.
- Provider health checks, bounded network timeouts, retry for transient 5xx responses and HTTP cache.
- Conservative title matching for automatic playback failover.

### Playback
- Generic provider player supports MP4, HLS and DASH.
- DASH module is included explicitly in Media3.
- Provider-specific Referer/User-Agent headers are preserved for segment requests.
- Quality/source cycling and 12-second startup watchdog.
- Automatic fallback to the next quality/source and then another compatible provider when safe.
- Playback speed and resize mode reuse the existing AniRu player preferences.
- Progress is saved every 10 seconds and on pause/exit.

### Episodes and long series
- Provider episode picker supports large series using 100-episode ranges.
- Continue, last episode, watched/progress status and current episode highlighting.
- AnimeVost retains the full-playlist fix for very long shows such as One Piece.

### Local library
- Favorites from generic providers are stored locally.
- Provider history and continue-watching are merged into `Я смотрю`.
- Existing AniLibria and AnimeVost local data remains separate and compatible.

### Reliability / release hardening
- Fixed Media3 `UnstableApi` lint failure in AnimeVost player.
- Added explicit Media3 DASH dependency.
- YummyAnime and SameBand details degrade gracefully if episode/player endpoints are temporarily unavailable.
- SameBand playback sends both Referer and AniRu User-Agent.
- Automatic cross-provider fallback now requires a high title-match score to reduce wrong-series playback.
- `allowBackup=false` for local library/privacy data.
- Deprecated Kotlin `capitalize()` usage removed from TV/mobile build scripts.
- GitHub Actions uses checkout/setup-java/upload-artifact v5 and treats Android lint errors as build failures.
- Provider-engine unit checks added before lint/build.

## Version
- `versionCode = 12`
- `versionName = 1.2.0`
- Artifact: `AniRu-TV-v1.2.0-debug`

## Important runtime note
External provider websites and CDNs can change independently of AniRu. The provider engine is designed to fail closed, try alternatives where safe, and avoid feeding iframe pages directly to Media3 as video streams.
