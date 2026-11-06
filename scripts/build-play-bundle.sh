#!/usr/bin/env bash
# LOCAL preparation only. Never uploads, creates credentials, or enrolls Play App Signing.
set +x
set -euo pipefail
umask 077
cd "$(dirname "$0")/.."
for name in TOUCH_ZOOM_KEYSTORE TOUCH_ZOOM_STORE_PASSWORD TOUCH_ZOOM_KEY_ALIAS TOUCH_ZOOM_KEY_PASSWORD; do
  [[ -n "${!name:-}" ]] || { echo "Missing local signing variable: $name" >&2; exit 1; }
done
[[ -f "$TOUCH_ZOOM_KEYSTORE" ]] || { echo 'Original private keystore is unavailable.' >&2; exit 1; }
for tool in gradle java jarsigner keytool python3; do command -v "$tool" >/dev/null || { echo "Missing tool: $tool" >&2; exit 1; }; done
out="${1:-build/play-preparation}"
mkdir -p "$out"
out="$(cd "$out" && pwd)"
# The build never receives signing material. The release variant remains unsigned by default.
env -u TOUCH_ZOOM_KEYSTORE -u TOUCH_ZOOM_STORE_PASSWORD -u TOUCH_ZOOM_KEY_ALIAS -u TOUCH_ZOOM_KEY_PASSWORD \
  gradle --no-daemon --no-build-cache :app:bundleRelease :app:assembleRelease :app:lintRelease
unsigned='app/build/outputs/bundle/release/app-release.aab'
[[ -s "$unsigned" ]] || { echo 'Release bundle missing.' >&2; exit 1; }
work="$(mktemp -d "$out/.signing-check.XXXXXX")"
trap 'rm -rf "$work"' EXIT
cp "$unsigned" "$work/candidate.aab"
jarsigner -keystore "$TOUCH_ZOOM_KEYSTORE" -storepass:env TOUCH_ZOOM_STORE_PASSWORD \
  -keypass:env TOUCH_ZOOM_KEY_PASSWORD -sigalg SHA256withRSA -digestalg SHA-256 \
  "$work/candidate.aab" "$TOUCH_ZOOM_KEY_ALIAS" > "$work/signing.txt" 2>&1
jarsigner -J-Duser.language=en -J-Duser.country=US -verify "$work/candidate.aab" > "$work/verification.txt" 2>&1
grep -Fq 'jar verified.' "$work/verification.txt" || { echo 'Bundle signature verification failed.' >&2; exit 1; }
keytool -J-Duser.language=en -J-Duser.country=US -printcert -jarfile "$work/candidate.aab" > "$work/certificate.txt"
actual="$(sed -n 's/^[[:space:]]*SHA256: //p' "$work/certificate.txt" | tr -d ':' | tr '[:upper:]' '[:lower:]')"
expected='446d153476667dee48a1cfa1588cce34ceeb600c1c143b67c0f63506a18e1b0b'
[[ "$actual" == "$expected" ]] || { echo 'Signer differs from original prototype; refusing output.' >&2; exit 1; }
java scripts/VerifyBundle.java "$work/candidate.aab" "$expected"
# AAB signing is not APK signing. Keep the reviewed original identity, no key export.
cp "$work/candidate.aab" "$out/touch-zoom-play-preparation.aab"
printf '%s\n' "$actual" > "$out/SIGNER-SHA256.txt"
(cd "$out" && sha256sum touch-zoom-play-preparation.aab > SHA256SUMS)
echo "Signed local release bundle: $out/touch-zoom-play-preparation.aab"
echo 'Not submitted. Confirm Play account, package, app-signing identity and declarations before upload.'
