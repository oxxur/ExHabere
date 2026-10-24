#!/usr/bin/env bash
set -euo pipefail

usage() {
    echo "Usage: $0 [device-serial]" >&2
}

if [[ $# -gt 1 ]]; then
    usage
    exit 2
fi

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-"$HOME/Android/Sdk"}}"
export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"

ADB="$SDK_DIR/platform-tools/adb"
if [[ ! -x "$ADB" ]]; then
    ADB="$(command -v adb || true)"
fi
if [[ -z "$ADB" || ! -x "$ADB" ]]; then
    echo "adb not found; install Android platform-tools or set ANDROID_HOME." >&2
    exit 1
fi

APKSIGNER="$SDK_DIR/build-tools/35.0.0/apksigner"
if [[ ! -x "$APKSIGNER" ]]; then
    echo "apksigner not found at $APKSIGNER; install Android Build-Tools 35.0.0." >&2
    exit 1
fi

if [[ $# -eq 1 ]]; then
    DEVICE_SERIAL="$1"
    DEVICE_STATE="$("$ADB" -s "$DEVICE_SERIAL" get-state 2>/dev/null || true)"
    if [[ "$DEVICE_STATE" != "device" ]]; then
        echo "Android device '$DEVICE_SERIAL' is not connected and authorized." >&2
        exit 1
    fi
else
    DEVICES="$("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
    DEVICE_COUNT="$(printf '%s\n' "$DEVICES" | awk 'NF { count++ } END { print count + 0 }')"
    if [[ "$DEVICE_COUNT" -eq 0 ]]; then
        echo "No connected, authorized Android device found." >&2
        exit 1
    elif [[ "$DEVICE_COUNT" -gt 1 ]]; then
        echo "Multiple devices found; pass the desired device serial." >&2
        exit 1
    fi
    DEVICE_SERIAL="$DEVICES"
fi

DEBUG_KEYSTORE="$HOME/.android/debug.keystore"
if [[ ! -f "$DEBUG_KEYSTORE" ]]; then
    if ! command -v keytool >/dev/null 2>&1; then
        echo "keytool not found; install JDK 17 or newer." >&2
        exit 1
    fi
    mkdir -p "$HOME/.android"
    keytool -genkeypair -v \
        -keystore "$DEBUG_KEYSTORE" \
        -storepass android \
        -alias androiddebugkey \
        -keypass android \
        -dname "CN=Android Debug,O=Android,C=US" \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000
fi

cd "$ROOT_DIR"
./gradlew :app:assembleRelease

UNSIGNED_APK="$ROOT_DIR/app/build/outputs/apk/release/app-release-unsigned.apk"
if [[ ! -f "$UNSIGNED_APK" ]]; then
    echo "Release APK not found at $UNSIGNED_APK." >&2
    exit 1
fi

TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/exhabere-install.XXXXXX")"
SIGNED_APK="$TEMP_DIR/app-release.apk"
cleanup() {
    rm -f "$SIGNED_APK"
    rmdir "$TEMP_DIR" 2>/dev/null || true
}
trap cleanup EXIT

# The project has no distribution signing key; use the local Android debug key
# only so this release variant can be installed directly on a development device.
"$APKSIGNER" sign \
    --ks "$DEBUG_KEYSTORE" \
    --ks-key-alias androiddebugkey \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out "$SIGNED_APK" \
    "$UNSIGNED_APK"

"$ADB" -s "$DEVICE_SERIAL" install -r "$SIGNED_APK"
