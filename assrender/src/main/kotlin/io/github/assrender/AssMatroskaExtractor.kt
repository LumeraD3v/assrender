package io.github.assrender

import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.mkv.EbmlProcessor
import androidx.media3.extractor.mkv.MatroskaExtractor

/**
 * Extends [MatroskaExtractor] to extract font attachments from MKV
 * and wraps the [ExtractorOutput] to eavesdrop on ASS subtitle data.
 *
 * Uses a subclass for font extraction (overriding protected methods)
 * and wraps the output in [AssExtractorOutput] via reflection on init.
 */
@UnstableApi
class AssMatroskaExtractor(
    private val handler: AssHandler
) : MatroskaExtractor(
    AssSubtitleParserFactory(),
    FLAG_EMIT_RAW_SUBTITLE_DATA
) {

    companion object {
        private const val TAG = "assrender"

        private const val ID_ATTACHMENTS = 0x1941A469
        private const val ID_ATTACHED_FILE = 0x61A7
        private const val ID_FILE_NAME = 0x466E
        private const val ID_FILE_MIME_TYPE = 0x4660
        private const val ID_FILE_DATA = 0x465C

        private val FONT_MIME_TYPES = setOf(
            "application/x-truetype-font",
            "application/x-font-truetype",
            "application/x-font-ttf",
            "application/vnd.ms-opentype",
            "application/x-font-opentype",
            "application/x-font-otf",
            "font/ttf",
            "font/otf",
            "font/sfnt",
            "font/collection",
            "application/font-woff",
            "font/woff",
            "font/woff2",
        )

        private var extractorOutputField: java.lang.reflect.Field? = null

        init {
            try {
                extractorOutputField = MatroskaExtractor::class.java
                    .getDeclaredField("extractorOutput")
                    .apply { isAccessible = true }
            } catch (e: Exception) {
                Log.e(TAG, "Could not access extractorOutput field", e)
            }
        }
    }

    private var currentFileName: String? = null
    private var currentFileMimeType: String? = null
    private var outputWrapped = false

    override fun getElementType(id: Int): Int {
        return when (id) {
            ID_ATTACHMENTS -> EbmlProcessor.ELEMENT_TYPE_MASTER
            ID_ATTACHED_FILE -> EbmlProcessor.ELEMENT_TYPE_MASTER
            ID_FILE_NAME, ID_FILE_MIME_TYPE -> EbmlProcessor.ELEMENT_TYPE_STRING
            ID_FILE_DATA -> EbmlProcessor.ELEMENT_TYPE_BINARY
            else -> super.getElementType(id)
        }
    }

    override fun isLevel1Element(id: Int): Boolean {
        return super.isLevel1Element(id) || id == ID_ATTACHMENTS
    }

    override fun startMasterElement(id: Int, contentPosition: Long, contentSize: Long) {
        if (!outputWrapped) {
            wrapExtractorOutput()
            outputWrapped = true
        }

        when (id) {
            ID_ATTACHMENTS -> { /* we handle this, don't pass to super */ }
            ID_ATTACHED_FILE -> {
                currentFileName = null
                currentFileMimeType = null
            }
            else -> super.startMasterElement(id, contentPosition, contentSize)
        }
    }

    override fun endMasterElement(id: Int) {
        when (id) {
            ID_ATTACHED_FILE -> {
                currentFileName = null
                currentFileMimeType = null
            }
            else -> super.endMasterElement(id)
        }
    }

    override fun stringElement(id: Int, value: String) {
        when (id) {
            ID_FILE_NAME -> currentFileName = value
            ID_FILE_MIME_TYPE -> currentFileMimeType = value
            else -> super.stringElement(id, value)
        }
    }

    override fun binaryElement(id: Int, length: Int, input: ExtractorInput) {
        if (id == ID_FILE_DATA) {
            val mime = currentFileMimeType?.lowercase() ?: ""
            val isFont = mime in FONT_MIME_TYPES
            if (isFont) {
                val fontData = ByteArray(length)
                input.readFully(fontData, 0, length)
                handler.onFontAttachment(currentFileName ?: "unknown_font", fontData)
                return
            }
        }
        super.binaryElement(id, length, input)
    }

    private fun wrapExtractorOutput() {
        val field = extractorOutputField ?: return
        try {
            val currentOutput = field.get(this) as? ExtractorOutput ?: return
            if (currentOutput is AssExtractorOutput) return // already wrapped
            val wrapped = AssExtractorOutput(currentOutput, handler)
            field.set(this, wrapped)
            Log.d(TAG, "Wrapped extractorOutput via reflection")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to wrap extractorOutput", e)
        }
    }
}
