package io.github.assrender

import android.util.Log
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.TrackOutput
import java.io.ByteArrayOutputStream

/**
 * Implements [TrackOutput] directly (not ForwardingTrackOutput) to ensure
 * we capture ALL data before forwarding to the delegate.
 */
@UnstableApi
internal class AssTrackOutput(
    private val delegate: TrackOutput,
    private val handler: AssHandler,
    private val trackId: Int
) : TrackOutput {

    companion object {
        private const val TAG = "assrender"
    }

    private var isAssTrack = false
    private val pendingData = ByteArrayOutputStream()

    override fun format(format: Format) {
        val mimeType = format.sampleMimeType

        isAssTrack = mimeType == MimeTypes.TEXT_SSA || mimeType == "text/x-ssa" || mimeType == "text/x-ass"

        if (!isAssTrack && format.initializationData.isNotEmpty()) {
            for (data in format.initializationData) {
                val preview = String(data, 0, minOf(50, data.size), Charsets.UTF_8)
                if (preview.contains("[Script Info]") || preview.contains("ScriptType:")) {
                    isAssTrack = true
                    break
                }
            }
        }

        if (isAssTrack && format.initializationData.isNotEmpty()) {
            for (data in format.initializationData) {
                val preview = String(data, 0, minOf(50, data.size), Charsets.UTF_8)
                if (preview.contains("[Script Info]") || preview.contains("ScriptType:")) {
                    handler.onTrackHeader(trackId, data, format)
                    break
                }
            }
        }

        Log.d(TAG, "Track[$trackId] format: mime=$mimeType, isAss=$isAssTrack")
        delegate.format(format)
    }

    override fun sampleData(
        data: ParsableByteArray,
        length: Int,
        sampleDataPart: Int
    ) {
        if (isAssTrack) {
            val pos = data.position
            val bytes = ByteArray(length)
            data.readBytes(bytes, 0, length)
            pendingData.write(bytes)
            data.setPosition(pos)
        }
        delegate.sampleData(data, length, sampleDataPart)
    }

    override fun sampleData(
        input: DataReader,
        length: Int,
        allowEndOfInput: Boolean,
        sampleDataPart: Int
    ): Int {
        if (isAssTrack) {
            val bytes = ByteArray(length)
            val bytesRead = input.read(bytes, 0, length)
            if (bytesRead > 0) {
                pendingData.write(bytes, 0, bytesRead)
                // Forward the data we already read via ParsableByteArray
                val pba = ParsableByteArray(bytes, bytesRead)
                delegate.sampleData(pba, bytesRead, sampleDataPart)
            }
            return bytesRead
        }
        return delegate.sampleData(input, length, allowEndOfInput, sampleDataPart)
    }

    override fun sampleMetadata(
        timeUs: Long,
        flags: Int,
        size: Int,
        offset: Int,
        cryptoData: TrackOutput.CryptoData?
    ) {
        if (isAssTrack && pendingData.size() > 0) {
            handler.onSubtitleSample(trackId, timeUs, pendingData.toByteArray())
            pendingData.reset()
        }
        delegate.sampleMetadata(timeUs, flags, size, offset, cryptoData)
    }
}
