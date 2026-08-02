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

# ---- helper functions — MUST be defined BEFORE first stage() call (line 111) ----
stage()  { printf '\n--- Stage %s: %s ---\n' "$1" "$2"; }
prune() { rm -fv "$@" || true; }

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

# ====================================================================
# Stage P0-runtimecfg — ANDROID RUNTIME-SAFE gtp_static.cfg OVERRIDE
#   2026-08-02 — 20+ builds exit=0 empty stderr root-cause #1 & #2
#     P0-runtimecfg-A: logToStderr=false → true.
#       When Katago hits any init error (OpenCL dlopen failure, wrong
#       HOME, DSP init, bad model), it writes to KATA_LOG_FILE or
#       ~/.katago/katago.log — BOTH LOCATIONS WE CANNOT READ IN
#       ProcessBuilder stderr. Setting logToStderr=true pipes ALL
#       init errors DIRECTLY to process.errorStream so the user's
#       diagnostic toast shows the real problem. This was the #1
#       reason all 20 toasts said "stderr = empty".
#
#     P0-runtimecfg-B: Comment out all openclDeviceToUseThread* force
#       assignments. When Katago's default OpenCL probe finds no
#       usable GPU ICD on non-Qualcomm/older-Mali/Google Tensor phones
#       it aborts nn init before GTP server start. By NOT forcing a
#       specific OpenCL device index, Katago falls back to the FIRST
#       nnBackend that successfully initializes (CPU / TFLite-CPU /
#       etc.), which works on all Android SoCs.
# ====================================================================
stage P0-runtimecfg "Patch gtp_static.cfg for Android runtime reliability (logToStderr=true, remove forced OpenCL device pins)"
CFG="$DST_APP/assets/gtp_static.cfg"
if [ -f "$CFG" ]; then
    # P0-runtimecfg-A
    sed -i.bak -E 's/^([[:space:]]*logToStderr[[:space:]]*=[[:space:]]*)false[[:space:]]*$/\1true/' "$CFG" && rm -f "$CFG.bak"
    # P0-runtimecfg-B: comment all openclDeviceToUseThread lines; keep
    # line as documentation but do NOT force a device index.
    sed -i.bak -E 's/^([[:space:]]*)(openclDeviceToUseThread[0-9]+[[:space:]]*=.*)$/# RUNTIME-PATCH: no forced-OpenCL-device (CPU fallback enabled)\n#\1\2/' "$CFG" 2>/dev/null && rm -f "$CFG.bak" || true
    # If above multi-line sed did not run (e.g., old sed), do plain line-by-line comment:
    grep -Eq '^[[:space:]]*openclDeviceToUseThread' "$CFG" && {
        awk '$0 ~ /^[[:space:]]*openclDeviceToUseThread/ { print "# RUNTIME-PATCH: openclDevice force-assign commented to allow CPU fallback\n#" $0; next }1' "$CFG" > "${CFG}.new" && mv -v "${CFG}.new" "$CFG"
    } || true
    echo "  After patch — $(grep -E '^logToStderr|^openclDeviceToUse' "$CFG" || echo "(no unpatched lines ✓)")"
    grep -qE '^[[:space:]]*logToStderr[[:space:]]*=[[:space:]]*true' "$CFG" && echo "  [OK] logToStderr=true confirmed in patched cfg"
fi

echo ""
echo "==> Copying jniLibs/arm64-v8a from $SRC_APP"
copy_dir "$SRC_APP/jniLibs/arm64-v8a"       "$DST_APP/jniLibs/arm64-v8a"

echo ""
echo "==> PRUNING (shrink v2 — multi-stage, belt & suspenders)"

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
# Stage P1-a — KEEP SNPE + TFLite family (12 so / ~93MB) — REVERTED 2026-08-02
#
#   FATAL LESSON (2026-08-02):
#   libkatago.so's ELF header declares DT_NEEDED on BOTH libSNPE.so AND
#   libtensorflowlite.so — confirmed with `readelf -d libkatago.so | grep NEEDED`.
#   If either is missing, the Android dynamic linker (/system/bin/linker64)
#   REFUSES to even enter main():
#
#       CANNOT LINK EXECUTABLE "/data/app/.../lib/arm64/libkatago.so": \
#         library "libSNPE.so" not found
#
#   This manifests to the user as a generic "Failed to start AI" toast no
#   matter how many launch plans we try. Even though gtp_static.cfg only
#   selects OpenCL (openclDeviceToUseThread0=0) at runtime — ELF NEEDED is
#   load-time, not runtime. Every NEEDED entry MUST be satisfied before
#   a single instruction of main() runs.
#
#   Cost: keeps ~93MB of jniLibs inside APK. This is why the original
#   (working) APK weighed ~220MB installed; the 14.7MB "shrink success"
#   build was simply not loadable by the linker.
#
#   Subset kept (all 12 upstream):
#     libSnpeHtpPrepare.so (67MB  —  HTP graph prepare, dlopen'd by libSNPE.so)
#     libSNPE.so           (18MB  —  NEEDED by libkatago.so)
#     libtensorflowlite.so (2.8MB —  NEEDED by libkatago.so)
#     libSnpeDspV66Stub.so / libSnpeHta.so
#     libSnpeHtpV{68,69,73,75,79,81}Stub.so
#     libhta_hexagon_runtime_snpe.so
# ================================================================
stage P1-a "REVERTED: KEEP SNPE + TFLite family (12 so) — libkatago NEEDED them at ELF load-time"
echo "  (no-op: keeping libSNPE.so + libtensorflowlite.so + 10 stubs/hta in jniLibs/arm64-v8a)"

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
# Stage P2 — KEEP jniLibs/libkatago.so (DO NOT DELETE) — REVERTED
#   Reverted 2026-08-02 after "Failed to start AI" on 14.7MB build.
#   Keeping both assets/libkatago.so (copy source) AND jniLibs/libkatago.so
#   (system nativeLibraryDir fallback) maximizes AI launch reliability even
#   if it costs ~5MB of APK size. Duplication is acceptable for stability.
# ================================================================
stage P2 "REVERTED: KEEP jniLibs/libkatago.so for launch reliability"
echo "  (no-op: keeping jniLibs/libkatago.so in place)"

