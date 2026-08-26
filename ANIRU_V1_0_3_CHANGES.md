# AniRu v1.0.3

Fixes for AnimeVost TV integration:

- Fixed AnimeVost home rows that could remain on loading forever.
  - New episodes still uses the normal date catalog.
  - Popular, best rated and discussed first try AnimeVost's real server-side sort using independent clients.
  - If a sort request does not answer within 4 seconds, AniRu falls back to a cached recent AnimeVost pool and sorts locally by views, rating or comments.
  - Added a 15 second general network timeout so a stalled request does not block the UI indefinitely.
- Added local AnimeVost favorites.
  - AnimeVost details now shows Add to favorites / Remove from favorites.
  - No login is required.
  - AnimeVost favorites are included in Я смотрю -> Избранное together with local AniLibria favorites.
- Смотреть on AnimeVost now opens an episode chooser instead of auto-playing the first episode.
- The Episodes button in the AnimeVost player now opens the full episode chooser and highlights the current episode.
- Choosing an episode from inside the player replaces the current player with the selected episode.
- Version bumped to 1.0.3 / versionCode 3.
