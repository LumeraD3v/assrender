#!/bin/bash
#
# Cross-compile FFmpeg for Android with ONLY subtitle support.
# This produces a minimal FFmpeg (~2-3MB) with no video/audio decoders.
#
# Prerequisites:
#   - Android NDK (set ANDROID_NDK_HOME)
#   - FFmpeg source (will be cloned if not present)
#
# Usage: ./build-ffmpeg.sh [arm64|arm]
#
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BUILD_DIR="$PROJECT_DIR/build/ffmpeg"
FFMPEG_SRC="$BUILD_DIR/ffmpeg-src"
API_LEVEL=21

# --- Validate NDK ---
if [ -z "$ANDROID_NDK_HOME" ]; then
    # Try common locations
    if [ -d "$HOME/Android/Sdk/ndk/"* ]; then
        ANDROID_NDK_HOME=$(ls -d "$HOME/Android/Sdk/ndk/"* | sort -V | tail -1)
    elif [ -d "$LOCALAPPDATA/Android/Sdk/ndk/"* ]; then
        ANDROID_NDK_HOME=$(ls -d "$LOCALAPPDATA/Android/Sdk/ndk/"* | sort -V | tail -1)
    else
        echo "ERROR: ANDROID_NDK_HOME not set and NDK not found"
        exit 1
    fi
fi
echo "Using NDK: $ANDROID_NDK_HOME"
TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64"
if [ ! -d "$TOOLCHAIN" ]; then
    TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/windows-x86_64"
fi
if [ ! -d "$TOOLCHAIN" ]; then
    TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64"
fi
echo "Using toolchain: $TOOLCHAIN"

# --- Clone FFmpeg if needed ---
if [ ! -d "$FFMPEG_SRC" ]; then
    echo "Cloning FFmpeg..."
    mkdir -p "$BUILD_DIR"
    git clone --depth 1 --branch release/7.1 https://git.ffmpeg.org/ffmpeg.git "$FFMPEG_SRC"
fi

build_arch() {
    local ARCH=$1
    local ANDROID_ABI
    local TARGET
    local CROSS_PREFIX
    local CPU

    case $ARCH in
        arm64)
            ANDROID_ABI="arm64-v8a"
            TARGET="aarch64-linux-android"
            CROSS_PREFIX="aarch64-linux-android-"
            CPU="armv8-a"
            ;;
        arm)
            ANDROID_ABI="armeabi-v7a"
            TARGET="armv7a-linux-androideabi"
            CROSS_PREFIX="arm-linux-androideabi-"
            CPU="armv7-a"
            ;;
        *)
            echo "Unknown arch: $ARCH (use arm64 or arm)"
            exit 1
            ;;
    esac

    local PREFIX="$PROJECT_DIR/prebuilt/$ANDROID_ABI"
    local BUILD="$BUILD_DIR/build-$ARCH"

    echo "========================================="
    echo "Building FFmpeg for $ANDROID_ABI"
    echo "========================================="

    mkdir -p "$BUILD"
    cd "$FFMPEG_SRC"

    make clean 2>/dev/null || true

    CC="${TOOLCHAIN}/bin/${TARGET}${API_LEVEL}-clang"
    CXX="${TOOLCHAIN}/bin/${TARGET}${API_LEVEL}-clang++"
    STRIP="${TOOLCHAIN}/bin/llvm-strip"

    ./configure \
        --prefix="$PREFIX" \
        --enable-cross-compile \
        --target-os=android \
        --arch=$ARCH \
        --cpu=$CPU \
        --cc="$CC" \
        --cxx="$CXX" \
        --strip="$STRIP" \
        --sysroot="$TOOLCHAIN/sysroot" \
        --enable-shared \
        --disable-static \
        --disable-programs \
        --disable-doc \
        --disable-htmlpages \
        --disable-manpages \
        --disable-podpages \
        --disable-txtpages \
        \
        --disable-everything \
        \
        --enable-demuxer=matroska \
        --enable-demuxer=mov \
        --enable-demuxer=avi \
        --enable-demuxer=mpegts \
        --enable-demuxer=srt \
        --enable-demuxer=ass \
        \
        --enable-decoder=ass \
        --enable-decoder=ssa \
        --enable-decoder=srt \
        --enable-decoder=subrip \
        --enable-decoder=pgssub \
        --enable-decoder=dvdsub \
        --enable-decoder=dvbsub \
        \
        --enable-parser=matroska \
        \
        --enable-protocol=file \
        --enable-protocol=http \
        --enable-protocol=https \
        --enable-protocol=tcp \
        --enable-protocol=tls \
        \
        --disable-avdevice \
        --disable-swscale \
        --disable-postproc \
        --disable-avfilter \
        --disable-encoders \
        --disable-muxers \
        --disable-bsfs \
        --disable-filters \
        --disable-indevs \
        --disable-outdevs \
        --disable-network \
        --enable-network \
        \
        --disable-vulkan \
        --disable-vdpau \
        --disable-vaapi \
        --disable-videotoolbox \
        --disable-audiotoolbox \
        --disable-asm \
        \
        --extra-cflags="-fPIC -Os" \
        --extra-ldflags="-Wl,--gc-sections"

    make -j$(nproc 2>/dev/null || echo 4)
    make install

    echo "FFmpeg installed to $PREFIX"
}

# Build requested arch(s)
if [ -n "$1" ]; then
    build_arch "$1"
else
    build_arch arm64
    build_arch arm
fi

echo "Done! FFmpeg build complete."
