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
# APK SHRINK NOTES (2026-08-02):
#   gtp_static.cfg is pure OpenCL backend (openclDeviceToUseThread0=0).
#   0 Java references to com.qualcomm.qti.snpe.* / com.qualcomm.qti.qnn.*
#   => Safe to DROP the entire SNPE (12 so / ~93MB) + QNN (17 so / ~86MB) families.
#   Engine start is Path-B only: assets/libkatago.so → filesDir + exec (no jniLibs
#   libkatago.so needed at all, so we delete that too — saves the single largest
#   duplicate copy).
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
echo "==> PRUNING (shrink v2 — multi-stage, belt & suspenders)"

stage()  { printf '\n--- Stage %s: %s ---\n' "$1" "$2"; }

prune() { rm -fv "$@" || true; }

# ================================================================
# Stage P0-a — KataGo mutually exclusive variant builds
#   KataGoEngine hardcodes BINARY_NAME="libkatago.so".
#   Note jniLibs/libkatago.so ITSELF is ALSO deleted in Stage 3 below
#   (we only use assets/ → files/ path now for 100% start stability).
# ================================================================
stage P0-a "Drop KataGo variants (hardcoded engine uses libkatago.so only)"
prune "$DST_APP/jniLibs/arm64-v8a/libkatago_without_snpe.so"
prune "$DST_APP/jniLibs/arm64-v8a/libkatago_large_boards_without_snpe.so"

# ================================================================
# Stage P0-b — Photo board-recognition (no CAMERA permission → impossible)
# ================================================================
stage P0-b "Drop photo-board-recognition (no CAMERA permission → dead code)"
prune "$DST_APP/jniLibs/arm64-v8a/libopencv_core.so"
prune "$DST_APP/jniLibs/arm64-v8a/libopencv_imgproc.so"
prune "$DST_APP/jniLibs/arm64-v8a/libopencv_imgcodecs.so"
prune "$DST_APP/jniLibs/arm64-v8a/libopencv_features2d.so"
prune "$DST_APP/jniLibs/arm64-v8a/libopencv_flann.so"
prune "$DST_APP/jniLibs/arm64-v8a/libimgToSgf.so"
prune "$DST_APP/jniLibs/arm64-v8a/libhidapi.so"

# ================================================================
# Stage P0-c — SDL family (UI = Compose Canvas, audio = SoundPool)
# ================================================================
stage P0-c "Drop SDL family (UI = Compose, audio = SoundPool)"
prune "$DST_APP/jniLibs/arm64-v8a/libSDL2.so"
prune "$DST_APP/jniLibs/arm64-v8a/libSDL2_image.so"
prune "$DST_APP/jniLibs/arm64-v8a/libSDL2_mixer.so"
prune "$DST_APP/jniLibs/arm64-v8a/libSDL2_ttf.so"

# ================================================================
# Stage P0-d — Python 3.7 runtime (scripts already removed upstream)
# ================================================================
stage P0-d "Drop Python runtime (investigation scripts removed commit 2df2a27)"
prune "$DST_APP/jniLibs/arm64-v8a/libpython3.7m.so"

