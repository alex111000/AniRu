# AniRu TV 2.0.0-beta1

## Included

- Main: Continue watching and Favorites. Movies and Series are shared poster grids.
- Cold launch no longer waits for the legacy AniLibria configuration endpoint. Local library and other providers remain reachable when that endpoint is offline.
- Global anime search. Genre/year filters. Default order: added date descending, then year descending.
- Grid pagination appears only when more indexed items exist; selecting it reveals and scrolls to the next results instead of leaving an inactive button on screen.
- Conservative deduplication: known external identifiers, or exact alternate names + matching year/type. Unknown metadata is not enough to merge. Different seasons, remakes and films stay separate.
- Eight native provider adapters: AniLibria/AniLiberty, AnimeVost, YummyAnime, SameBand, AnimeLib, AnimeGo, DreamersCast, HDRezka (anime only).
- Sequential paged catalog synchronization and on-disk metadata cache. Partial results stay usable when a provider fails. Source-reported addition dates are preferred; otherwise first indexing time is used, not release year masquerading as an addition date.
- Opening title details can use another already-matched provider copy when the primary is unavailable; this lookup has an eight-second deadline and cancels unused requests.
- Bounded playback resolution (6 seconds maximum, 450ms additional comparison window after a suitable result). This is not a guarantee of first frame within six seconds: media buffering is separate and has an 8-second fallback watchdog.
- Source, dubbing, quality and episode menus. Highest supported adaptive video track by default, retained dubbing, same-episode fallback, no silent cross-dub fallback.
- Legacy favorites/history remain intact. The unified view reads them and keeps explicit overrides for new favorite choices. Existing per-episode positions are read where episode identity is known.
- The CI debug signing path is explicit and the generated key is cached for subsequent builds. Cache eviction can still lose it; a permanently configured release-signing key is required for durable production update identity. This cannot recreate keys used by older, uncached CI builds.

## Important limitations / device validation

This is a beta for on-device testing, not a claim that eight third-party services have passed live end-to-end playback tests in every country.

- A source may require authorization, block a country, change HTML or be unavailable. The application does not bypass authentication, paywalls, anti-bot challenges or geographic restrictions. AnimeLib's authorized-only player cannot be used without the service granting access; public players are tried.
- The first index is incremental. All providers need not finish before results appear. A missing type stays out of Movies/Series until identified; search can still show it. SameBand and HTML sources follow pagination links actually supplied by the site, rather than claiming an unsupported full-catalog API.
- Conservative matching may leave duplicates when a provider omits year/type or uses an unrecognized alias. This is safer than playing the wrong title.
- Manual stream changes preserve the requested playback timestamp. Different cuts can have different timelines; exact synchronization across cuts is not guaranteed.
- Automatic quality depends on the actual stream and codecs supported by the device, not just the resolution advertised by a provider.
- Older APKs may have a different debug signature. Do not uninstall an old installation with important local data simply to bypass an update-signature error.

## Verification

The CI pipeline runs AnimeVost SDK tests, TV app unit tests, lint, APK assembly and an offline emulator UI smoke test. New tests cover canonical identity, sorting, season/special matching, short playback races, deadlines, cancellation and native adapter response fixtures. The emulator uses labelled synthetic catalog entries only to test cached grid rendering and navigation; it does not prove live source availability. Live playback and migration still need confirmation on the target Android TV/phone.

Provider parsers are native adaptations informed by vypivshiy/anicli-api (MIT). See THIRD_PARTY_NOTICES.md and the bundled license asset.
