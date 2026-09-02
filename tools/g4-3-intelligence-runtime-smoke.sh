#!/usr/bin/env bash
set -euo pipefail

PACKAGE_NAME="com.tino.app"
ACTIVITY_NAME="${PACKAGE_NAME}/.debug.IntelligenceRuntimeSmokeActivity"
OUTPUT_DIR="${1:-$(mktemp -d -t tino-g4-3-runtime-XXXXXX)}"
INPUT="${2:-qual produto tem o menor estoque?}"

mapfile -t AUTHORIZED_DEVICES < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if [[ "${#AUTHORIZED_DEVICES[@]}" -ne 1 ]]; then
  echo "G4.3 PENDING_DEVICE_VALIDATION: é necessário exatamente um device autorizado." >&2
  adb devices -l >&2
  exit 2
fi

mkdir -p "$OUTPUT_DIR"
SERIAL="${AUTHORIZED_DEVICES[0]}"
ADB=(adb -s "$SERIAL")

echo "Executando Intelligence Runtime smoke em $SERIAL"
"${ADB[@]}" logcat -c
"${ADB[@]}" shell am force-stop "$PACKAGE_NAME"
if [[ "$INPUT" == "qual produto tem o menor estoque?" ]]; then
  "${ADB[@]}" shell am start -W -n "$ACTIVITY_NAME" >"$OUTPUT_DIR/activity-start.txt"
else
  # adb shell receives one argument per token; encode spaces for custom
  # probes and let the activity decode them before creating the request.
  ENCODED_INPUT="${INPUT// /%20}"
  "${ADB[@]}" shell am start -W -n "$ACTIVITY_NAME" --es input "$ENCODED_INPUT" >"$OUTPUT_DIR/activity-start.txt"
fi

for _ in {1..120}; do
  if "${ADB[@]}" logcat -d -s TinoIntelligenceSmoke:I '*:S' | rg -q 'PASS|FAIL'; then
    break
  fi
  sleep 1
done

"${ADB[@]}" logcat -d -v threadtime >"$OUTPUT_DIR/logcat.txt"
"${ADB[@]}" shell dumpsys activity exit-info "$PACKAGE_NAME" >"$OUTPUT_DIR/exit-info.txt"
"${ADB[@]}" shell pidof "$PACKAGE_NAME" >"$OUTPUT_DIR/main-pid.txt" || true
"${ADB[@]}" logcat -d -s TinoIntelligenceSmoke:I '*:S' >"$OUTPUT_DIR/result.txt"

if rg -q 'PASS' "$OUTPUT_DIR/result.txt" && rg -q 'status=ANSWERED' "$OUTPUT_DIR/result.txt"; then
  echo "G4.3 PASS: Intelligence Runtime consultou fatos locais e respondeu sem mutação."
  cat "$OUTPUT_DIR/result.txt"
  echo "Evidências: $OUTPUT_DIR"
  exit 0
fi

echo "G4.3 não confirmou resposta ANSWERED:"
cat "$OUTPUT_DIR/result.txt" || true
echo "Evidências: $OUTPUT_DIR"
exit 1
