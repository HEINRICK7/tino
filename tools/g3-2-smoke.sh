#!/usr/bin/env bash
set -euo pipefail

APK_PATH="${1:-app/build/outputs/apk/debug/app-debug.apk}"
PACKAGE_NAME="com.tino.app"
MAIN_ACTIVITY="${PACKAGE_NAME}/.MainActivity"

if [[ ! -f "$APK_PATH" ]]; then
  echo "APK não encontrado: $APK_PATH" >&2
  exit 1
fi

mapfile -t AUTHORIZED_DEVICES < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if [[ "${#AUTHORIZED_DEVICES[@]}" -ne 1 ]]; then
  echo "G3.2 PENDING_DEVICE_VALIDATION: é necessário exatamente um device autorizado." >&2
  adb devices -l >&2
  echo "Desbloqueie o aparelho e aceite a chave de depuração USB." >&2
  exit 2
fi

SERIAL="${AUTHORIZED_DEVICES[0]}"
ADB=(adb -s "$SERIAL")

echo "Device: $(${ADB[@]} shell getprop ro.product.manufacturer) $(${ADB[@]} shell getprop ro.product.model)"
echo "Android: $(${ADB[@]} shell getprop ro.build.version.release) (API $(${ADB[@]} shell getprop ro.build.version.sdk))"
echo "Instalando sem apagar os dados locais..."
"${ADB[@]}" install -r "$APK_PATH" >/dev/null
"${ADB[@]}" shell am force-stop "$PACKAGE_NAME"
# Ignore crashes from previous launches; only this smoke run is evidence.
"${ADB[@]}" logcat -c
"${ADB[@]}" shell am start -W -n "$MAIN_ACTIVITY" >/dev/null

for _ in 1 2 3 4 5; do
  if ! "${ADB[@]}" shell pidof "$PACKAGE_NAME" >/dev/null; then
    echo "G3.2 DEVICE_VALIDATION_FAILED: processo do TINO não permaneceu ativo." >&2
    exit 1
  fi
  if "${ADB[@]}" logcat -d -t 500 | rg -q "FATAL EXCEPTION|Process: ${PACKAGE_NAME}|Process ${PACKAGE_NAME} has died"; then
    echo "G3.2 DEVICE_VALIDATION_FAILED: crash fatal encontrado no logcat recente." >&2
    exit 1
  fi
  sleep 1
done

"${ADB[@]}" shell dumpsys package "$PACKAGE_NAME" | rg "versionName=|versionCode=" | head -2
echo "G3.2 PASS_FULL: instalação, abertura e processo ativo."
