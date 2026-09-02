#!/usr/bin/env bash
set -euo pipefail

PACKAGE_NAME="com.tino.app"
ACTIVITY_NAME="${PACKAGE_NAME}/.debug.IntelligenceEvidenceSnapshotSmokeActivity"
OUTPUT_DIR="${1:-$(mktemp -d -t tino-g4-5-evidence-XXXXXX)}"

mapfile -t AUTHORIZED_DEVICES < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if [[ "${#AUTHORIZED_DEVICES[@]}" -ne 1 ]]; then
  echo "G4.5 PENDING_DEVICE_VALIDATION: é necessário exatamente um device autorizado." >&2
  adb devices -l >&2
  exit 2
fi

mkdir -p "$OUTPUT_DIR"
SERIAL="${AUTHORIZED_DEVICES[0]}"
ADB=(adb -s "$SERIAL")

echo "Executando evidence/model-readiness smoke em $SERIAL"
"${ADB[@]}" logcat -c
"${ADB[@]}" shell am force-stop "$PACKAGE_NAME"
"${ADB[@]}" shell am start -W -n "$ACTIVITY_NAME" >"$OUTPUT_DIR/activity-start.txt"

for _ in {1..60}; do
  if "${ADB[@]}" logcat -d -s TinoEvidenceSmoke:I '*:S' | rg -q 'PASS|FAIL'; then
    break
  fi
  sleep 1
done

"${ADB[@]}" logcat -d -v threadtime >"$OUTPUT_DIR/logcat.txt"
"${ADB[@]}" shell dumpsys activity exit-info "$PACKAGE_NAME" >"$OUTPUT_DIR/exit-info.txt"
"${ADB[@]}" logcat -d -s TinoEvidenceSmoke:I '*:S' >"$OUTPUT_DIR/result.txt"

if rg -q 'read_status=PASS' "$OUTPUT_DIR/result.txt"; then
  echo "G4.5 READ_PASS: evidências locais foram lidas sem mutação; consulte model_readiness."
  cat "$OUTPUT_DIR/result.txt"
  echo "Evidências: $OUTPUT_DIR"
  exit 0
fi

echo "G4.5 não confirmou leitura das evidências:"
cat "$OUTPUT_DIR/result.txt" || true
echo "Evidências: $OUTPUT_DIR"
exit 1
