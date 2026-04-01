package io.github.assrender

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser

/**
 * A [SubtitleParser.Factory] that returns a no-op parser for ASS/SSA tracks,
 * preventing ExoPlayer from trying to parse them (which would crash with
 * "Legacy decoding is disabled"). All other formats are handled normally.
 */
@UnstableApi
class AssSubtitleParserFactory : SubtitleParser.Factory {

    private val defaultFactory = DefaultSubtitleParserFactory()

    override fun supportsFormat(format: Format): Boolean {
        val mime = format.sampleMimeType ?: return defaultFactory.supportsFormat(format)
        if (mime == MimeTypes.TEXT_SSA || mime == "text/x-ssa" || mime == "text/x-ass") {
            return true
        }
        return defaultFactory.supportsFormat(format)
    }

    override fun getCueReplacementBehavior(format: Format): Int {
        val mime = format.sampleMimeType
        if (mime == MimeTypes.TEXT_SSA || mime == "text/x-ssa" || mime == "text/x-ass") {
            return Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE
        }
        return defaultFactory.getCueReplacementBehavior(format)
    }

    override fun create(format: Format): SubtitleParser {
        val mime = format.sampleMimeType
        if (mime == MimeTypes.TEXT_SSA || mime == "text/x-ssa" || mime == "text/x-ass") {
            return AssNoOpSubtitleParser()
        }
        return defaultFactory.create(format)
    }
}

/**
 * A subtitle parser that does nothing — satisfies ExoPlayer's pipeline
 * without actually processing ASS data (our libass handles that).
 */
@UnstableApi
internal class AssNoOpSubtitleParser : SubtitleParser {

    override fun parse(
        data: ByteArray,
        offset: Int,
        length: Int,
        outputOptions: SubtitleParser.OutputOptions,
        output: Consumer<CuesWithTiming>
    ) {
        // No-op: libass handles ASS rendering via AssHandler
    }

    override fun getCueReplacementBehavior(): Int {
        return Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE
    }
}
