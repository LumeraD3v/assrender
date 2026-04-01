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
MBEDTLS_SRC="$BUILD_DIR/mbedtls-src"
MBEDTLS_VERSION="3.6.3"
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
    echo "Building FFmpeg for $ANDROID_ABI (with TLS)"
    echo "========================================="

    CC="${TOOLCHAIN}/bin/${TARGET}${API_LEVEL}-clang"
    CXX="${TOOLCHAIN}/bin/${TARGET}${API_LEVEL}-clang++"
    STRIP="${TOOLCHAIN}/bin/llvm-strip"
    AR="${TOOLCHAIN}/bin/llvm-ar"
    RANLIB="${TOOLCHAIN}/bin/llvm-ranlib"

    # --- Build mbedTLS (static, linked into FFmpeg) ---
    echo "--- Building mbedTLS for $ANDROID_ABI ---"
    if [ ! -d "$MBEDTLS_SRC" ]; then
        echo "Downloading mbedTLS..."
        mkdir -p "$BUILD_DIR"
        curl -L "https://github.com/Mbed-TLS/mbedtls/releases/download/mbedtls-${MBEDTLS_VERSION}/mbedtls-${MBEDTLS_VERSION}.tar.bz2" -o "$BUILD_DIR/mbedtls.tar.bz2"
        mkdir -p "$MBEDTLS_SRC"
        tar xjf "$BUILD_DIR/mbedtls.tar.bz2" -C "$MBEDTLS_SRC" --strip-components=1
        rm "$BUILD_DIR/mbedtls.tar.bz2"
    fi

    local MBEDTLS_BUILD="$BUILD_DIR/mbedtls-build-$ARCH"
    local MBEDTLS_PREFIX="$BUILD_DIR/mbedtls-install-$ARCH"
    rm -rf "$MBEDTLS_BUILD"
    mkdir -p "$MBEDTLS_BUILD" && cd "$MBEDTLS_BUILD"

    cmake "$MBEDTLS_SRC" \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI=$ANDROID_ABI \
        -DANDROID_PLATFORM=android-$API_LEVEL \
        -DCMAKE_INSTALL_PREFIX="$MBEDTLS_PREFIX" \
        -DENABLE_TESTING=OFF \
        -DENABLE_PROGRAMS=OFF \
        -DBUILD_SHARED_LIBS=OFF \
        -DCMAKE_C_FLAGS="-fPIC -Os"

    make -j$(nproc 2>/dev/null || echo 4)
    make install

    echo "mbedTLS installed to $MBEDTLS_PREFIX"

    # --- Build FFmpeg ---
    mkdir -p "$BUILD"
    cd "$FFMPEG_SRC"

    make clean 2>/dev/null || true

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
        --enable-version3 \
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
        --enable-protocol=httpproxy \
        --enable-protocol=hls \
        \
        --enable-network \
        --enable-mbedtls \
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
        \
        --disable-vulkan \
        --disable-vdpau \
        --disable-vaapi \
        --disable-videotoolbox \
        --disable-audiotoolbox \
        --disable-asm \
        \
        --extra-cflags="-fPIC -Os -I$MBEDTLS_PREFIX/include" \
        --extra-ldflags="-Wl,--gc-sections -L$MBEDTLS_PREFIX/lib" \
        --extra-libs="-lmbedtls -lmbedx509 -lmbedcrypto"

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
