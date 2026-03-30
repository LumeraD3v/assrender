#include "subtitle_pipeline.h"

#include <stdlib.h>
#include <string.h>
#include <android/log.h>

#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libavutil/dict.h>
#include <ass/ass.h>

#define LOG_TAG "assrender"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define MAX_SUBTITLE_TRACKS 64

typedef struct {
    int stream_index;
    const char *codec_name;
    char language[64];
    char title[256];
    int is_default;
} SubTrackInfo;

struct AssRenderContext {
    int width;
    int height;
    float font_scale;

    // FFmpeg
    AVFormatContext *fmt_ctx;
    SubTrackInfo tracks[MAX_SUBTITLE_TRACKS];
    int track_count;

    // libass
    ASS_Library *ass_library;
    ASS_Renderer *ass_renderer;
    ASS_Track *ass_track;
};

AssRenderContext *assrender_init(int width, int height, float font_scale) {
    AssRenderContext *ctx = calloc(1, sizeof(AssRenderContext));
    if (!ctx) return NULL;

    ctx->width = width;
    ctx->height = height;
    ctx->font_scale = font_scale;

    // Init libass
    ctx->ass_library = ass_library_init();
    if (!ctx->ass_library) {
        LOGE("Failed to init libass library");
        free(ctx);
        return NULL;
    }
    ass_set_message_cb(ctx->ass_library, NULL, NULL);

    ctx->ass_renderer = ass_renderer_init(ctx->ass_library);
    if (!ctx->ass_renderer) {
        LOGE("Failed to init libass renderer");
        ass_library_done(ctx->ass_library);
        free(ctx);
        return NULL;
    }

    ass_set_storage_size(ctx->ass_renderer, width, height);
    ass_set_frame_size(ctx->ass_renderer, width, height);
    ass_set_font_scale(ctx->ass_renderer, (double)font_scale);
    ass_set_fonts(ctx->ass_renderer, NULL, "sans-serif",
                  ASS_FONTPROVIDER_AUTODETECT, NULL, 1);

    LOGI("assrender initialized: %dx%d, font_scale=%.2f", width, height, font_scale);
    return ctx;
}

int assrender_open_stream(AssRenderContext *ctx, const char *url) {
    if (!ctx || !url) return -1;

    // Close previous format context if any
    if (ctx->fmt_ctx) {
        avformat_close_input(&ctx->fmt_ctx);
        ctx->track_count = 0;
    }

    // Open the stream
    int ret = avformat_open_input(&ctx->fmt_ctx, url, NULL, NULL);
    if (ret < 0) {
        LOGE("Failed to open stream: %s (error %d)", url, ret);
        return -1;
    }

    ret = avformat_find_stream_info(ctx->fmt_ctx, NULL);
    if (ret < 0) {
        LOGE("Failed to find stream info (error %d)", ret);
        avformat_close_input(&ctx->fmt_ctx);
        return -1;
    }

    // Scan for subtitle streams
    ctx->track_count = 0;
    for (unsigned i = 0; i < ctx->fmt_ctx->nb_streams; i++) {
        AVStream *stream = ctx->fmt_ctx->streams[i];
        if (stream->codecpar->codec_type != AVMEDIA_TYPE_SUBTITLE)
            continue;
        if (ctx->track_count >= MAX_SUBTITLE_TRACKS)
            break;

        SubTrackInfo *info = &ctx->tracks[ctx->track_count];
        info->stream_index = i;

        const AVCodecDescriptor *desc = avcodec_descriptor_get(stream->codecpar->codec_id);
        info->codec_name = desc ? desc->name : "unknown";

        // Extract metadata
        AVDictionaryEntry *lang = av_dict_get(stream->metadata, "language", NULL, 0);
        if (lang && lang->value)
            strncpy(info->language, lang->value, sizeof(info->language) - 1);
        else
            info->language[0] = '\0';

        AVDictionaryEntry *title = av_dict_get(stream->metadata, "title", NULL, 0);
        if (title && title->value)
            strncpy(info->title, title->value, sizeof(info->title) - 1);
        else
            info->title[0] = '\0';

        info->is_default = (stream->disposition & AV_DISPOSITION_DEFAULT) ? 1 : 0;

        LOGI("Found subtitle track %d: %s, lang=%s, title=%s, default=%d",
             ctx->track_count, info->codec_name, info->language,
             info->title, info->is_default);

        ctx->track_count++;
    }

    LOGI("Found %d subtitle tracks", ctx->track_count);
    return ctx->track_count;
}

