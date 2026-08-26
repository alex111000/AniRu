# AniRu TV

AniRu is a personal Android TV fork based on the AniLiberty TV application with an additional
AnimeVost content source.

## Included in this build

- Existing AniLiberty TV catalog and UI.
- AnimeVost as a separate item in the left-side TV menu.
- AnimeVost "new episodes" row on the AniRu home screen.
- AnimeVost paginated catalog.
- AnimeVost details screen using the AniLiberty TV visual language.
- Episode list.
- Media3 / ExoPlayer playback using AnimeVost `getVideoSources(videoId)`.
- Quality cycling when more than one AnimeVost source is available.
- Previous / next episode support.
- Separate Android application id: `com.aniru.tv`.
- Upstream AniLiberty update prompts disabled so a personal AniRu install is not redirected to
  AniLiberty releases.

## Build an APK with GitHub Actions

1. Create a private GitHub repository.
2. Upload the contents of this project to the repository.
3. Open **Actions**.
4. Run **Build AniRu TV APK**.
5. Download the `AniRu-TV-debug` artifact.
6. Inside the artifact is the installable debug APK.

The workflow builds:

`./gradlew :app-tv:assembleAppDebug`

## Local Android Studio build

Open the project in Android Studio with Android SDK 36 installed and build the `appDebug`
variant of the `app-tv` module.

## Notes

AnimeVost is integrated as the local Gradle module `:animevost-sdk`.
Its networking/parsing code is compiled into AniRu rather than being fetched at runtime.
