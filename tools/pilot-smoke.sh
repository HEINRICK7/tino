#!/usr/bin/env bash
set -euo pipefail

APK_PATH="${1:-app/build/outputs/apk/debug/app-debug.apk}"
PACKAGE_NAME="com.tino.app"
MAIN_ACTIVITY="${PACKAGE_NAME}/.MainActivity"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb não encontrado no PATH." >&2
  exit 1
fi

if [[ ! -f "$APK_PATH" ]]; then
  echo "APK não encontrado: $APK_PATH" >&2
  exit 1
fi

DEVICE_COUNT="$(adb devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')"
if [[ "$DEVICE_COUNT" != "1" ]]; then
  echo "Conecte exatamente um device autorizado. Encontrados: $DEVICE_COUNT" >&2
  adb devices >&2
  exit 1
fi

echo "Device: $(adb shell getprop ro.product.manufacturer) $(adb shell getprop ro.product.model)"
echo "Android: $(adb shell getprop ro.build.version.release) (API $(adb shell getprop ro.build.version.sdk))"
echo "Instalando sem apagar os dados locais..."
adb install -r "$APK_PATH" >/dev/null
adb shell am force-stop "$PACKAGE_NAME"
adb shell am start -n "$MAIN_ACTIVITY" >/dev/null
sleep 3

if ! adb shell pidof "$PACKAGE_NAME" >/dev/null; then
  echo "O processo do TINO não permaneceu ativo." >&2
  exit 1
fi

if adb logcat -d -t 500 | rg -q "FATAL EXCEPTION|Process ${PACKAGE_NAME} has died"; then
  echo "Crash fatal encontrado no logcat recente." >&2
  exit 1
fi

adb shell dumpsys package "$PACKAGE_NAME" | rg "versionName=|versionCode=" | head -2
echo "Smoke básico aprovado: instalação, abertura e processo ativo."
