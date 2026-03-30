package io.github.assrender

/**
 * Configuration for the ASS subtitle renderer.
 */
data class AssRenderConfig(
    /** Video render width in pixels. */
    val videoWidth: Int = 1920,
    /** Video render height in pixels. */
    val videoHeight: Int = 1080,
    /** Font scale multiplier (1.0 = default). */
    val fontScale: Float = 1.0f,
    /** Override default font family for unstyled text. */
    val defaultFontFamily: String? = null,
    /** Path to a directory containing fallback fonts. */
    val fontDirectory: String? = null
)
