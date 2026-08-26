# AniRu v1.2.1 — AnimeVost runtime catalog fix

This update targets the TV runtime issue where AnimeVost rows showed loading and then no content.

Changes:
- Default date-descending AnimeVost catalog pages are fetched directly with GET, without depending on the DLE sort POST.
- Explicit non-default sorting still uses the DLE sort/cookie POST, but the actual catalog HTML is always fetched separately with GET.
- Adds Referer on AnimeVost catalog fetch/sort requests.
- Home curated rows (popular/rating/discussed) are ranked locally from a cached recent pool, avoiding several simultaneous DLE sort requests.
- Adds a resilient H1/H2 catalog-title parser fallback if the DLE wrapper class changes.
- Preserves the v1.2 playlist, long-series, player, history and unified-search work.
- Bumps TV version to 1.2.1 / versionCode 13.
- GitHub Actions Gradle tasks use the low-worker JVM settings that were already validated in Codespaces.
