#!/usr/bin/env bash
set -euo pipefail

PACKAGE_NAME="com.tino.app"
MAIN_ACTIVITY="${PACKAGE_NAME}/.MainActivity"
OUTPUT_DIR="${1:-$(mktemp -d -t tino-g4-1-XXXXXX)}"

mapfile -t AUTHORIZED_DEVICES < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if [[ "${#AUTHORIZED_DEVICES[@]}" -ne 1 ]]; then
  echo "G4.1 PENDING_DEVICE_VALIDATION: é necessário exatamente um device autorizado." >&2
  adb devices -l >&2
  exit 2
fi

mkdir -p "$OUTPUT_DIR"
SERIAL="${AUTHORIZED_DEVICES[0]}"
ADB=(adb -s "$SERIAL")

echo "Captura G4.1 em: $OUTPUT_DIR"
"${ADB[@]}" logcat -c
"${ADB[@]}" shell am force-stop "$PACKAGE_NAME"
"${ADB[@]}" shell am start -W -n "$MAIN_ACTIVITY" >"$OUTPUT_DIR/activity-start.txt"

echo "Agora reproduza o fluxo de voz/crash no device; aguardando 15 segundos..."
sleep 15

"${ADB[@]}" logcat -d -v threadtime >"$OUTPUT_DIR/logcat.txt"
"${ADB[@]}" shell dumpsys activity exit-info "$PACKAGE_NAME" >"$OUTPUT_DIR/exit-info.txt"
"${ADB[@]}" shell pidof "$PACKAGE_NAME" >"$OUTPUT_DIR/pid.txt" || true

rg -n -i "FATAL EXCEPTION|AndroidRuntime|SIGSEGV|Fatal signal|tombstone|SQLiteException|OutOfMemoryError|ANR in|Process: ${PACKAGE_NAME}" \
  "$OUTPUT_DIR/logcat.txt" >"$OUTPUT_DIR/signatures.txt" || true

if [[ -s "$OUTPUT_DIR/signatures.txt" ]]; then
  echo "Assinaturas de falha encontradas: $OUTPUT_DIR/signatures.txt"
else
  echo "Nenhuma assinatura de falha encontrada no intervalo capturado."
fi
echo "Evidências: $OUTPUT_DIR"
