package io.github.assrender

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.BaseRenderer
import androidx.media3.exoplayer.RendererCapabilities

/**
 * A Media3 Renderer that renders ASS/SSA subtitles using the native
 * FFmpeg + libass pipeline. This renderer does NOT use ExoPlayer's
 * subtitle extraction — it independently extracts subtitles via FFmpeg.
 */
@UnstableApi
class AssSubtitleRenderer(
    private val overlayView: SubtitleOverlayView,
    private val config: AssRenderConfig = AssRenderConfig()
) : BaseRenderer(C.TRACK_TYPE_NONE) {

    private var nativeHandle: Long = 0L
    private var renderBitmap: Bitmap? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var streamUrl: String? = null
    private var isRendering = false

    fun setSubtitleSource(url: String, trackIndex: Int = 0) {
        streamUrl = url
        if (nativeHandle != 0L) {
            NativeBridge.nativeOpenStream(nativeHandle, url)
            NativeBridge.nativeSelectTrack(nativeHandle, trackIndex)
            isRendering = true
        }
    }

    fun loadExternalSubtitle(path: String) {
        if (nativeHandle != 0L) {
            NativeBridge.nativeLoadExternalSubtitle(nativeHandle, path)
            isRendering = true
        }
    }

    fun getSubtitleTracks(): List<SubtitleTrack> {
        if (nativeHandle == 0L) return emptyList()
        val url = streamUrl ?: return emptyList()

        val count = NativeBridge.nativeOpenStream(nativeHandle, url)
        if (count <= 0) return emptyList()

        return (0 until count).mapNotNull { i ->
            val info = NativeBridge.nativeGetTrackInfo(nativeHandle, i) ?: return@mapNotNull null
            SubtitleTrack(
                index = info[0].toIntOrNull() ?: i,
                codecName = info[1],
                language = info[2].takeIf { it.isNotEmpty() },
                title = info[3].takeIf { it.isNotEmpty() },
                isDefault = info[4] == "1"
            )
        }
    }

    fun selectTrack(trackIndex: Int) {
        if (nativeHandle != 0L) {
            NativeBridge.nativeSelectTrack(nativeHandle, trackIndex)
        }
    }

    override fun getName(): String = "AssSubtitleRenderer"

    @UnstableApi
    override fun supportsFormat(format: Format): Int {
        return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
    }

    override fun onEnabled(joining: Boolean, mayRenderStartOfStream: Boolean) {
        super.onEnabled(joining, mayRenderStartOfStream)
        nativeHandle = NativeBridge.nativeInit(
            config.videoWidth,
            config.videoHeight,
            config.fontScale
        )
        renderBitmap = Bitmap.createBitmap(
            config.videoWidth,
            config.videoHeight,
            Bitmap.Config.ARGB_8888
        )

        val url = streamUrl
        if (url != null && nativeHandle != 0L) {
            NativeBridge.nativeOpenStream(nativeHandle, url)
            NativeBridge.nativeSelectTrack(nativeHandle, 0)
            isRendering = true
        }
    }

    override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
        if (!isRendering || nativeHandle == 0L) return
        val bitmap = renderBitmap ?: return

        val positionMs = positionUs / 1000
        val hasContent = NativeBridge.nativeRender(nativeHandle, positionMs, bitmap)

        if (hasContent) {
            mainHandler.post { overlayView.updateBitmap(bitmap) }
        } else {
            mainHandler.post { overlayView.clear() }
        }
    }

    override fun isReady(): Boolean = true

    override fun isEnded(): Boolean = false

    override fun onPositionReset(positionUs: Long, joining: Boolean) {
        super.onPositionReset(positionUs, joining)
        if (nativeHandle != 0L) {
            NativeBridge.nativeSeek(nativeHandle, positionUs / 1000)
        }
        mainHandler.post { overlayView.clear() }
    }

    override fun onDisabled() {
        super.onDisabled()
        isRendering = false
        if (nativeHandle != 0L) {
            NativeBridge.nativeDestroy(nativeHandle)
            nativeHandle = 0L
        }
        renderBitmap?.recycle()
        renderBitmap = null
        mainHandler.post { overlayView.clear() }
    }
}
