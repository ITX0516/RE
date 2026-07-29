#!/usr/bin/env bash
#
# setup-from-badukai.sh
#
# Copies ONLY the minimal binary assets that BadukNext actually needs to run
# the bundled KataGo engine:
#   - engine binary          (assets/engine/libkatago.so or assets/libkatago.so
#                            or jniLibs/arm64-v8a/libkatago.so)
#   - default GTP config     (assets/engine/default_gtp.cfg or assets/gtp_static.cfg)
#   - libc++_shared.so       (assets/engine/libc++_shared.so or jniLibs)
#   - optional model weights under assets/models/ (user can still pick their own)
#
# Everything else that the upstream badukai repo bundles under jniLibs (SNPE,
# QNN, OpenCV, SDL2, TFLite, Python, etc.) is explicitly excluded. Those libs
# are not used by the BadukNext app at runtime and would otherwise bloat the
# APK from ~6 MB to ~80+ MB.
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

copy_file_if_exists() {
    local s="$1" d="$2"
    if [ -e "$s" ]; then
        mkdir -p "$(dirname "$d")"
        cp -f "$s" "$d"
        echo "  OK  $s  ->  $d"
        return 0
    fi
    return 1
}

# Copy engine binary: try several candidate locations the upstream repo may use.
copy_engine_binary() {
    local dest="$1"
    mkdir -p "$(dirname "$dest")"
    # Try the BadukAI repo conventions first.
    for cand in \
        "$SRC_APP/assets/engine/katago" \
        "$SRC_APP/assets/engine/libkatago.so" \
        "$SRC_APP/assets/libkatago.so" \
        "$SRC_APP/jniLibs/arm64-v8a/libkatago.so"; do
        if [ -e "$cand" ]; then
            cp -f "$cand" "$dest"
            echo "  OK  $cand  ->  $dest"
            return 0
        fi
    done
    echo "  MISSING engine binary (looked under $SRC_APP assets/jniLibs)" >&2
    return 1
}

# Copy default GTP config.
copy_gtp_config() {
    local dest="$1"
    mkdir -p "$(dirname "$dest")"
    for cand in \
        "$SRC_APP/assets/engine/default_gtp.cfg" \
        "$SRC_APP/assets/gtp_static.cfg" \
        "$SRC_APP/assets/engine/gtp_static.cfg"; do
        if [ -e "$cand" ]; then
            cp -f "$cand" "$dest"
            echo "  OK  $cand  ->  $dest"
            return 0
        fi
    done
    echo "  MISSING gtp config (looked under $SRC_APP assets)" >&2
    return 1
}

# Copy libc++_shared.so next to the engine (or as jniLib if present upstream).
copy_libcxx() {
    local dest_assets="$1"
    local dest_jnilibs="$2"
    for cand in \
        "$SRC_APP/assets/engine/libc++_shared.so" \
        "$SRC_APP/assets/libc++_shared.so" \
        "$SRC_APP/jniLibs/arm64-v8a/libc++_shared.so"; do
        if [ -e "$cand" ]; then
            # Put it next to the engine binary in assets (EngineBootstrap looks
            # for it there first). Also keep a copy in jniLibs so that the
            # Android dynamic linker can still find it via System.loadLibrary.
            mkdir -p "$(dirname "$dest_assets")"
            cp -f "$cand" "$dest_assets"
            mkdir -p "$dest_jnilibs"
            cp -f "$cand" "$dest_jnilibs/libc++_shared.so"
            echo "  OK  $cand  ->  $dest_assets (+ jniLibs copy)"
            return 0
        fi
    done
    echo "  SKIP  libc++_shared.so not provided upstream (using NDK STL instead)"
    return 0
}

# Optional model weights bundled with upstream. Users can always pick their own
# weight file at runtime, so we only copy lightweight defaults (if any) and
# deliberately skip huge (>200MB) net files.
copy_default_models() {
    local src="$SRC_APP/assets/models"
    local dst="$DST_APP/assets/models"
    if [ ! -d "$src" ]; then
        echo "  SKIP  $src (no bundled models)"
        return 0
    fi
    mkdir -p "$dst"
    local copied=0 skipped=0
    while IFS= read -r -d '' f; do
        local sz_mb
        sz_mb=$(du -m "$f" | cut -f1)
        if [ "$sz_mb" -le 80 ]; then
            cp -f "$f" "$dst/"
            copied=$((copied + 1))
        else
            skipped=$((skipped + 1))
        fi
    done < <(find "$src" -maxdepth 1 -type f -print0)
    echo "  OK  $src/*  ->  $dst/ (copied=$copied, skipped=$skipped large files)"
}

echo ""
echo "==> Syncing minimal KataGo engine assets from $SRC_APP"
echo ""
echo "NOTE: SNPE / QNN / OpenCV / SDL2 / TFLite / Python libs under jniLibs are"
echo "      deliberately NOT copied -- BadukNext does not use them at runtime."
echo ""

copy_engine_binary "$DST_APP/assets/engine/libkatago.so" || true
copy_gtp_config    "$DST_APP/assets/engine/default_gtp.cfg" || true
copy_libcxx        "$DST_APP/assets/engine/libc++_shared.so" "$DST_APP/jniLibs/arm64-v8a"
copy_default_models

echo ""
echo "==> Summary"
echo "assets/engine/libkatago.so:      $(ls -lh "$DST_APP/assets/engine/libkatago.so"      2>/dev/null | awk '{print $5}' || echo MISSING)"
echo "assets/engine/default_gtp.cfg:   $(ls -lh "$DST_APP/assets/engine/default_gtp.cfg"   2>/dev/null | awk '{print $5}' || echo MISSING)"
echo "assets/engine/libc++_shared.so:  $(ls -lh "$DST_APP/assets/engine/libc++_shared.so"  2>/dev/null | awk '{print $5}' || echo MISSING)"
echo "assets/models:                   $(find "$DST_APP/assets/models" -maxdepth 1 -type f 2>/dev/null | wc -l | tr -d ' ') files"
echo "jniLibs/arm64-v8a:              $(find "$DST_APP/jniLibs/arm64-v8a" -maxdepth 1 -type f 2>/dev/null | wc -l | tr -d ' ') .so files (should only be libc++_shared.so if any)"
echo ""
echo "Done. Now run:  ./gradlew assembleDebug"
