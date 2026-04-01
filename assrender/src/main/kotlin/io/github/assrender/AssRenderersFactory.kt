package io.github.assrender

import android.content.Context
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.text.TextRenderer

/**
 * A [DefaultRenderersFactory] that:
 * 1. Enables legacy decoding on text renderers so raw text/x-ssa data doesn't crash
 * 2. Adds [AssTimeRenderer] to sync playback time to [AssHandler]
 */
@UnstableApi
class AssRenderersFactory(
    context: Context,
    private val handler: AssHandler
) : DefaultRenderersFactory(context) {

    override fun buildTextRenderers(
        context: Context,
        output: androidx.media3.exoplayer.text.TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>
    ) {
        super.buildTextRenderers(context, output, outputLooper, extensionRendererMode, out)

        // Enable legacy decoding on all text renderers so they accept raw text/x-ssa
        for (renderer in out) {
            if (renderer is TextRenderer) {
                renderer.experimentalSetLegacyDecodingEnabled(true)
            }
        }
    }

    override fun createRenderers(
        eventHandler: android.os.Handler,
        videoRendererEventListener: androidx.media3.exoplayer.video.VideoRendererEventListener,
        audioRendererEventListener: androidx.media3.exoplayer.audio.AudioRendererEventListener,
        textRendererOutput: androidx.media3.exoplayer.text.TextOutput,
        metadataRendererOutput: androidx.media3.exoplayer.metadata.MetadataOutput
    ): Array<Renderer> {
        val renderers = super.createRenderers(
            eventHandler, videoRendererEventListener, audioRendererEventListener,
            textRendererOutput, metadataRendererOutput
        )
        return renderers + AssTimeRenderer(handler)
    }
}
