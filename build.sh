#!/usr/bin/env bash
# TuProxy build script — bungkus ./gradlew + cek prasyarat.
# Pakai: ./build.sh [debug|release|clean|install]   (default: debug)
set -euo pipefail

cd "$(dirname "$0")"

MODE="${1:-debug}"
APK_DEBUG="app/build/outputs/apk/debug/app-debug.apk"
APK_RELEASE="app/build/outputs/apk/release/app-release.apk"
APK_RELEASE_APK="/Users/admin/Downloads/TuProxy.apk"

# 1. JDK 17+
if ! command -v java >/dev/null 2>&1; then
  echo "ERROR: java tidak ditemukan. Install JDK 17+." >&2
  exit 1
fi
echo "java: $(java -version 2>&1 | head -1)"

# 2. Android SDK (local.properties > ANDROID_HOME > ANDROID_SDK_ROOT > default macOS)
if [[ ! -f local.properties ]]; then
  SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
  if [[ -d "$SDK" ]]; then
    echo "sdk.dir=$SDK" > local.properties
    echo "local.properties dibuat -> $SDK"
  else
    echo "ERROR: Android SDK tidak ketemu. Set ANDROID_HOME atau isi local.properties." >&2
    exit 1
  fi
fi

# 3. gradlew executable
[[ -x gradlew ]] || chmod +x gradlew 2>/dev/null || true

# 4. Build
case "$MODE" in
  debug)
    ./gradlew :app:assembleDebug --console=plain
    echo "OK: $APK_DEBUG ($(du -h "$APK_DEBUG" | cut -f1))"
    cp "$APK_DEBUG" "$APK_RELEASE_APK"
    ;;
  release)
    ./gradlew :app:assembleRelease --console=plain
    echo "OK: $APK_RELEASE ($(du -h "$APK_RELEASE" | cut -f1))"
    ;;
  clean)
    ./gradlew clean --console=plain
    echo "OK: build dibersihkan."
    ;;
  install)
    ./gradlew :app:installDebug --console=plain
    echo "OK: terinstall di device."
    ;;
  *)
    echo "Pakai: $0 [debug|release|clean|install]" >&2
    exit 2
    ;;
esac
