#!/usr/bin/env bash
set -euo pipefail

APK_PATH="${1:-app/build/outputs/apk/debug/app-debug.apk}"
PACKAGE_NAME="com.tino.app"
ACTIVITY_NAME="${PACKAGE_NAME}/.debug.AttentionNotificationSmokeActivity"

if [[ ! -f "$APK_PATH" ]]; then
  echo "APK não encontrado: $APK_PATH" >&2
  exit 1
fi

mapfile -t AUTHORIZED_DEVICES < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if [[ "${#AUTHORIZED_DEVICES[@]}" -ne 1 ]]; then
  echo "G4.2 PENDING_DEVICE_VALIDATION: é necessário exatamente um device autorizado." >&2
  adb devices -l >&2
  exit 2
fi

SERIAL="${AUTHORIZED_DEVICES[0]}"
ADB=(adb -s "$SERIAL")

echo "Instalando smoke de notificação sem apagar dados locais..."
"${ADB[@]}" install -r "$APK_PATH" >/dev/null
"${ADB[@]}" shell am force-stop "$PACKAGE_NAME"
"${ADB[@]}" shell am start -W -n "$ACTIVITY_NAME" >/dev/null
sleep 2

if ! "${ADB[@]}" shell dumpsys notification --noredact | rg -q 'TINO percebeu algo|Teste de atenção'; then
  echo "G4.2 DEVICE_VALIDATION_FAILED: notificação não encontrada." >&2
  exit 1
fi

echo "G4.2 PASS: notificação local publicada no Android; dados do comércio preservados."
"${ADB[@]}" shell dumpsys notification --noredact | rg 'TINO percebeu algo|Teste de atenção|tino-attention' | head -20