# ================================================================
# Stage P1-a — SNPE (Qualcomm Snapdragon NPE) 12 .so / ~93MB
#   0 com.qualcomm.qti.snpe.* in Kotlin, and gtp_static.cfg only
#   declares openclDeviceToUseThread0=0 (pure OpenCL backend, no SNPE).
#   Two 67MB giants (libSnpeHtpPrepare) + libSNPE + 8 stubs/hta.
# ================================================================
stage P1-a "DROP SNPE family (12 so / ~93MB): pure OpenCL cfg, 0 SNPE Java refs"
prune "$DST_APP/jniLibs/arm64-v8a/libSnpeHtpPrepare.so"          # 67MB — HTP graph prepare
prune "$DST_APP/jniLibs/arm64-v8a/libSNPE.so"                    # 18MB — top-level runtime
prune "$DST_APP/jniLibs/arm64-v8a/libSnpeDspV66Stub.so"          # 1.5MB
prune "$DST_APP/jniLibs/arm64-v8a/libSnpeHta.so"                  # 959KB
prune "$DST_APP/jniLibs/arm64-v8a/libSnpeHtpV81Stub.so"          # 581KB
prune "$DST_APP/jniLibs/arm64-v8a/libSnpeHtpV79Stub.so"          # 525KB
prune "$DST_APP/jniLibs/arm64-v8a/libSnpeHtpV75Stub.so"          # 525KB
prune "$DST_APP/jniLibs/arm64-v8a/libSnpeHtpV73Stub.so"          # 525KB
prune "$DST_APP/jniLibs/arm64-v8a/libSnpeHtpV69Stub.so"          # 519KB
prune "$DST_APP/jniLibs/arm64-v8a/libSnpeHtpV68Stub.so"          # 519KB
prune "$DST_APP/jniLibs/arm64-v8a/libhta_hexagon_runtime_snpe.so" # 2.3MB
# TFLite runtime itself — also dead because no Java class references
# (com.google.tensorflow.lite / interpreter) 0 hits in repo.
prune "$DST_APP/jniLibs/arm64-v8a/libtensorflowlite.so"          # 2.8MB

# ================================================================
# Stage P1-b — QNN (Qualcomm AI Engine Direct) 17 .so / ~86MB
#   Same evidence: cfg only OpenCL, 0 com.qualcomm.qti.qnn.* in code,
#   NO mention of qnn/htp/dsp backend in gtp_static.cfg at all.
#   libQnnHtpPrepare is another 67MB giant we definitely never touch.
# ================================================================
stage P1-b "DROP QNN family (17 so / ~86MB): no QNN backend / Java API usage"
prune "$DST_APP/jniLibs/arm64-v8a/libQnnHtpPrepare.so"           # 67MB
prune "$DST_APP/jniLibs/arm64-v8a/libQnnCpu.so"                  # 5.9MB
prune "$DST_APP/jniLibs/arm64-v8a/libQnnSystem.so"               # 2.4MB
prune "$DST_APP/jniLibs/arm64-v8a/libQnnHtp.so"                  # 2.4MB
prune "$DST_APP/jniLibs/arm64-v8a/libQnnDsp.so"                  # 1.4MB
prune "$DST_APP/jniLibs/arm64-v8a/libQnnTFLiteDelegate.so"       # 1.0MB
prune "$DST_APP/jniLibs/arm64-v8a/libQnnHta.so"                  # 959KB
prune "$DST_APP/jniLibs/arm64-v8a/libQnnSaver.so"                # 764KB
prune "$DST_APP/jniLibs/arm64-v8a/libQnnHtpV81Stub.so"           # 581KB
prune "$DST_APP/jniLibs/arm64-v8a/libQnnHtpV79Stub.so"           # 525KB
prune "$DST_APP/jniLibs/arm64-v8a/libQnnHtpV75Stub.so"           # 525KB
prune "$DST_APP/jniLibs/arm64-v8a/libQnnHtpV73Stub.so"           # 525KB
prune "$DST_APP/jniLibs/arm64-v8a/libQnnHtpV69Stub.so"           # 519KB
prune "$DST_APP/jniLibs/arm64-v8a/libQnnHtpV68Stub.so"           # 519KB
prune "$DST_APP/jniLibs/arm64-v8a/libQnnDspV66Stub.so"           # 312KB
prune "$DST_APP/jniLibs/arm64-v8a/libQnnIr.so"                   # 15KB
prune "$DST_APP/jniLibs/arm64-v8a/libQnnGpu.so"                  # 15KB (separate OpenCL path via Qnn GPU — not used by KataGo, KataGo has its own OpenCL)

