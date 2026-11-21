#!/bin/sh
set -eu

if [ "$#" -ne 2 ]; then
  printf 'Aufruf: %s <unsigned.apk> <signed-output.apk>\n' "$0" >&2
  exit 2
fi

INPUT=$1
OUTPUT=$2
test -f "$INPUT"
test ! -e "$OUTPUT"
ANDROID_HOME=${ANDROID_HOME:-$HOME/Library/Android/sdk}
ZIPALIGN="$ANDROID_HOME/build-tools/36.0.0/zipalign"
APKSIGNER="$ANDROID_HOME/build-tools/36.0.0/apksigner"
KEYSTORE="$HOME/hendler-dynasty/keys/frame61-release.jks"
test -x "$ZIPALIGN"
test -x "$APKSIGNER"
test -f "$KEYSTORE"

KS_PASS=$(security find-generic-password -s frame61-keystore-pass -a frame61 -w)
export KS_PASS
ALIGNED="${OUTPUT}.aligned.tmp"
trap 'rm -f "$ALIGNED"' EXIT INT TERM
mkdir -p "$(dirname "$OUTPUT")"
"$ZIPALIGN" -f -p 4 "$INPUT" "$ALIGNED"
"$APKSIGNER" sign \
  --ks "$KEYSTORE" \
  --ks-key-alias frame61 \
  --ks-pass env:KS_PASS \
  --key-pass env:KS_PASS \
  --out "$OUTPUT" "$ALIGNED"
"$APKSIGNER" verify --print-certs "$OUTPUT"
shasum -a 256 "$OUTPUT"
