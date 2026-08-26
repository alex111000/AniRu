# AniRu v1.0.4

## Player compatibility

- Added Media3 playback error handling for AnimeVost.
- AnimeVost now automatically falls back to the next available source/quality if the first source cannot start.
- If available, a separate download URL is also tried as a progressive playback fallback.
- Video sources are refreshed once after all current candidates fail, which helps with expired/rotated URLs.
- Added a 12-second startup watchdog so sources that hang forever without reaching READY are skipped automatically.
- Shows a visible message when no AnimeVost source can be played instead of leaving a black player with `-- / --`.

## Video resize modes

Added a new aspect-ratio action to the TV player. Each click cycles between:

1. `По размеру` — preserve the full image and aspect ratio.
2. `Заполнить экран` — preserve aspect ratio and zoom/crop to fill the display.
3. `Растянуть` — stretch the video to the full display dimensions.

The resize action is implemented in the shared TV player, so it is available to both AniLibria and AnimeVost playback.

## Small fix

- Fixed the Speed action to use its own `player_action_speed` id instead of the quality action id.
