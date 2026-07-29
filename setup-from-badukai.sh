#!/usr/bin/env bash
#
# setup-from-badukai.sh
#
# Copies all binary assets (the KataGo engine, its jniLibs dependencies, and the
# neural network models) directly from a local clone of the upstream "badukai"
# Android app repository (philippmerz/badukai). After running this script, the
# project layout matches badukai 1:1 under app/src/main/assets and
# app/src/main/jniLibs, and you can build the APK with ./gradlew assembleDebug.
#
# Usage:
#   ./setup-from-badukai.sh /absolute/path/to/your/badukai-checkout
#
# Or set the env var BADUKAI_DIR instead:
#   BADUKAI_DIR=/home/you/badukai ./setup-from-badukai.sh
#
# If you don't have a local clone, the script will clone it from GitHub into
# .badukai-upstream for you (requires git and an internet connection).
#

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
BADUKAI_DIR="${1:-${BADUKAI_DIR:-}}"

REPO="https://github.com/philippmerz/badukai.git"
BRANCH="main"

if [ -z "$BADUKAI_DIR" ]; then
    BADUKAI_DIR="$HERE/.badukai-upstream"
    if [ ! -d "$BADUKAI_DIR/.git" ]; then
        echo "No badukai dir provided -- cloning $REPO into $BADUKAI_DIR"
        git clone --depth=1 --branch "$BRANCH" "$REPO" "$BADUKAI_DIR"
    else
        echo "Using existing cached clone at $BADUKAI_DIR"
        (cd "$BADUKAI_DIR" && git fetch --depth=1 origin "$BRANCH" && git reset --hard "origin/$BRANCH") || true
    fi
fi

SRC_APP="$BADUKAI_DIR/app/src/main"
DST_APP="$HERE/app/src/main"

require_file() {
    local f="$1"
    if [ ! -e "$f" ]; then
        echo "Required file missing in badukai checkout: $f" >&2
        echo "Is $BADUKAI_DIR really a philippmerz/badukai clone?" >&2
        exit 1
    fi
}

copy_file() {
    local s="$1" d="$2"
    mkdir -p "$(dirname "$d")"
    if [ -e "$s" ]; then
        cp -f "$s" "$d"
        echo "  OK  $s  ->  $d"
    else
        echo "  -   $s not present (skipped)"
    fi
}

copy_dir() {
    local s="$1" d="$2"
    if [ -d "$s" ]; then
        mkdir -p "$d"
        find "$s" -maxdepth 1 -type f -exec cp -f {} "$d/" \;
        echo "  OK  $s/*  ->  $d/"
    else
        echo "  -   $s not present (skipped)"
    fi
}

echo ""
echo "==> Copying assets from $SRC_APP"
require_file "$SRC_APP/assets/libkatago.so"
require_file "$SRC_APP/assets/gtp_static.cfg"

copy_file "$SRC_APP/assets/libkatago.so"    "$DST_APP/assets/libkatago.so"
copy_file "$SRC_APP/assets/gtp_static.cfg"  "$DST_APP/assets/gtp_static.cfg"
copy_dir  "$SRC_APP/assets/models"          "$DST_APP/assets/models"

echo ""
echo "==> Copying jniLibs/arm64-v8a from $SRC_APP"
copy_dir "$SRC_APP/jniLibs/arm64-v8a"       "$DST_APP/jniLibs/arm64-v8a"

echo ""
echo "==> Summary"
echo "assets/libkatago.so:  $(ls -lh "$DST_APP/assets/libkatago.so"    2>/dev/null | awk '{print $5}' || echo MISSING)"
echo "assets/gtp_static.cfg: $(ls -lh "$DST_APP/assets/gtp_static.cfg" 2>/dev/null | awk '{print $5}' || echo MISSING)"
echo "assets/models:         $(find "$DST_APP/assets/models" -maxdepth 1 -type f 2>/dev/null | wc -l | tr -d ' ') files"
echo "jniLibs/arm64-v8a:    $(find "$DST_APP/jniLibs/arm64-v8a"   -maxdepth 1 -type f 2>/dev/null | wc -l | tr -d ' ') .so files"

echo ""
echo "Done. Now run:  ./gradlew assembleDebug"
