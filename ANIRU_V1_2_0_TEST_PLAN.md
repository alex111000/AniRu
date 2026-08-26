# AniRu v1.2.0 — Release test plan

## CI gate
The GitHub Actions build must pass, in order:
1. `:animevost-sdk:testDebugUnitTest`
2. `:app-tv:testAppDebugUnitTest`
3. `:app-tv:lintAppDebug`
4. `:app-tv:assembleAppDebug`
5. Upload artifact `AniRu-TV-v1.2.0-debug`

Do not treat the APK as validated if any of the first four steps is red.

## Smoke test on Android TV
- Launch over the existing installation and confirm local favorites/history remain.
- Open all sidebar pages: `Главное`, `От AnimeVost`, `От AniLibria`, `Я смотрю`.
- Search for `One Piece`: verify separate result rows from all providers that return a match.
- Open AnimeVost One Piece and verify access to high episode numbers / range navigation.
- Open a YummyAnime result: details -> episode -> dubbing/source -> playback.
- Open a SameBand result: details -> episode -> playback.
- In provider playback test quality/source cycling, previous/next episode, episode list, speed, resize mode and resume after exit.
- Force a bad source (when available) and confirm AniRu advances to another stream instead of remaining black indefinitely.
- Add/remove favorites from AniLibria, AnimeVost and a generic provider; verify `Я смотрю -> Избранное`.
- Watch several minutes of a generic provider, exit, reopen and confirm resume position.
- Confirm Android TV global search opens the correct provider detail screen.

## Regression checks
- AniLibria details/player still work unchanged.
- AnimeVost details/favorites/player and episode picker still work.
- Resize modes: `По размеру`, `Заполнить экран`, `Растянуть`.
- No login popup at startup or when using local favorites.
- No legacy AniLibria updater prompt.
