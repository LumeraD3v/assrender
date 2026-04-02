cmake_minimum_required(VERSION 3.22.1)
project(assrender C)

# Prebuilt library paths (populated by build scripts)
set(PREBUILT_DIR "${CMAKE_SOURCE_DIR}/../../../../prebuilt/${ANDROID_ABI}")

# --- FFmpeg libraries ---
add_library(avformat SHARED IMPORTED)
set_target_properties(avformat PROPERTIES
    IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libavformat.so"
)

add_library(avcodec SHARED IMPORTED)
set_target_properties(avcodec PROPERTIES
    IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libavcodec.so"
)

add_library(avutil SHARED IMPORTED)
set_target_properties(avutil PROPERTIES
    IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libavutil.so"
)

add_library(swresample SHARED IMPORTED)
set_target_properties(swresample PROPERTIES
    IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libswresample.so"
)

# --- libass and dependencies ---
add_library(ass SHARED IMPORTED)
set_target_properties(ass PROPERTIES
    IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libass.so"
)

add_library(freetype SHARED IMPORTED)
set_target_properties(freetype PROPERTIES
    IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libfreetype.so"
)

add_library(fribidi SHARED IMPORTED)
set_target_properties(fribidi PROPERTIES
    IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libfribidi.so"
)

add_library(harfbuzz SHARED IMPORTED)
set_target_properties(harfbuzz PROPERTIES
    IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libharfbuzz.so"
)

add_library(fontconfig SHARED IMPORTED)
set_target_properties(fontconfig PROPERTIES
    IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libfontconfig.so"
)

add_library(expat SHARED IMPORTED)
set_target_properties(expat PROPERTIES
    IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libexpat.so"
)

# --- Include paths ---
include_directories(
    "${PREBUILT_DIR}/include"
)

# --- Our library ---
add_library(assrender SHARED
    assrender_jni.c
    subtitle_pipeline.c
    ass_direct.c
    ass_direct_jni.c
)

target_link_libraries(assrender
    avformat
    avcodec
    avutil
    swresample
    ass
    freetype
    fribidi
    harfbuzz
    fontconfig
    expat
    android
    jnigraphics
    log
)
