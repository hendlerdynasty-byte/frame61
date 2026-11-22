#!/bin/sh
# Signiert ein Release-AAB lokal mit dem Release-Keystore.
#
# Warum lokal: das Signingmaterial bleibt auf dem Mac. Der CI-Bau liefert
# bewusst ein unsigniertes Bundle; erst hier entsteht das signierte AAB.
# AABs werden mit jarsigner (JAR-Signatur v1) signiert, das ist das von
# Play erwartete Format. Das Passwort kommt aus der Keychain und wird
# weder ausgegeben noch in die Datei geschrieben.
#
# Aufruf:
#   scripts/sign_release_aab.sh <unsigned.aab> <signed-output.aab>
set -eu

if [ "$#" -ne 2 ]; then
  printf 'Aufruf: %s <unsigned.aab> <signed-output.aab>\n' "$0" >&2
  exit 2
fi

INPUT=$1
OUTPUT=$2
test -f "$INPUT"
test ! -e "$OUTPUT"
KEYSTORE="$HOME/hendler-dynasty/keys/frame61-release.jks"
test -f "$KEYSTORE"

KS_PASS=$(security find-generic-password -s frame61-keystore-pass -a frame61 -w)
export KS_PASS
mkdir -p "$(dirname "$OUTPUT")"

jarsigner \
  -keystore "$KEYSTORE" \
  -storetype JKS \
  -storepass:env KS_PASS \
  -keypass:env KS_PASS \
  -signedjar "$OUTPUT" \
  "$INPUT" \
  frame61

jarsigner -verify -keystore "$KEYSTORE" -storepass:env KS_PASS "$OUTPUT"
shasum -a 256 "$OUTPUT"
