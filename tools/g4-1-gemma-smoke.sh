#!/usr/bin/env bash
set -euo pipefail

PACKAGE_NAME="com.tino.app"
ACTIVITY_NAME="${PACKAGE_NAME}/.debug.GemmaSmokeActivity"
OUTPUT_DIR="${1:-$(mktemp -d -t tino-g4-1-gemma-XXXXXX)}"
FAILURE_MODE="${2:-none}"

mapfile -t AUTHORIZED_DEVICES < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if [[ "${#AUTHORIZED_DEVICES[@]}" -ne 1 ]]; then
  echo "G4.1 PENDING_DEVICE_VALIDATION: é necessário exatamente um device autorizado." >&2
  adb devices -l >&2
  exit 2
fi

mkdir -p "$OUTPUT_DIR"
SERIAL="${AUTHORIZED_DEVICES[0]}"
ADB=(adb -s "$SERIAL")

echo "Executando Gemma smoke em $SERIAL"
"${ADB[@]}" logcat -c
"${ADB[@]}" shell am force-stop "$PACKAGE_NAME"
START_ARGS=()
if [[ "$FAILURE_MODE" == "kill-gemma" ]]; then
  START_ARGS+=(--ez kill_gemma true)
fi
"${ADB[@]}" shell am start -W -n "$ACTIVITY_NAME" "${START_ARGS[@]}" >"$OUTPUT_DIR/activity-start.txt"

for _ in {1..20}; do
  sleep 1
done

for _ in {1..40}; do
  if "${ADB[@]}" logcat -d -s TinoGemmaSmoke:I '*:S' | rg -q 'GENERATED|UNAVAILABLE|FAILED'; then
    break
  fi
  sleep 1
done

"${ADB[@]}" logcat -d -v threadtime >"$OUTPUT_DIR/logcat.txt"
"${ADB[@]}" shell dumpsys activity exit-info "$PACKAGE_NAME" >"$OUTPUT_DIR/exit-info.txt"
"${ADB[@]}" shell pidof "$PACKAGE_NAME" >"$OUTPUT_DIR/main-pid.txt" || true
"${ADB[@]}" shell pidof "$PACKAGE_NAME:gemma" >"$OUTPUT_DIR/gemma-pid.txt" || true
"${ADB[@]}" logcat -d -s TinoGemmaSmoke:I '*:S' >"$OUTPUT_DIR/result.txt"

if [[ "$FAILURE_MODE" == "kill-gemma" ]] && rg -q 'UNAVAILABLE' "$OUTPUT_DIR/result.txt"; then
  if [[ -s "$OUTPUT_DIR/main-pid.txt" ]]; then
    echo "G4.1 fallback smoke PASS: Gemma caiu e o processo principal permaneceu vivo."
    cat "$OUTPUT_DIR/result.txt"
    echo "Evidências: $OUTPUT_DIR"
    exit 0
  fi
  echo "Fallback retornou, mas o PID principal não foi encontrado."
  exit 1
fi

if [[ "$FAILURE_MODE" == "none" ]] && rg -q 'GENERATED' "$OUTPUT_DIR/result.txt"; then
  echo "G4.1 Gemma smoke PASS: inferência real retornou no processo isolado."
  cat "$OUTPUT_DIR/result.txt"
  echo "Evidências: $OUTPUT_DIR"
  exit 0
fi

echo "G4.1 Gemma smoke não confirmou GENERATED:"
cat "$OUTPUT_DIR/result.txt" || true
echo "Evidências: $OUTPUT_DIR"
exit 1
