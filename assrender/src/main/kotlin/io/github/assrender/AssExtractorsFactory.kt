package io.github.assrender

import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor

/**
 * An [ExtractorsFactory] that:
 * 1. Sets [AssSubtitleParserFactory] so ExoPlayer doesn't crash on ASS tracks
 * 2. Replaces MatroskaExtractor with [AssMatroskaExtractor] to eavesdrop
 *    on raw ASS data for libass rendering
 */
@UnstableApi
class AssExtractorsFactory(
    private val handler: AssHandler
) : ExtractorsFactory {

    private val defaultFactory = DefaultExtractorsFactory()
        .setSubtitleParserFactory(AssSubtitleParserFactory())

    override fun createExtractors(): Array<Extractor> {
        val defaults = defaultFactory.createExtractors()

        return defaults.map { extractor ->
            if (extractor is MatroskaExtractor && extractor !is AssMatroskaExtractor) {
                AssMatroskaExtractor(handler)
            } else {
                extractor
            }
        }.toTypedArray()
    }
}
