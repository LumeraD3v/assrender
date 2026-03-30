package io.github.assrender

/**
 * Represents a subtitle track found in a media container.
 */
data class SubtitleTrack(
    val index: Int,
    val codecName: String,
    val language: String?,
    val title: String?,
    val isDefault: Boolean
)
