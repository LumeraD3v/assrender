# assrender

ASS/SSA subtitle renderer for Android Media3/ExoPlayer.

**The problem:** ExoPlayer's built-in ASS/SSA support strips all styling (fonts, colors, positioning, animations). Subtitles render as plain text.

**The solution:** Intercept raw ASS data from ExoPlayer's extraction pipeline and render it with libass, preserving full styling. No FFmpeg needed — works with any source ExoPlayer supports (HTTP, HLS, DASH, local files, torrents).

## Architecture

```
MKV/MP4 Container
    |
    v
AssMatroskaExtractor (extends MatroskaExtractor)
    |--- extracts font attachments --> AssHandler.onFontAttachment() --> libass
    |--- wraps ExtractorOutput via reflection
    v
AssTrackOutput (eavesdrops on raw ASS data)
    |--- captures ASS headers from initializationData
    |--- captures dialogue events from sampleData
    |--- forwards everything to ExoPlayer unchanged
    v
AssHandler (coordinates libass rendering)
    |--- stores events per track for instant track switching
    |--- syncs with ExoPlayer's track selection
    |--- 30fps render loop using player.currentPosition
    v
SubtitleOverlayView (transparent bitmap canvas on top of video)
```

## Usage

```kotlin
val assHandler = AssHandler(SubtitleOverlayView(context))
assHandler.player = player

val player = ExoPlayer.Builder(context, AssRenderersFactory(context, assHandler))
    .setMediaSourceFactory(
        DefaultMediaSourceFactory(dataSourceFactory, AssExtractorsFactory(assHandler))
    )
    .build()
```

## Features

- Full ASS/SSA styling: fonts, colors, positioning, animations, karaoke
- Embedded font extraction from MKV containers
- Instant track switching with event replay
- No FFmpeg dependency for playback
- Works with any ExoPlayer source (HTTP, HLS, local, torrent)
- Minimal integration: two factory swaps

## Target Architectures

- `arm64-v8a` — modern Android TV, phones
- `armeabi-v7a` — budget Android TV sticks

## Building Native Libraries

Only needed for development. The AAR includes prebuilt native libraries.

Requires Android NDK + WSL (on Windows).

```bash
export ANDROID_NDK_HOME=~/android-ndk-r27c
./scripts/build-libass.sh arm64
./scripts/build-libass.sh arm
```

## License

Apache License 2.0
