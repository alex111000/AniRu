# AniRu v1.1.0 TV test checklist

Run this once after installing the v1.1.0 APK over the previous AniRu build.

## 1. Launch and menu
- App launches without login dialog.
- Side menu contains: `Главное`, `От AnimeVost`, `От AniLibria`, `Я смотрю`.
- No `Профиль` entry.

## 2. Unified search
- Search `one piece`.
- Confirm separate `AniLibria` and `AnimeVost` rows appear when each provider has matches.
- Open one result from each row.
- If Android TV global/voice search is available on the device, test the same query there.

## 3. One Piece / long AnimeVost series
- Open the AnimeVost One Piece result.
- Confirm details load and show the total episode count when supplied by the full playlist.
- Press `Смотреть`.
- Confirm large-series ranges appear (for example 1–100, 101–200, etc.).
- Open an early, middle and late range and select an episode.
- Confirm `Последняя серия` opens the final available episode.

## 4. AnimeVost player
- Confirm video starts.
- Test previous / next episode.
- Test the episode-list button from inside the player.
- Test quality/source cycling.
- Test speed cycling and reopen the player to confirm the speed is remembered.
- Test resize modes: fit -> fill -> stretch -> fit and reopen to confirm the mode is remembered.
- Exit during an episode, reopen it and confirm playback resumes near the saved position.
- If one source fails, confirm AniRu tries another instead of staying indefinitely on a black player.

## 5. Watched episode state
- Start several episodes of one AnimeVost series.
- Finish at least one episode.
- Reopen `Смотреть` and confirm started/completed progress is shown beside the relevant episodes.

## 6. Favorites and history
- Add one AniLibria title and one AnimeVost title to favorites without login.
- Open `Я смотрю` and confirm `Избранное` contains both.
- Confirm `Продолжить просмотр` can contain both providers.
- Confirm AnimeVost history remembers the latest episode of each title.

## 7. AnimeVost home
- Test `Новые серии`, `Популярное`, `Лучшее по рейтингу`, `Обсуждают`.
- Scroll the AnimeVost catalog/navigation row and open a genre/type/year.
- Open at least one item from `Расписание AnimeVost`.

## 8. Top catalog button
- Press `Каталог` from the main title bar.
- Confirm source choice appears.
- Open AniLibria catalog and test its existing filters.
- Open AnimeVost catalog and browse several pages.

## 9. AniLibria regression
- Open an AniLibria title.
- Select an episode, play it, change quality, use episode list and favorites.
- Confirm existing AniLibria functionality still works.

## 10. Persistence
- Close AniRu fully and reopen it.
- Confirm local favorites, AnimeVost episode progress, playback speed and resize preference remain.