int assrender_get_track_info(AssRenderContext *ctx, int track_index,
                             const char **out_codec, const char **out_language,
                             const char **out_title, int *out_default) {
    if (!ctx || track_index < 0 || track_index >= ctx->track_count)
        return -1;

    SubTrackInfo *info = &ctx->tracks[track_index];
    *out_codec = info->codec_name;
    *out_language = info->language;
    *out_title = info->title;
    *out_default = info->is_default;
    return 0;
}

static int extract_and_load_track(AssRenderContext *ctx, int track_index) {
    if (!ctx->fmt_ctx || track_index < 0 || track_index >= ctx->track_count)
        return -1;

    SubTrackInfo *info = &ctx->tracks[track_index];
    AVStream *stream = ctx->fmt_ctx->streams[info->stream_index];

    // Free previous ass track
    if (ctx->ass_track) {
        ass_free_track(ctx->ass_track);
        ctx->ass_track = NULL;
    }

    // For ASS/SSA: the codec extradata contains the full ASS header
    // ([Script Info], [V4+ Styles], etc.)
    if (stream->codecpar->codec_id == AV_CODEC_ID_ASS ||
        stream->codecpar->codec_id == AV_CODEC_ID_SSA) {

        ctx->ass_track = ass_new_track(ctx->ass_library);
        if (!ctx->ass_track) return -1;

        // Load the ASS header from extradata
        if (stream->codecpar->extradata && stream->codecpar->extradata_size > 0) {
            ass_process_codec_private(ctx->ass_track,
                                      (char *)stream->codecpar->extradata,
                                      stream->codecpar->extradata_size);
        }

        // Extract font attachments from the container
        for (unsigned i = 0; i < ctx->fmt_ctx->nb_streams; i++) {
            AVStream *s = ctx->fmt_ctx->streams[i];
            if (s->codecpar->codec_type == AVMEDIA_TYPE_ATTACHMENT &&
                s->codecpar->extradata && s->codecpar->extradata_size > 0) {
                AVDictionaryEntry *fn = av_dict_get(s->metadata, "filename", NULL, 0);
                const char *filename = fn ? fn->value : "attached_font";
                ass_add_font(ctx->ass_library, filename,
                             (char *)s->codecpar->extradata,
                             s->codecpar->extradata_size);
                LOGI("Loaded font attachment: %s (%d bytes)",
                     filename, s->codecpar->extradata_size);
            }
        }

        // Re-configure fonts after adding attachments
        ass_set_fonts(ctx->ass_renderer, NULL, "sans-serif",
                      ASS_FONTPROVIDER_AUTODETECT, NULL, 1);

        // Read all subtitle packets from the stream
        AVPacket *pkt = av_packet_alloc();
        if (!pkt) return -1;

        // Seek to beginning
        av_seek_frame(ctx->fmt_ctx, -1, 0, AVSEEK_FLAG_BACKWARD);

        while (av_read_frame(ctx->fmt_ctx, pkt) >= 0) {
            if (pkt->stream_index == info->stream_index) {
                // ASS packets in FFmpeg contain the dialogue line text.
                // Timing comes from pkt->pts and pkt->duration.
                int64_t start_ms = av_rescale_q(pkt->pts,
                    stream->time_base, (AVRational){1, 1000});
                int64_t duration_ms = av_rescale_q(pkt->duration,
                    stream->time_base, (AVRational){1, 1000});

                ass_process_chunk(ctx->ass_track,
                                  (char *)pkt->data, pkt->size,
                                  start_ms, duration_ms);
            }
            av_packet_unref(pkt);
        }
        av_packet_free(&pkt);

        LOGI("Loaded ASS track: %d events", ctx->ass_track->n_events);

    } else if (stream->codecpar->codec_id == AV_CODEC_ID_SUBRIP ||
               stream->codecpar->codec_id == AV_CODEC_ID_SRT) {
        // SRT fallback: create an ASS track and convert SRT events
        ctx->ass_track = ass_new_track(ctx->ass_library);
        if (!ctx->ass_track) return -1;

        // Set default style for SRT
        ass_set_storage_size(ctx->ass_renderer, ctx->width, ctx->height);

        AVPacket *pkt = av_packet_alloc();
        if (!pkt) return -1;

        av_seek_frame(ctx->fmt_ctx, -1, 0, AVSEEK_FLAG_BACKWARD);

        while (av_read_frame(ctx->fmt_ctx, pkt) >= 0) {
            if (pkt->stream_index == info->stream_index && pkt->data) {
                int64_t start_ms = av_rescale_q(pkt->pts,
                    stream->time_base, (AVRational){1, 1000});
                int64_t duration_ms = av_rescale_q(pkt->duration,
                    stream->time_base, (AVRational){1, 1000});

                // SRT text can be fed as a chunk — libass handles basic formatting
                ass_process_chunk(ctx->ass_track,
                                  (char *)pkt->data, pkt->size,
                                  start_ms, duration_ms);
            }
            av_packet_unref(pkt);
        }
        av_packet_free(&pkt);

        LOGI("Loaded SRT track: %d events", ctx->ass_track->n_events);
    } else {
        LOGE("Unsupported subtitle codec: %s", info->codec_name);
        return -1;
    }

    return 0;
}