# ================================================================
# Stage P2 — Kill the jniLibs libkatago.so entirely (~5MB)
#   Path-A (jniLibs direct exec) removed from KataGoEngine.kt; only
#   assets/ → files/ copy (Path B) runs. This removes the *duplicate*
#   of assets/libkatago.so that was inflating APK/jniLibs/app-lib 3x.
# ================================================================
stage P2 "DROP jniLibs/libkatago.so (5MB): engine start is assets→filesDir only"
prune "$DST_APP/jniLibs/arm64-v8a/libkatago.so"

# ================================================================
# Stage P3 — Move remaining 5 jniLibs .so into assets/deps/, then DROP jniLibs.
#   0 Java System.loadLibrary() calls in the codebase → no JNI loading ever.
#   The only remaining .so consumers are KataGo child-process dlopen() calls
#   for libc++_shared / libcalculator / libffi / libmain / libcdsprpc.
#   Moving them to assets/deps/ means:
#     • Gradle stops treating them as "jniLibs" → no useLegacyPackaging effect,
#       no auto-extraction to /data/app-lib, NO more inflated install size.
#     • KataGoEngine copies assets/deps/*.so to filesDir on startup, then
#       sets LD_LIBRARY_PATH=filesDir:hexagonDir (no nativeLibraryDir needed).
#     • After this stage, jniLibs/arm64-v8a is EMPTY and can be removed.
# ================================================================
stage P3 "MOVE jniLibs/*.so → assets/deps/*.so, then rm jniLibs (0 jni System.load call in codebase)"
DEST_DEPS="$DST_APP/assets/deps"
mkdir -p "$DEST_DEPS"
for so in "$DST_APP/jniLibs/arm64-v8a"/*.so; do
    if [ -f "$so" ]; then
        base="$(basename "$so")"
        mv -fv "$so" "$DEST_DEPS/$base" || {
            # If files already on different FS, fall back to cp+rm
            cp -fv "$so" "$DEST_DEPS/$base" && rm -fv "$so"
        }
    fi
done
# Remove the now-empty arm64-v8a dir and (if empty) the parent jniLibs dir
rmdir -v "$DST_APP/jniLibs/arm64-v8a" 2>/dev/null || true
rmdir -v "$DST_APP/jniLibs"           2>/dev/null || true
# If any stragglers survive the glob (shouldn't), hammer them with prune
prune "$DST_APP/jniLibs/arm64-v8a"/*.so 2>/dev/null || true

echo ""
echo "==> Summary (after full shrink pipeline)"
echo "assets/libkatago.so:   $(ls -lh "$DST_APP/assets/libkatago.so"    2>/dev/null | awk '{print $5}' || echo MISSING)"
echo "assets/gtp_static.cfg: $(ls -lh "$DST_APP/assets/gtp_static.cfg" 2>/dev/null | awk '{print $5}' || echo MISSING)"
echo "assets/models:         $(find "$DST_APP/assets/models" -maxdepth 1 -type f 2>/dev/null | wc -l | tr -d ' ') files"
deps_count=$(find "$DST_APP/assets/deps" -maxdepth 1 -name "*.so" -type f 2>/dev/null | wc -l | tr -d ' ')
deps_bytes=$(du -cb "$DST_APP/assets/deps"/*.so 2>/dev/null | tail -1 | awk '{print $1}')
deps_mb=$(( deps_bytes / 1048576 ))
echo "assets/deps/ (KataGo ld.so deps): $deps_count .so files = ${deps_mb}MB"
jni_count=$(find "$DST_APP/jniLibs" -name "*.so" -type f 2>/dev/null | wc -l | tr -d ' ')
echo "jniLibs/ .so remaining (should be 0): $jni_count"

echo ""
echo "==> Post-prune top .so sizes (largest first)"
find "$DST_APP" -name "*.so" -exec ls -lh {} \; 2>/dev/null | awk '{print $5, $NF}' | sort -rh | head -20

echo ""
echo "Done. Now run:  ./gradlew assembleDebug"
