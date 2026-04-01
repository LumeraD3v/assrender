package io.github.assrender

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.NoSampleRenderer

/**
 * A no-sample renderer that syncs ExoPlayer's playback time
 * to [AssHandler] for subtitle rendering. Does not consume any track.
 */
@UnstableApi
class AssTimeRenderer(
    private val handler: AssHandler
) : NoSampleRenderer() {

    override fun getName(): String = "AssTimeRenderer"

    override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
        handler.currentTimeUs = positionUs
    }

    override fun isReady(): Boolean = true

    override fun isEnded(): Boolean = false

    override fun onDisabled() {
        super.onDisabled()
        // Don't release handler here — ExoPlayer disables/re-enables renderers
        // during track switching. Handler lifecycle is managed by ExoPlayerBackend.
    }
}
