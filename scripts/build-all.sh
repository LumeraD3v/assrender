#!/bin/bash
#
# Build everything: FFmpeg (subtitle-only) + libass + dependencies
# for all target Android architectures.
#
# Usage: ./build-all.sh [arm64|arm]
#
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "============================================"
echo "  assrender — Full Native Build"
echo "============================================"
echo ""

ARCH=${1:-""}

echo "Step 1: Building libass + dependencies..."
"$SCRIPT_DIR/build-libass.sh" $ARCH

echo ""
echo "Step 2: Building FFmpeg (subtitle-only)..."
"$SCRIPT_DIR/build-ffmpeg.sh" $ARCH

echo ""
echo "============================================"
echo "  Build complete!"
echo "  Prebuilt libraries are in: prebuilt/"
echo "============================================"
