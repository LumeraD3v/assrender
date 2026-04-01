package io.github.assrender

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.Format

/**
 * Coordinates the ASS subtitle pipeline:
 * - Receives raw ASS data from [AssTrackOutput] (header + dialogue chunks)
 * - Feeds data to libass via [AssDirectBridge]
 * - Renders subtitle bitmaps synced to playback time
 * - Manages the [SubtitleOverlayView] display
 */
class AssHandler(
    val overlayView: SubtitleOverlayView,
    private val videoWidth: Int = 1920,
    private val videoHeight: Int = 1080
) {
    /** Called when the first ASS track is detected — use to hide ExoPlayer subtitles */
    var onAssTrackDetected: (() -> Unit)? = null
    companion object {
        private const val TAG = "assrender"
    }

    private var nativeHandle: Long = 0L
    private var renderBitmap: Bitmap? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Track state
    private val trackFormats = mutableMapOf<Int, Format>()
    private val trackHeaders = mutableMapOf<Int, ByteArray>()
    // Triple: startMs, durationMs, chunkData — thread-safe lists
    private val trackEvents = java.util.concurrent.ConcurrentHashMap<Int, java.util.concurrent.CopyOnWriteArrayList<Triple<Long, Long, ByteArray>>>()
    private var activeTrackId: Int = -1
    private var initialized = false

    // Buffer fonts that arrive before initialization
    private val pendingFonts = mutableListOf<Pair<String, ByteArray>>()

    // Playback time — updated by AssTimeRenderer or player reference
    @Volatile
    var currentTimeUs: Long = 0L

    // Direct player reference for accurate position
    var player: androidx.media3.exoplayer.ExoPlayer? = null

    private var lastRenderedMs: Long = Long.MIN_VALUE
    private var renderRunnable: Runnable? = null

    /**
     * Called by [AssTrackOutput] when ASS header (codec private) is available.
     */
    fun onTrackHeader(trackId: Int, headerData: ByteArray, format: Format) {
        trackFormats[trackId] = format
        trackHeaders[trackId] = headerData

        // Initialize on first ASS track
        if (!initialized) {
            nativeHandle = AssDirectBridge.nativeInit(videoWidth, videoHeight, 1.0f)
            if (nativeHandle == 0L) {
                Log.e(TAG, "Failed to init native context")
                return
            }
            renderBitmap = Bitmap.createBitmap(videoWidth, videoHeight, Bitmap.Config.ARGB_8888)
            initialized = true
            // Don't auto-select track 0 — wait for syncAssTrackWithExoPlayer
            // to call selectTrackByFormat with the correct track
            Log.d(TAG, "Initialized (no track selected yet)")
            flushPendingFonts()
            onAssTrackDetected?.invoke()
        }
    }

    /**
     * Called by [AssTrackOutput] for each subtitle dialogue line.
     */
    fun onSubtitleSample(trackId: Int, timeUs: Long, data: ByteArray) {
        if (!initialized || nativeHandle == 0L) return

        val line = String(data, Charsets.UTF_8)
        if (!line.startsWith("Dialogue:")) return

        val content = line.removePrefix("Dialogue:").trimStart()
        val parts = content.split(",", limit = 11)
        if (parts.size < 11) return

        val durationMs = parseAssTimeMs(parts[1].trim())
        val startMs = timeUs / 1000
        val chunkData = "${parts[2].trim()},${parts[3].trim()},${parts[4].trim()},${parts[5].trim()},${parts[6].trim()},${parts[7].trim()},${parts[8].trim()},${parts[9].trim()},${parts[10]}"
        val chunkBytes = chunkData.toByteArray(Charsets.UTF_8)

        // Store event for this track (for replay on track switch)
        trackEvents.getOrPut(trackId) { java.util.concurrent.CopyOnWriteArrayList() }
            .add(Triple(startMs, durationMs, chunkBytes))

        // Only feed to libass if this is the active track
        if (trackId == activeTrackId) {
            AssDirectBridge.nativeProcessChunk(nativeHandle, chunkBytes, startMs, durationMs)
        }
    }

    /**
     * Called by [AssMatroskaExtractor] for font attachments.
     */
    fun onFontAttachment(name: String, data: ByteArray) {
        if (!initialized || nativeHandle == 0L) {
            // Buffer fonts that arrive before libass is initialized
            pendingFonts.add(Pair(name, data))
            return
        }
        AssDirectBridge.nativeAddFont(nativeHandle, name, data)
        Log.d(TAG, "Added font: $name (${data.size} bytes)")
    }

    private fun flushPendingFonts() {
        if (pendingFonts.isEmpty()) return
        for ((name, data) in pendingFonts) {
            AssDirectBridge.nativeAddFont(nativeHandle, name, data)
        }
        Log.d(TAG, "Flushed ${pendingFonts.size} pending fonts")
        pendingFonts.clear()
    }

    /**
     * Switch to a different subtitle track.
     */
    fun selectTrack(trackId: Int) {
        if (!initialized || nativeHandle == 0L) return
        val header = trackHeaders[trackId] ?: return

        // Run on main thread to avoid race with render loop
        mainHandler.post {
            activeTrackId = trackId
            overlayView.clear()

            // Flush and reload header
            AssDirectBridge.nativeFlush(nativeHandle)
            AssDirectBridge.nativeLoadHeader(nativeHandle, header)

            // Replay stored events for this track
            val events = trackEvents[trackId]
            if (events != null) {
                for ((startMs, durationMs, chunkBytes) in events) {
                    AssDirectBridge.nativeProcessChunk(nativeHandle, chunkBytes, startMs, durationMs)
                }
                Log.d(TAG, "Switched to track $trackId, replayed ${events.size} events")
            } else {
                Log.d(TAG, "Switched to track $trackId")
            }

            // Restart render loop (may have been stopped by clearOverlay)
            startRenderLoop()
        }
    }

    /**
     * Select ASS track by matching language/title from ExoPlayer's format.
     */
    fun selectTrackByFormat(format: Format) {
        if (!initialized) return
        val targetLang = format.language?.lowercase() ?: ""
        val targetLabel = format.label?.lowercase() ?: ""

        for ((id, fmt) in trackFormats) {
            val lang = fmt.language?.lowercase() ?: ""
            val label = fmt.label?.lowercase() ?: ""
            if ((targetLabel.isNotEmpty() && label == targetLabel) ||
                (targetLang.isNotEmpty() && lang == targetLang)) {
                selectTrack(id)
                return
            }
        }

        // No matching ASS track — clear overlay
        clearOverlay()
        Log.d(TAG, "No matching ASS track for lang=$targetLang, label=$targetLabel")
    }

    private fun startRenderLoop() {
        renderRunnable?.let { mainHandler.removeCallbacks(it) }

        val runnable = object : Runnable {
            override fun run() {
                renderFrame()
                mainHandler.postDelayed(this, 33) // ~30fps
            }
        }
        renderRunnable = runnable
        mainHandler.post(runnable)
    }

    private fun renderFrame() {
        if (!initialized || nativeHandle == 0L) return
        val bitmap = renderBitmap ?: return

        val positionMs = player?.currentPosition ?: (currentTimeUs / 1000)
        val hasContent = AssDirectBridge.nativeRender(nativeHandle, positionMs, bitmap)

        if (hasContent) {
            overlayView.updateBitmap(bitmap)
        } else {
            overlayView.clear()
        }
    }

    /**
     * Convert MKV ASS dialogue format to standard ASS format.
     * MKV:      "Dialogue: 0:00:00:00,Duration,ReadOrder,Layer,Style,Name,ML,MR,MV,Effect,Text"
     *           Start is always 0, Duration is relative. Real start comes from timeUs.
     * Standard: "Dialogue: Layer,Start,End,Style,Name,ML,MR,MV,Effect,Text"
     */
    private fun convertMkvDialogue(line: String, timeUs: Long): String? {
        if (!line.startsWith("Dialogue:")) return null

        val content = line.removePrefix("Dialogue:").trimStart()
        val parts = content.split(",", limit = 11)
        if (parts.size < 11) return null

        val durationStr = parts[1].trim() // Duration in H:MM:SS:CC format
        val readOrder = parts[2].trim()
        val layer = parts[3].trim()
        val style = parts[4].trim()
        val name = parts[5].trim()
        val marginL = parts[6].trim()
        val marginR = parts[7].trim()
        val marginV = parts[8].trim()
        val effect = parts[9].trim()
        val text = parts[10]

        // Parse duration from the MKV "end" field
        val durationMs = parseAssTimeMs(durationStr)

        // Calculate real start and end from timeUs
        val startMs = timeUs / 1000
        val endMs = startMs + durationMs

        val startStr = formatAssTime(startMs)
        val endStr = formatAssTime(endMs)

        return "Dialogue: $layer,$startStr,$endStr,$style,$name,$marginL,$marginR,$marginV,$effect,$text"
    }

    /** Parse "H:MM:SS:CC" to milliseconds */
    private fun parseAssTimeMs(time: String): Long {
        val parts = time.split(":")
        if (parts.size != 4) return 0
        val h = parts[0].toLongOrNull() ?: 0
        val m = parts[1].toLongOrNull() ?: 0
        val s = parts[2].toLongOrNull() ?: 0
        val cs = parts[3].toLongOrNull() ?: 0
        return h * 3600000 + m * 60000 + s * 1000 + cs * 10
    }

    /** Format milliseconds to "H:MM:SS.CC" */
    private fun formatAssTime(ms: Long): String {
        val totalCs = ms / 10
        val cs = totalCs % 100
        val totalS = totalCs / 100
        val s = totalS % 60
        val totalM = totalS / 60
        val m = totalM % 60
        val h = totalM / 60
        return "%d:%02d:%02d.%02d".format(h, m, s, cs)
    }

    /**
     * Clear the overlay and stop rendering ASS (e.g. when switching to non-ASS track).
     */
    fun clearOverlay() {
        activeTrackId = -1
        if (nativeHandle != 0L) {
            AssDirectBridge.nativeFlush(nativeHandle)
        }
        // Stop render loop and clear overlay
        renderRunnable?.let { mainHandler.removeCallbacks(it) }
        renderRunnable = null
        mainHandler.post { overlayView.clear() }
    }

    /**
     * Release all resources.
     */
    fun release() {
        renderRunnable?.let { mainHandler.removeCallbacks(it) }
        renderRunnable = null

        if (nativeHandle != 0L) {
            AssDirectBridge.nativeDestroy(nativeHandle)
            nativeHandle = 0L
        }
        renderBitmap?.recycle()
        renderBitmap = null
        initialized = false
        trackFormats.clear()
        mainHandler.post { overlayView.clear() }
        Log.d(TAG, "Released")
    }
}