# ================================================================
# Stage P3 — KEEP remaining jniLibs .so IN jniLibs (DO NOT MOVE) — REVERTED
#   Reverted 2026-08-02 after "Failed to start AI" on 14.7MB build.
#   The 5 remaining deps (libc++_shared, libcalculator, libffi, libmain,
#   libcdsprpc) are left inside jniLibs/arm64-v8a so Gradle packages them
#   as bona fide JNI libs. With extractNativeLibs=true the PackageManager
#   extracts them to /data/app-lib, which is on the linker's standard
#   native-library search path and is GUARANTEED to be readable +
#   dlopen()able by any child process we exec from our own UID.
#   This is the conservative choice: ~1.2MB of extra on-disk footprint
#   but removes the entire "copy deps to filesDir and manage LD_LIBRARY_PATH"
#   variable-setting surface area that most likely broke launch.
# ================================================================
stage P3 "REVERTED: KEEP 5 jniLibs deps in jniLibs (no assets/deps move)"
echo "  (no-op: keeping jniLibs/arm64-v8a/*.so in place)"


# ================================================================
# Stage P4 — ONLY SHIP 6b inside assets/models (user explicitly
#            asked for "6b bundled inside APK"). Upstream badukai
#            ships two models: 6b txt.gz (4.97MB) + 10b bin
#            (12MB). 10b is never referenced by Kotlin code, never
#            selected by the UI, and was inflating the APK by 12MB
#            of dead weight. Remove everything in assets/models
#            that isn't our 6b baseline (either .txt.gz OR .txt —
#            aapt2 sometimes decompresses gz entries during
#            packaging so both forms can exist).
#
# 2026-08-02 AAPT2 WORKAROUND — after pruning we NORMALIZE the
#   shipped 6b form to a single file named:
#     kata1-b6c96-s175395328-d26788732.bin
#   Why? Because aapt2 has a hard-coded "if entry suffix == .gz
#   then inflate it during packaging" behaviour that ignores our
#   aaptOptions.noCompress+=gz half the time and silently rewrites
#   the entry to .txt (12.4MB instead of 4.97MB). *.bin is already
#   in aaptOptions.noCompress and aapt2 has no special case for it,
#   so the byte-for-byte identical gzip payload stays STORED at
#   exactly 4,967,720 bytes. ModelManager probes for the *.bin form
#   first (highest priority), then falls back to *.gz / *.txt so
#   older builds still work. Rules:
#     - if we have .txt.gz (4.97MB): mv directly to .bin
#     - if we have .txt    (12.4MB): gzip -9 to .gz then mv .gz → .bin
#   After normalize the directory contains EXACTLY ONE 6b entry
#   (.bin) and nothing else.
# ================================================================
stage P4 "PRUNE assets/models to ONLY 6b + RENAME to .bin (aapt2 gz-decompress workaround)"
MODELS_DIR="$DST_APP/assets/models"
SHIPPED_BIN="kata1-b6c96-s175395328-d26788732.bin"
if [ -d "$MODELS_DIR" ]; then
    # Acceptable names for the shipped 6b net (gz form + plaintext form).
    keep_gz="kata1-b6c96-s175395328-d26788732.txt.gz"
    keep_txt="kata1-b6c96-s175395328-d26788732.txt"
    kept=0
    removed=0
    ( cd "$MODELS_DIR" && find . -maxdepth 1 -type f -print0 | while IFS= read -r -d '' f; do
        name="${f#./}"
        if [ "$name" = "$keep_gz" ] || [ "$name" = "$keep_txt" ] || [ "$name" = "$SHIPPED_BIN" ]; then
            sz=$(stat -c '%s' "$name" 2>/dev/null || echo 0)
            echo "  KEEP $name ($sz bytes)"
            kept=$((kept+1))
        else
            sz=$(stat -c '%s' "$name" 2>/dev/null || echo 0)
            rm -fv "$name"
            echo "  REMOVE $name ($sz bytes — not 6b shipped baseline)"
            removed=$((removed+1))
        fi
    done ; echo "  Summary P4 (prune): assets/models kept=$kept removed=$removed" )

    # ---------- NORMALIZE → single .bin entry (aapt2 workaround) ----------
    ( cd "$MODELS_DIR" && \
      has_bin=0; has_gz=0; has_txt=0
      [ -f "$SHIPPED_BIN" ] && has_bin=1
      [ -f "$keep_gz"    ] && has_gz=1
      [ -f "$keep_txt"   ] && has_txt=1
      echo "  P4 normalize: before state — bin=$has_bin gz=$has_gz txt=$has_txt"

      if [ "$has_bin" -eq 0 ]; then
          if   [ "$has_gz"  -eq 1 ]; then
              echo "  NORMALIZE mv $keep_gz -> $SHIPPED_BIN (identical gzip byte payload, rename defeats aapt2 .gz handler)"
              mv -v  "$keep_gz" "$SHIPPED_BIN"
          elif [ "$has_txt" -eq 1 ]; then
              echo "  NORMALIZE $keep_txt (plaintext 12.4MB) -> gzip -9 -> $SHIPPED_BIN (≈4.97MB STORED gz, aapt2 safe)"
              gzip -9 -c "$keep_txt" > "$SHIPPED_BIN.tmpgz" && mv -v "$SHIPPED_BIN.tmpgz" "$SHIPPED_BIN" && rm -fv "$keep_txt"
          else
              echo "  NORMALIZE SKIP: no gz/txt/bin form present in $MODELS_DIR (this will fail later — ensure upstream sync actually copied the 6b weights)"
          fi
      else
          echo "  NORMALIZE SKIP: $SHIPPED_BIN already present and is canonical shipped form"
      fi

      # Cleanup any leftover gz/txt — canonical form is .bin only.
      [ -f "$keep_gz" ] && { echo "  CLEANUP removing $keep_gz (canonical form is $SHIPPED_BIN now)" ; rm -fv "$keep_gz" ; }
      [ -f "$keep_txt" ] && { echo "  CLEANUP removing $keep_txt (canonical form is $SHIPPED_BIN now)" ; rm -fv "$keep_txt" ; }

      echo "  P4 normalize final assets/models:"
      find . -maxdepth 1 -type f -printf '  %12s %f\n' 2>/dev/null || true
    )
