# assrender

Bulletproof ASS/SSA subtitle renderer for Android Media3/ExoPlayer.

**The problem:** ExoPlayer's built-in ASS/SSA support strips all styling. Existing libass wrappers rely on ExoPlayer's broken subtitle demuxer, causing crashes and rendering glitches.

**The solution:** Use FFmpeg for subtitle extraction and libass for rendering. ExoPlayer's subtitle pipeline is never touched.

## Architecture

```
ExoPlayer (video + audio only)
         │ playback clock
         ▼
AssSubtitleRenderer (Media3 Renderer)
         │ JNI
         ▼
FFmpeg (demux subs) → libass (render to bitmap)
         │
         ▼
SubtitleOverlayView (transparent canvas on top of video)
```

## Usage

```kotlin
val overlayView = findViewById<SubtitleOverlayView>(R.id.subtitle_overlay)

val player = ExoPlayer.Builder(context)
    .setRenderersFactory(AssRenderer.buildRenderersFactory(context, overlayView))
    .build()

// Point to the same stream URL — FFmpeg extracts subs independently
AssRenderer.setSubtitleSource(player, videoUrl)
```

## Building Native Libraries

Requires Android NDK. Set `ANDROID_NDK_HOME` environment variable.

```bash
# Build everything (FFmpeg + libass + deps) for both architectures
./scripts/build-all.sh

# Or build for a specific architecture
./scripts/build-all.sh arm64
./scripts/build-all.sh arm
```

## Supported Formats

| Format | Container | Status |
|--------|-----------|--------|
| ASS/SSA | MKV, MP4, AVI | Full styling, fonts, animations |
| SRT | MKV, MP4, standalone | Basic rendering via libass |
| PGS | MKV, MP4 | Planned |
| VobSub | MKV, AVI | Planned |

## Target Architectures

- `arm64-v8a` — modern Android TV, phones
- `armeabi-v7a` — budget Android TV sticks (Amlogic S905W etc.)

## License

Apache License 2.0
