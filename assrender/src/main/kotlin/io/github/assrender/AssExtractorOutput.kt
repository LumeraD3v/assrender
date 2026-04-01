package io.github.assrender

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.TrackOutput

/**
 * Wraps [ExtractorOutput] to intercept subtitle tracks with [AssTrackOutput].
 * Non-subtitle tracks pass through unchanged.
 */
@UnstableApi
internal class AssExtractorOutput(
    private val delegate: ExtractorOutput,
    private val handler: AssHandler
) : ExtractorOutput by delegate {

    private var subtitleTrackCount = 0

    override fun track(id: Int, type: Int): TrackOutput {
        val trackOutput = delegate.track(id, type)

        return if (type == C.TRACK_TYPE_TEXT) {
            val trackId = subtitleTrackCount++
            AssTrackOutput(trackOutput, handler, trackId)
        } else {
            trackOutput
        }
    }
}