int assrender_select_track(AssRenderContext *ctx, int track_index) {
    if (!ctx) return -1;
    return extract_and_load_track(ctx, track_index);
}

int assrender_load_external(AssRenderContext *ctx, const char *path) {
    if (!ctx || !path) return -1;

    if (ctx->ass_track) {
        ass_free_track(ctx->ass_track);
        ctx->ass_track = NULL;
    }

    ctx->ass_track = ass_read_file(ctx->ass_library, path, NULL);
    if (!ctx->ass_track) {
        LOGE("Failed to load external subtitle: %s", path);
        return -1;
    }

    LOGI("Loaded external subtitle: %s (%d events)", path, ctx->ass_track->n_events);
    return 0;
}

int assrender_render_frame(AssRenderContext *ctx, int64_t time_ms,
                           uint8_t *out_pixels) {
    if (!ctx || !ctx->ass_track || !ctx->ass_renderer || !out_pixels)
        return 0;

    int changed = 0;
    ASS_Image *img = ass_render_frame(ctx->ass_renderer, ctx->ass_track,
                                      time_ms, &changed);

    // Clear output buffer
    memset(out_pixels, 0, ctx->width * ctx->height * 4);

    if (!img) return 0;

    // Composite all ASS_Image layers into the RGBA output buffer
    int has_content = 0;
    while (img) {
        if (img->w == 0 || img->h == 0) {
            img = img->next;
            continue;
        }

        has_content = 1;
        uint8_t r = (img->color >> 24) & 0xFF;
        uint8_t g = (img->color >> 16) & 0xFF;
        uint8_t b = (img->color >> 8) & 0xFF;
        uint8_t a = 255 - (img->color & 0xFF); // ASS: 0=opaque, 255=transparent

        uint8_t *src = img->bitmap;
        for (int y = 0; y < img->h; y++) {
            uint8_t *dst = out_pixels + ((img->dst_y + y) * ctx->width + img->dst_x) * 4;
            for (int x = 0; x < img->w; x++) {
                uint8_t alpha = (uint8_t)(((uint32_t)src[x] * a) >> 8);
                if (alpha == 0) {
                    dst += 4;
                    continue;
                }

                // Alpha blending (premultiplied)
                uint8_t dst_a = dst[3];
                if (dst_a == 0) {
                    dst[0] = r;
                    dst[1] = g;
                    dst[2] = b;
                    dst[3] = alpha;
                } else {
                    uint32_t inv = 255 - alpha;
                    dst[0] = (uint8_t)((alpha * r + inv * dst[0]) / 255);
                    dst[1] = (uint8_t)((alpha * g + inv * dst[1]) / 255);
                    dst[2] = (uint8_t)((alpha * b + inv * dst[2]) / 255);
                    dst[3] = (uint8_t)(alpha + (inv * dst_a) / 255);
                }
                dst += 4;
            }
            src += img->stride;
        }
        img = img->next;
    }

    return has_content;
}

void assrender_seek(AssRenderContext *ctx, int64_t time_ms) {
    (void)time_ms;
    if (!ctx) return;
    // libass doesn't need explicit seek notification,
    // but we flush any cached state if needed in the future.
    ass_flush_events(ctx->ass_track);
}

void assrender_destroy(AssRenderContext *ctx) {
    if (!ctx) return;

    if (ctx->ass_track)
        ass_free_track(ctx->ass_track);
    if (ctx->ass_renderer)
        ass_renderer_done(ctx->ass_renderer);
    if (ctx->ass_library)
        ass_library_done(ctx->ass_library);
    if (ctx->fmt_ctx)
        avformat_close_input(&ctx->fmt_ctx);

    free(ctx);
    LOGI("assrender destroyed");
}
