#!/usr/bin/env bash
# Frame61 Preflight — schnell, offline, ohne Gradle.
#
# Vor jedem Block: scripts/preflight.sh
# Der volle Build-Gate laeuft remote bzw. ueber ./gradlew gate:
#   :app:assembleDebug + :app:lint + Unit-Tests + Pack-Validator + APK-Groesse.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

green=0
red=0
warn=0

ok()   { printf '  OK    %s\n' "$1"; green=$((green + 1)); }
bad()  { printf '  FAIL  %s\n' "$1"; red=$((red + 1)); }
note() { printf '  WARN  %s\n' "$1"; warn=$((warn + 1)); }

echo "Frame61 Preflight — $ROOT"

# --- Toolchain -------------------------------------------------------------
if command -v java >/dev/null 2>&1; then
  java_major="$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)"
  if [ "$java_major" = "17" ]; then ok "JDK 17"; else bad "JDK 17 erwartet, gefunden: ${java_major:-unbekannt}"; fi
else
  bad "java nicht im PATH"
fi

if [ -x ./gradlew ]; then ok "gradlew ausfuehrbar"; else bad "gradlew fehlt oder ist nicht ausfuehrbar"; fi

sdk_dir=""
if [ -f local.properties ]; then
  sdk_dir="$(sed -n 's/^sdk\.dir=//p' local.properties | head -1)"
fi
if [ -z "$sdk_dir" ] && [ -n "${ANDROID_HOME:-}" ]; then sdk_dir="$ANDROID_HOME"; fi
if [ -n "$sdk_dir" ] && [ -d "$sdk_dir" ]; then
  ok "Android SDK: $sdk_dir"
  if [ -d "$sdk_dir/platforms/android-36" ]; then ok "SDK-Plattform android-36"; else bad "SDK-Plattform android-36 fehlt"; fi
  if [ -d "$sdk_dir/build-tools/36.0.0" ]; then ok "Build-Tools 36.0.0"; else note "Build-Tools 36.0.0 nicht gefunden (AGP kann ausweichen)"; fi
else
  bad "Android SDK nicht gefunden (local.properties/ANDROID_HOME)"
fi

if command -v python3 >/dev/null 2>&1; then ok "python3"; else bad "python3 fehlt (Pack-Validator)"; fi

# --- Assets und Vertrag ----------------------------------------------------
for dir in app/src/main/assets/sources app/src/main/assets/hosters app/src/main/assets/skins app/src/main/assets/catalog app/src/main/assets/meta; do
  if [ -d "$dir" ] && [ -n "$(find "$dir" -type f -name '*.json' -print -quit)" ]; then
    ok "Assets: $dir"
  else
    bad "Assets fehlen oder leer: $dir"
  fi
done

for doc in docs/SOURCE_PACK.md docs/SCHEMA.md docs/SKINS.md docs/STATE.md docs/AUDIT.md docs/STAND.md; do
  if [ -f "$doc" ]; then ok "Doku: $doc"; else bad "Doku fehlt: $doc"; fi
done

if command -v python3 >/dev/null 2>&1; then
  if python3 tools/validate_packs.py --root "$ROOT"; then
    ok "Pack-/Asset-Validator"
  else
    bad "Pack-/Asset-Validator (Details oben)"
  fi
  if python3 -m unittest discover -s tools/fetch -p 'test_*.py' >/dev/null 2>&1; then
    ok "Fetch-Werkzeug-Tests"
  else
    bad "Fetch-Werkzeug-Tests"
  fi
fi

# --- Code-Regeln -----------------------------------------------------------
bang="$(grep -rn '!!' --include='*.kt' app core player recs scraper sources 2>/dev/null | wc -l | tr -d ' ')"
if [ "$bang" = "0" ]; then ok "kein !!-Operator"; else bad "$bang Vorkommen von !! in Kotlin-Quellen"; fi

if grep -q 'tasks.register("gate")' build.gradle.kts 2>/dev/null; then
  ok "Gradle-Task 'gate' registriert"
else
  bad "Gradle-Task 'gate' fehlt in build.gradle.kts"
fi

if grep -q 'checkApkSize' build.gradle.kts 2>/dev/null; then
  ok "APK-Groessenpruefung im Gate"
else
  bad "APK-Groessenpruefung fehlt"
fi

if grep -q 'validatePacks' build.gradle.kts 2>/dev/null; then
  ok "Pack-Validator im Gate"
else
  bad "Pack-Validator nicht im Gate"
fi

keys="$(grep -rEn '(api[_-]?key|bearer |AIza[0-9A-Za-z_-]{10,}|sk-[A-Za-z0-9]{16,})' \
  --include='*.kt' --include='*.json' app core player recs scraper sources 2>/dev/null \
  | grep -v '/build/' | grep -vi 'apikey.*verification\|keyless\|keine api' | wc -l | tr -d ' ')"
if [ "$keys" = "0" ]; then ok "keine eingebetteten API-Keys"; else bad "$keys Verdachtsstellen fuer API-Keys"; fi

# --- Git-Zustand (nur Hinweis) --------------------------------------------
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  branch="$(git branch --show-current)"
  dirty="$(git status --porcelain | wc -l | tr -d ' ')"
  if [ "$dirty" = "0" ]; then ok "Git sauber ($branch)"; else note "Git nicht sauber ($branch, $dirty Aenderungen) — Build bindet den HEAD"; fi
  ok "HEAD $(git rev-parse --short HEAD)"
else
  note "kein Git-Repository"
fi

echo
echo "Ergebnis: $green OK, $red FAIL, $warn WARN"
if [ "$red" -gt 0 ]; then
  echo "PREFLIGHT ROT — vor dem Block reparieren."
  exit 1
fi
echo "PREFLIGHT GRUEN — Gate: ./gradlew gate (remote auf contabo-build)."
exit 0
