#!/bin/bash
#
# Cross-compile libass and its dependencies for Android.
# Dependencies: FreeType, FriBidi, HarfBuzz
#
# Prerequisites:
#   - Android NDK (set ANDROID_NDK_HOME)
#
# Usage: ./build-libass.sh [arm64|arm]
#
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BUILD_DIR="$PROJECT_DIR/build/libass"
API_LEVEL=21

# Dependency versions
FREETYPE_VERSION="2.13.3"
FRIBIDI_VERSION="1.0.16"
HARFBUZZ_VERSION="10.1.0"
LIBASS_VERSION="0.17.3"

# --- Validate NDK ---
if [ -z "$ANDROID_NDK_HOME" ]; then
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

download_and_extract() {
    local url=$1
    local dest=$2
    if [ ! -d "$dest" ]; then
        echo "Downloading $url..."
        mkdir -p "$(dirname "$dest")"
        curl -L "$url" -o "$dest.tar.gz"
        mkdir -p "$dest"
        tar xzf "$dest.tar.gz" -C "$dest" --strip-components=1
        rm "$dest.tar.gz"
    fi
}

build_arch() {
    local ARCH=$1
    local ANDROID_ABI
    local TARGET
    local HOST

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
        *)
            echo "Unknown arch: $ARCH"
            exit 1
            ;;
    esac

    local PREFIX="$PROJECT_DIR/prebuilt/$ANDROID_ABI"
    mkdir -p "$PREFIX"

    export CC="${TOOLCHAIN}/bin/${TARGET}${API_LEVEL}-clang"
    export CXX="${TOOLCHAIN}/bin/${TARGET}${API_LEVEL}-clang++"
    export AR="${TOOLCHAIN}/bin/llvm-ar"
    export RANLIB="${TOOLCHAIN}/bin/llvm-ranlib"
    export STRIP="${TOOLCHAIN}/bin/llvm-strip"
    export PKG_CONFIG_PATH="$PREFIX/lib/pkgconfig"
    export PATH="$TOOLCHAIN/bin:$PATH"

    echo "========================================="
    echo "Building libass stack for $ANDROID_ABI"
    echo "========================================="

    # --- FreeType ---
    echo "--- Building FreeType ---"
    local FREETYPE_SRC="$BUILD_DIR/freetype-src"
    download_and_extract \
        "https://download.savannah.gnu.org/releases/freetype/freetype-${FREETYPE_VERSION}.tar.gz" \
        "$FREETYPE_SRC"

    cd "$FREETYPE_SRC"
    make clean 2>/dev/null || true
    ./configure \
        --host=$HOST \
        --prefix="$PREFIX" \
        --enable-shared \
        --disable-static \
        --with-zlib=no \
        --with-bzip2=no \
        --with-png=no \
        --with-harfbuzz=no \
        CFLAGS="-fPIC -Os"
    make -j$(nproc 2>/dev/null || echo 4)
    make install

    # --- FriBidi ---
    echo "--- Building FriBidi ---"
    local FRIBIDI_SRC="$BUILD_DIR/fribidi-src"
    download_and_extract \
        "https://github.com/fribidi/fribidi/releases/download/v${FRIBIDI_VERSION}/fribidi-${FRIBIDI_VERSION}.tar.xz" \
        "$FRIBIDI_SRC"

    cd "$FRIBIDI_SRC"
    make clean 2>/dev/null || true
    ./configure \
        --host=$HOST \
        --prefix="$PREFIX" \
        --enable-shared \
        --disable-static \
        --disable-docs \
        CFLAGS="-fPIC -Os"
    make -j$(nproc 2>/dev/null || echo 4)
    make install

    # --- HarfBuzz ---
    echo "--- Building HarfBuzz ---"
    local HARFBUZZ_SRC="$BUILD_DIR/harfbuzz-src"
    download_and_extract \
        "https://github.com/harfbuzz/harfbuzz/releases/download/${HARFBUZZ_VERSION}/harfbuzz-${HARFBUZZ_VERSION}.tar.xz" \
        "$HARFBUZZ_SRC"

    cd "$HARFBUZZ_SRC"
    make clean 2>/dev/null || true
    # HarfBuzz uses meson, but we can use the cmake build
    mkdir -p build-$ARCH && cd build-$ARCH
    cmake .. \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI=$ANDROID_ABI \
        -DANDROID_PLATFORM=android-$API_LEVEL \
        -DCMAKE_INSTALL_PREFIX="$PREFIX" \
        -DCMAKE_PREFIX_PATH="$PREFIX" \
        -DBUILD_SHARED_LIBS=ON \
        -DHB_HAVE_FREETYPE=ON \
        -DHB_HAVE_GLIB=OFF \
        -DHB_HAVE_ICU=OFF \
        -DHB_HAVE_GOBJECT=OFF \
        -DHB_BUILD_UTILS=OFF \
        -DHB_BUILD_SUBSET=OFF
    make -j$(nproc 2>/dev/null || echo 4)
    make install

    # --- libass ---
    echo "--- Building libass ---"
    local LIBASS_SRC="$BUILD_DIR/libass-src"
    download_and_extract \
        "https://github.com/libass/libass/releases/download/${LIBASS_VERSION}/libass-${LIBASS_VERSION}.tar.gz" \
        "$LIBASS_SRC"

    cd "$LIBASS_SRC"
    make clean 2>/dev/null || true
    ./configure \
        --host=$HOST \
        --prefix="$PREFIX" \
        --enable-shared \
        --disable-static \
        --disable-asm \
        --disable-fontconfig \
        --disable-require-system-font-provider \
        CFLAGS="-fPIC -Os -I$PREFIX/include -I$PREFIX/include/freetype2" \
        LDFLAGS="-L$PREFIX/lib" \
        FREETYPE_CFLAGS="-I$PREFIX/include/freetype2" \
        FREETYPE_LIBS="-L$PREFIX/lib -lfreetype" \
        FRIBIDI_CFLAGS="-I$PREFIX/include/fribidi" \
        FRIBIDI_LIBS="-L$PREFIX/lib -lfribidi" \
        HARFBUZZ_CFLAGS="-I$PREFIX/include/harfbuzz" \
        HARFBUZZ_LIBS="-L$PREFIX/lib -lharfbuzz"
    make -j$(nproc 2>/dev/null || echo 4)
    make install

    echo "libass stack installed to $PREFIX"
}

# Download sources
mkdir -p "$BUILD_DIR"

# Build requested arch(s)
if [ -n "$1" ]; then
    build_arch "$1"
else
    build_arch arm64
    build_arch arm
fi

echo "Done! libass build complete."
