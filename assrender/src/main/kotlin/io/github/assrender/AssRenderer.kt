package io.github.assrender

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory

/**
 * Public API entry point for assrender.
 *
 * Usage:
 * ```kotlin
 * val overlayView = findViewById<SubtitleOverlayView>(R.id.subtitle_overlay)
 *
 * val player = ExoPlayer.Builder(context)
 *     .setRenderersFactory(AssRenderer.buildRenderersFactory(context, overlayView))
 *     .build()
 *
 * // Set subtitle source (same URL as the video)
 * AssRenderer.setSubtitleSource(player, videoUrl)
 * ```
 */
object AssRenderer {

    /**
     * Creates a [RenderersFactory] that includes the ASS subtitle renderer
     * alongside ExoPlayer's default renderers.
     */
    @OptIn(UnstableApi::class)
    fun buildRenderersFactory(
        context: android.content.Context,
        overlayView: SubtitleOverlayView,
        config: AssRenderConfig = AssRenderConfig()
    ): RenderersFactory {
        val defaultFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context)
        return RenderersFactory { handler, videoListener, audioListener, textOutput, metadataOutput ->
            val defaultRenderers = defaultFactory.createRenderers(
                handler, videoListener, audioListener, textOutput, metadataOutput
            )
            val assRenderer = AssSubtitleRenderer(overlayView, config)
            defaultRenderers + assRenderer
        }
    }

    /**
     * Find the [AssSubtitleRenderer] attached to a player and set the subtitle source.
     */
    @OptIn(UnstableApi::class)
    fun setSubtitleSource(player: ExoPlayer, url: String, trackIndex: Int = 0) {
        findRenderer(player)?.setSubtitleSource(url, trackIndex)
    }

    /**
     * Load an external subtitle file.
     */
    @OptIn(UnstableApi::class)
    fun loadExternalSubtitle(player: ExoPlayer, path: String) {
        findRenderer(player)?.loadExternalSubtitle(path)
    }

    /**
     * Get available subtitle tracks from the stream.
     */
    @OptIn(UnstableApi::class)
    fun getSubtitleTracks(player: ExoPlayer): List<SubtitleTrack> {
        return findRenderer(player)?.getSubtitleTracks() ?: emptyList()
    }

    /**
     * Switch to a different subtitle track.
     */
    @OptIn(UnstableApi::class)
    fun selectTrack(player: ExoPlayer, trackIndex: Int) {
        findRenderer(player)?.selectTrack(trackIndex)
    }

    @OptIn(UnstableApi::class)
    private fun findRenderer(player: ExoPlayer): AssSubtitleRenderer? {
        for (i in 0 until player.rendererCount) {
            val renderer = player.getRenderer(i)
            if (renderer is AssSubtitleRenderer) return renderer
        }
        return null
    }
}
