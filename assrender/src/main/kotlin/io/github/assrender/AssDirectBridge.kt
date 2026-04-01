package io.github.assrender

import android.graphics.Bitmap
import androidx.annotation.Keep

/**
 * JNI bridge for the direct libass pipeline (no FFmpeg).
 * Subtitle data is fed directly from ExoPlayer's extraction.
 */
@Keep
object AssDirectBridge {

    init {
        System.loadLibrary("assrender")
    }

    /** Initialize libass context. Returns handle or 0 on failure. */
    external fun nativeInit(width: Int, height: Int, fontScale: Float): Long

    /** Load ASS header (codec private data from Format.initializationData). */
    external fun nativeLoadHeader(handle: Long, headerData: ByteArray): Int

    /** Add a font attachment. */
    external fun nativeAddFont(handle: Long, name: String?, fontData: ByteArray)

    /** Process a subtitle chunk (dialogue line with timing). */
    external fun nativeProcessChunk(handle: Long, data: ByteArray, startMs: Long, durationMs: Long)

    /** Process raw ASS data (full dialogue lines with embedded timing). */
    external fun nativeProcessData(handle: Long, data: ByteArray)

    /** Render subtitle frame at given time. Returns true if content rendered. */
    external fun nativeRender(handle: Long, timeMs: Long, bitmap: Bitmap): Boolean

    /** Flush all events (on seek or track change). */
    external fun nativeFlush(handle: Long)

    /** Destroy context and free resources. */
    external fun nativeDestroy(handle: Long)
}
