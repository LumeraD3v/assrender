#ifndef ASSRENDER_SUBTITLE_PIPELINE_H
#define ASSRENDER_SUBTITLE_PIPELINE_H

#include <stdint.h>

typedef struct AssRenderContext AssRenderContext;

/**
 * Create a new render context.
 * @param width  Video width in pixels.
 * @param height Video height in pixels.
 * @param font_scale Font scale multiplier (1.0 = default).
 * @return Opaque context pointer, or NULL on failure.
 */
AssRenderContext *assrender_init(int width, int height, float font_scale);

/**
 * Open a media stream and scan for subtitle tracks.
 * @return Number of subtitle tracks found, or -1 on error.
 */
int assrender_open_stream(AssRenderContext *ctx, const char *url);

/**
 * Get metadata for a subtitle track.
 * @param out_codec    Output: codec name (e.g., "ass", "srt").
 * @param out_language Output: language tag or empty string.
 * @param out_title    Output: track title or empty string.
 * @param out_default  Output: 1 if default track, 0 otherwise.
 * @return 0 on success, -1 on error.
 */
int assrender_get_track_info(AssRenderContext *ctx, int track_index,
                             const char **out_codec, const char **out_language,
                             const char **out_title, int *out_default);

/**
 * Select a subtitle track for rendering. Extracts all events
 * from the track and loads them into libass.
 * @return 0 on success, -1 on error.
 */
int assrender_select_track(AssRenderContext *ctx, int track_index);

/**
 * Load an external subtitle file (ASS/SSA/SRT).
 * @return 0 on success, -1 on error.
 */
int assrender_load_external(AssRenderContext *ctx, const char *path);

/**
 * Render the subtitle frame at the given timestamp.
 * @param time_ms Playback position in milliseconds.
 * @param out_pixels Output buffer (RGBA, width*height*4 bytes).
 * @return 1 if subtitle content was rendered, 0 if blank.
 */
int assrender_render_frame(AssRenderContext *ctx, int64_t time_ms,
                           uint8_t *out_pixels);

/**
 * Notify of a seek event (clears internal caches).
 */
void assrender_seek(AssRenderContext *ctx, int64_t time_ms);

/**
 * Destroy the context and free all resources.
 */
void assrender_destroy(AssRenderContext *ctx);

#endif // ASSRENDER_SUBTITLE_PIPELINE_H
