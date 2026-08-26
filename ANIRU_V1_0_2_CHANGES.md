# AniRu v1.0.2

This update includes the v1.0.1 AnimeVost navigation fix and menu reorganization, plus local no-login favorites.

## Login removal
- AniRu no longer opens AniLibria login automatically at startup.
- `Добавить в избранное` no longer opens the AniLibria authentication dialog.
- The hidden profile sign-in action is disabled.

## Local favorites
- AniLibria favorites are stored locally on the Android TV device using SharedPreferences.
- The details screen immediately switches between `Добавить в избранное` and `Убрать из избранного`.
- `Я смотрю` now contains an `Избранное` row.
- Favorites survive app updates, but are removed if app data is cleared or the app is uninstalled.

## Included from v1.0.1
- Fixed AnimeVost side-menu crash by using a RowsSupportFragment-compatible page.
- Side menu: `Главное`, `От AnimeVost`, `От AniLibria`, `Я смотрю`.
- Removed `Профиль` from the side menu.
- Added local AnimeVost viewing history.
