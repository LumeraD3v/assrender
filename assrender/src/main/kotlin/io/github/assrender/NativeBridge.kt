package io.github.assrender

import android.graphics.Bitmap
import androidx.annotation.Keep

/**
 * JNI bridge to the native FFmpeg + libass pipeline.
 * All methods are internal — public API is through [AssRenderer].
 */
@Keep
internal object NativeBridge {

    init {
        System.loadLibrary("assrender")
    }

    /**
     * Initialize the native pipeline.
     * @return handle (pointer) to the native context, or 0 on failure.
     */
    external fun nativeInit(width: Int, height: Int, fontScale: Float): Long

    /**
     * Open a media stream and scan for subtitle tracks.
     * @return number of subtitle tracks found, or -1 on error.
     */
    external fun nativeOpenStream(handle: Long, url: String): Int

    /**
     * Get metadata for a subtitle track.
     * @return array of [index, codecName, language, title, isDefault(0/1)]
     */
    external fun nativeGetTrackInfo(handle: Long, trackIndex: Int): Array<String>?

    /**
     * Select a subtitle track for rendering.
     * @return true on success.
     */
    external fun nativeSelectTrack(handle: Long, trackIndex: Int): Boolean

    /**
     * Load an external subtitle file (ASS/SSA/SRT).
     * @return true on success.
     */
    external fun nativeLoadExternalSubtitle(handle: Long, path: String): Boolean

    /**
     * Render the subtitle frame at the given playback time.
     * @param timeMs playback position in milliseconds.
     * @param bitmap pre-allocated ARGB_8888 Bitmap to render into.
     * @return true if a subtitle was rendered, false if blank at this time.
     */
    external fun nativeRender(handle: Long, timeMs: Long, bitmap: Bitmap): Boolean

    /**
     * Notify the pipeline of a seek event.
     */
    external fun nativeSeek(handle: Long, timeMs: Long)

    /**
     * Release all native resources.
     */
    external fun nativeDestroy(handle: Long)
}
