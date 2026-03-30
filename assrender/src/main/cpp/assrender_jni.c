#include <jni.h>
#include <stdio.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <string.h>

#include "subtitle_pipeline.h"

#define LOG_TAG "assrender_jni"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// --- Helper: convert jstring to C string ---
static const char *jstring_to_cstr(JNIEnv *env, jstring str) {
    if (!str) return NULL;
    return (*env)->GetStringUTFChars(env, str, NULL);
}

static void release_cstr(JNIEnv *env, jstring str, const char *cstr) {
    if (str && cstr)
        (*env)->ReleaseStringUTFChars(env, str, cstr);
}

// --- JNI Methods ---

JNIEXPORT jlong JNICALL
Java_io_github_assrender_NativeBridge_nativeInit(
        JNIEnv *env, jobject thiz,
        jint width, jint height, jfloat font_scale) {
    (void)env; (void)thiz;
    AssRenderContext *ctx = assrender_init(width, height, font_scale);
    return (jlong)(intptr_t)ctx;
}

JNIEXPORT jint JNICALL
Java_io_github_assrender_NativeBridge_nativeOpenStream(
        JNIEnv *env, jobject thiz,
        jlong handle, jstring url) {
    (void)thiz;
    AssRenderContext *ctx = (AssRenderContext *)(intptr_t)handle;
    const char *curl = jstring_to_cstr(env, url);
    if (!curl) return -1;

    int result = assrender_open_stream(ctx, curl);
    release_cstr(env, url, curl);
    return result;
}

JNIEXPORT jobjectArray JNICALL
Java_io_github_assrender_NativeBridge_nativeGetTrackInfo(
        JNIEnv *env, jobject thiz,
        jlong handle, jint track_index) {
    (void)thiz;
    AssRenderContext *ctx = (AssRenderContext *)(intptr_t)handle;

    const char *codec = NULL, *language = NULL, *title = NULL;
    int is_default = 0;

    if (assrender_get_track_info(ctx, track_index,
                                  &codec, &language, &title, &is_default) < 0)
        return NULL;

    jclass str_class = (*env)->FindClass(env, "java/lang/String");
    jobjectArray arr = (*env)->NewObjectArray(env, 5, str_class, NULL);

    char idx_str[16];
    snprintf(idx_str, sizeof(idx_str), "%d", track_index);

    (*env)->SetObjectArrayElement(env, arr, 0,
        (*env)->NewStringUTF(env, idx_str));
    (*env)->SetObjectArrayElement(env, arr, 1,
        (*env)->NewStringUTF(env, codec ? codec : ""));
    (*env)->SetObjectArrayElement(env, arr, 2,
        (*env)->NewStringUTF(env, language ? language : ""));
    (*env)->SetObjectArrayElement(env, arr, 3,
        (*env)->NewStringUTF(env, title ? title : ""));
    (*env)->SetObjectArrayElement(env, arr, 4,
        (*env)->NewStringUTF(env, is_default ? "1" : "0"));

    return arr;
}

JNIEXPORT jboolean JNICALL
Java_io_github_assrender_NativeBridge_nativeSelectTrack(
        JNIEnv *env, jobject thiz,
        jlong handle, jint track_index) {
    (void)env; (void)thiz;
    AssRenderContext *ctx = (AssRenderContext *)(intptr_t)handle;
    return assrender_select_track(ctx, track_index) == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_io_github_assrender_NativeBridge_nativeLoadExternalSubtitle(
        JNIEnv *env, jobject thiz,
        jlong handle, jstring path) {
    (void)thiz;
    AssRenderContext *ctx = (AssRenderContext *)(intptr_t)handle;
    const char *cpath = jstring_to_cstr(env, path);
    if (!cpath) return JNI_FALSE;

    int result = assrender_load_external(ctx, cpath);
    release_cstr(env, path, cpath);
    return result == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_io_github_assrender_NativeBridge_nativeRender(
        JNIEnv *env, jobject thiz,
        jlong handle, jlong time_ms, jobject bitmap) {
    (void)thiz;
    AssRenderContext *ctx = (AssRenderContext *)(intptr_t)handle;
    if (!ctx || !bitmap) return JNI_FALSE;

    void *pixels = NULL;
    int ret = AndroidBitmap_lockPixels(env, bitmap, &pixels);
    if (ret != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("Failed to lock bitmap pixels: %d", ret);
        return JNI_FALSE;
    }

    int has_content = assrender_render_frame(ctx, time_ms, (uint8_t *)pixels);

    AndroidBitmap_unlockPixels(env, bitmap);
    return has_content ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_io_github_assrender_NativeBridge_nativeSeek(
        JNIEnv *env, jobject thiz,
        jlong handle, jlong time_ms) {
    (void)env; (void)thiz;
    AssRenderContext *ctx = (AssRenderContext *)(intptr_t)handle;
    assrender_seek(ctx, time_ms);
}

JNIEXPORT void JNICALL
Java_io_github_assrender_NativeBridge_nativeDestroy(
        JNIEnv *env, jobject thiz,
        jlong handle) {
    (void)env; (void)thiz;
    AssRenderContext *ctx = (AssRenderContext *)(intptr_t)handle;
    assrender_destroy(ctx);
}