fi

echo ""
echo "==> Summary (after shrink pipeline — P0/P1/P4 only, P2/P3 reverted for AI launch reliability)"
echo "assets/libkatago.so:   $(ls -lh "$DST_APP/assets/libkatago.so"    2>/dev/null | awk '{print $5}' || echo MISSING)"
echo "assets/gtp_static.cfg: $(ls -lh "$DST_APP/assets/gtp_static.cfg" 2>/dev/null | awk '{print $5}' || echo MISSING)"
# NOTE: `find … | wc -l | tr -d ' '` wrapped with `{ find … || true; }` because
# `set -euo pipefail` turns a non-zero exit from *any* pipeline member into a
# script-wide fatal error. Keeping the guard is still useful (belt & suspenders)
# even though P3 no longer removes jniLibs.
echo "assets/models (P4=only 6b allowed): $( { find "$DST_APP/assets/models" -maxdepth 1 -type f -printf '  %12s %f\n' 2>/dev/null || true; } | sort)"
# assets/deps is intentionally UNUSED (empty / missing) after P3 revert.
deps_count=$( { find "$DST_APP/assets/deps" -maxdepth 1 -name "*.so" -type f 2>/dev/null || true; } | wc -l | tr -d ' ' )
echo "assets/deps/ (UNUSED after P3 revert): $deps_count .so files"
# jniLibs should now have 6 .so: libkatago.so + 5 ld.so deps
jni_count=$( { find "$DST_APP/jniLibs" -name "*.so" -type f 2>/dev/null || true; } | wc -l | tr -d ' ' )
jni_bytes=$( { find "$DST_APP/jniLibs" -name "*.so" -type f -print0 2>/dev/null || true; } \
  | xargs -0 -r du -cb 2>/dev/null | tail -1 | awk '{print $1}')
jni_bytes="${jni_bytes:-0}"
[[ "$jni_bytes" =~ ^[0-9]+$ ]] || jni_bytes=0
jni_mb=$(( jni_bytes / 1048576 ))
echo "jniLibs/ .so remaining (expect 6: libkatago + 5 deps): $jni_count files = ${jni_mb}MB"

echo ""
echo "==> Post-prune top .so sizes (largest first)"
{ find "$DST_APP" -name "*.so" -exec ls -lh {} \; 2>/dev/null || true; } | awk '{print $5, $NF}' | sort -rh | head -20

echo ""
echo "Done. Now run:  ./gradlew assembleDebug"
