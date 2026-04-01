package io.github.assrender

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.BaseRenderer
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.RendererCapabilities
import java.nio.ByteBuffer

/**
 * A Media3 text Renderer that intercepts raw ASS/SSA subtitle data
 * from ExoPlayer's extraction pipeline and renders it via libass.
 *
 * This replaces ExoPlayer's default text renderer for ASS/SSA formats,
 * providing full styling, fonts, positioning, and animation support.
 */
@UnstableApi
class AssTextRenderer(
    private val overlayView: SubtitleOverlayView,
    private val videoWidth: Int = 1920,
    private val videoHeight: Int = 1080
) : BaseRenderer(C.TRACK_TYPE_TEXT) {

    companion object {
        private const val TAG = "assrender"
        private val ASS_MIME_TYPES = setOf(
            MimeTypes.TEXT_SSA,
            "text/x-ssa",
            "text/x-ass",
        )
    }

    private var nativeHandle: Long = 0L
    private var renderBitmap: Bitmap? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val formatHolder = FormatHolder()
    private var inputBuffer: androidx.media3.decoder.DecoderInputBuffer? = null
    private var headerLoaded = false
    private var lastRenderedTimeUs = Long.MIN_VALUE

    override fun getName(): String = "AssTextRenderer"

    @UnstableApi
    override fun supportsFormat(format: Format): Int {
        val mimeType = format.sampleMimeType ?: return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
        val supported = mimeType in ASS_MIME_TYPES
        return if (supported) {
            RendererCapabilities.create(C.FORMAT_HANDLED)
        } else {
            RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
        }
    }

    override fun onEnabled(joining: Boolean, mayRenderStartOfStream: Boolean) {
        super.onEnabled(joining, mayRenderStartOfStream)
        Log.d(TAG, "AssTextRenderer enabled")

        nativeHandle = AssDirectBridge.nativeInit(videoWidth, videoHeight, 1.0f)
        if (nativeHandle == 0L) {
            Log.e(TAG, "Failed to init native context")
            return
        }

        renderBitmap = Bitmap.createBitmap(videoWidth, videoHeight, Bitmap.Config.ARGB_8888)
        inputBuffer = androidx.media3.decoder.DecoderInputBuffer(
            androidx.media3.decoder.DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL
        )
        headerLoaded = false
    }

    override fun onStreamChanged(formats: Array<out Format>, startPositionUs: Long, offsetUs: Long, mediaPeriodId: androidx.media3.exoplayer.source.MediaSource.MediaPeriodId) {
        super.onStreamChanged(formats, startPositionUs, offsetUs, mediaPeriodId)

        val format = formats.firstOrNull() ?: return

        // Load ASS header from codec private data
        if (format.initializationData.isNotEmpty() && nativeHandle != 0L) {
            val headerBytes = format.initializationData[0]
            Log.d(TAG, "Loaded ASS header: ${headerBytes.size} bytes")
            AssDirectBridge.nativeLoadHeader(nativeHandle, headerBytes)
            headerLoaded = true
        }
    }

    override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
        if (nativeHandle == 0L || !headerLoaded) return

        // Read all available subtitle samples from ExoPlayer
        while (true) {
            val buffer = inputBuffer ?: break
            buffer.clear()

            val result = readSource(formatHolder, buffer, 0)

            if (result == C.RESULT_FORMAT_READ) {
                // Format change — reload header if needed
                val newFormat = formatHolder.format
                if (newFormat != null && newFormat.initializationData.isNotEmpty()) {
                    val headerBytes = newFormat.initializationData[0]
                    AssDirectBridge.nativeLoadHeader(nativeHandle, headerBytes)
                    headerLoaded = true
                }
                continue
            }

            if (result == C.RESULT_NOTHING_READ) break

            if (result == C.RESULT_BUFFER_READ) {
                if (buffer.isEndOfStream) break

                val data = buffer.data ?: continue
                val bytes = ByteArray(data.remaining())
                data.get(bytes)

                val startMs = buffer.timeUs / 1000
                // ExoPlayer may not provide duration for all formats.
                // For ASS in MKV, duration comes from the block duration.
                val durationUs = if (buffer.timeUs != C.TIME_UNSET) {
                    // SubtitleInputBuffer doesn't expose duration directly,
                    // but for ASS the duration is encoded in the dialogue line.
                    // Pass 0 and let libass parse it, or use a large default.
                    0L
                } else {
                    0L
                }

                AssDirectBridge.nativeProcessChunk(nativeHandle, bytes, startMs, durationUs)
            }
        }

        // Render the frame for the current playback position
        val bitmap = renderBitmap ?: return
        val positionMs = positionUs / 1000

        // Only re-render if time has changed enough (avoid redundant renders)
        if (kotlin.math.abs(positionUs - lastRenderedTimeUs) < 10_000) return // < 10ms
        lastRenderedTimeUs = positionUs

        val hasContent = AssDirectBridge.nativeRender(nativeHandle, positionMs, bitmap)

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
        // Don't flush events on seek — we want to keep all events in memory
        // so seeking works instantly. libass handles timestamp lookup internally.
        lastRenderedTimeUs = Long.MIN_VALUE
        mainHandler.post { overlayView.clear() }
    }

    override fun onDisabled() {
        super.onDisabled()
        Log.d(TAG, "AssTextRenderer disabled")
        if (nativeHandle != 0L) {
            AssDirectBridge.nativeDestroy(nativeHandle)
            nativeHandle = 0L
        }
        renderBitmap?.recycle()
        renderBitmap = null
        inputBuffer = null
        headerLoaded = false
        mainHandler.post { overlayView.clear() }
    }
}
