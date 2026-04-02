#!/bin/bash
#
# Cross-compile fontconfig for Android.
# Prerequisites: FreeType already built in prebuilt/
#
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BUILD_DIR="$PROJECT_DIR/build/fontconfig"
FONTCONFIG_VERSION="2.15.0"
EXPAT_VERSION="2.6.4"
API_LEVEL=21

if [ -z "$ANDROID_NDK_HOME" ]; then
    echo "ERROR: ANDROID_NDK_HOME not set"
    exit 1
fi

TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64"

download_fontconfig() {
    if [ ! -d "$BUILD_DIR/fontconfig-src" ]; then
        echo "Downloading fontconfig..."
        mkdir -p "$BUILD_DIR"
        curl -L "https://www.freedesktop.org/software/fontconfig/release/fontconfig-${FONTCONFIG_VERSION}.tar.gz" -o "$BUILD_DIR/fontconfig.tar.gz"
        mkdir -p "$BUILD_DIR/fontconfig-src"
        tar xzf "$BUILD_DIR/fontconfig.tar.gz" -C "$BUILD_DIR/fontconfig-src" --strip-components=1
        rm "$BUILD_DIR/fontconfig.tar.gz"
    fi
}

build_arch() {
    local ARCH=$1
    local ANDROID_ABI TARGET HOST

    case $ARCH in
        arm64)
            ANDROID_ABI="arm64-v8a"
            TARGET="aarch64-linux-android"
            HOST="aarch64-linux-android"
            ;;
        arm)
            ANDROID_ABI="armeabi-v7a"
            TARGET="armv7a-linux-androideabi"
            HOST="arm-linux-androideabi"
            ;;
        *) echo "Unknown arch: $ARCH"; exit 1 ;;
    esac

    local PREFIX="$PROJECT_DIR/prebuilt/$ANDROID_ABI"

    export CC="${TOOLCHAIN}/bin/${TARGET}${API_LEVEL}-clang"
    export CXX="${TOOLCHAIN}/bin/${TARGET}${API_LEVEL}-clang++"
    export AR="${TOOLCHAIN}/bin/llvm-ar"
    export RANLIB="${TOOLCHAIN}/bin/llvm-ranlib"
    export STRIP="${TOOLCHAIN}/bin/llvm-strip"
    export PKG_CONFIG_PATH="$PREFIX/lib/pkgconfig"
    export PATH="$TOOLCHAIN/bin:$PATH"

    echo "========================================="
    echo "Building fontconfig for $ANDROID_ABI"
    echo "========================================="

    # --- Build expat first ---
    echo "--- Building expat ---"
    local EXPAT_SRC="$BUILD_DIR/expat-src"
    if [ ! -d "$EXPAT_SRC" ]; then
        curl -L "https://github.com/libexpat/libexpat/releases/download/R_${EXPAT_VERSION//./_}/expat-${EXPAT_VERSION}.tar.gz" -o "$BUILD_DIR/expat.tar.gz"
        mkdir -p "$EXPAT_SRC"
        tar xzf "$BUILD_DIR/expat.tar.gz" -C "$EXPAT_SRC" --strip-components=1
        rm "$BUILD_DIR/expat.tar.gz"
    fi

    cd "$EXPAT_SRC"
    make clean 2>/dev/null || true
    ./configure \
        --host=$HOST \
        --prefix="$PREFIX" \
        --enable-shared \
        --disable-static \
        --without-docbook \
        CFLAGS="-fPIC -Os"
    make -j$(nproc 2>/dev/null || echo 4)
    make install

    # --- Build fontconfig ---
    cd "$BUILD_DIR/fontconfig-src"
    make clean 2>/dev/null || true

    ./configure \
        --host=$HOST \
        --prefix="$PREFIX" \
        --enable-shared \
        --disable-static \
        --disable-docs \
        --disable-cache-build \
        --with-default-fonts=/system/fonts \
        --with-cache-dir=/data/local/tmp/fontconfig \
        --with-baseconfigdir=/system/etc/fonts \
        CFLAGS="-fPIC -Os -I$PREFIX/include -I$PREFIX/include/freetype2" \
        LDFLAGS="-L$PREFIX/lib" \
        FREETYPE_CFLAGS="-I$PREFIX/include/freetype2" \
        FREETYPE_LIBS="-L$PREFIX/lib -lfreetype"

    make -j$(nproc 2>/dev/null || echo 4)
    # Install only libs and headers, skip config files (they target /system/)
    make install-exec install-pkgconfigDATA 2>/dev/null || true
    # Manually install headers
    mkdir -p "$PREFIX/include/fontconfig"
    cp -f fontconfig/fontconfig.h fontconfig/fcfreetype.h fontconfig/fcprivate.h "$PREFIX/include/fontconfig/" 2>/dev/null || true

    echo "fontconfig installed to $PREFIX"
}

download_fontconfig

if [ -n "$1" ]; then
    build_arch "$1"
else
    build_arch arm64
    build_arch arm
fi

echo "Done! fontconfig build complete."
