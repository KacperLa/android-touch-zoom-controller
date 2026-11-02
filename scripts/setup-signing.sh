#!/usr/bin/env bash
# Run locally with an authenticated GitHub CLI. Never enable shell tracing.
set +x
set -euo pipefail
repo='KacperLa/android-touch-zoom-controller'
environment='signing'
expected='446d153476667dee48a1cfa1588cce34ceeb600c1c143b67c0f63506a18e1b0b'
[[ $# == 1 && -f "$1" ]] || { echo "Usage: bash scripts/setup-signing.sh /private/path/original.jks" >&2; exit 1; }
keystore="$1"
for tool in gh python3 keytool base64; do command -v "$tool" >/dev/null || { echo "Missing tool: $tool" >&2; exit 1; }; done
[[ $(gh api user --jq .login) == KacperLa ]] || { echo 'Authenticate gh as KacperLa first.' >&2; exit 1; }
[[ $(gh api "repos/$repo" --jq .permissions.admin) == true ]] || { echo 'Repository admin access required.' >&2; exit 1; }
# Discover without conflating authentication/network failures with a missing environment.
environments=$(gh api "repos/$repo/environments" --paginate --jq '.environments[].name')
if ! grep -Fxq "$environment" <<< "$environments"; then
  printf '%s' '{"deployment_branch_policy":{"protected_branches":false,"custom_branch_policies":true}}' |
    gh api --method PUT "repos/$repo/environments/$environment" --input - >/dev/null
  printf '%s' '{"name":"main","type":"branch"}' |
    gh api --method POST "repos/$repo/environments/$environment/deployment-branch-policies" --input - >/dev/null
fi
policy=$(gh api "repos/$repo/environments/$environment" --jq '.deployment_branch_policy')
python3 -c 'import json,sys;p=json.load(sys.stdin);assert p and p.get("custom_branch_policies") is True and p.get("protected_branches") is False, "Set signing environment to selected main branch only first"' <<< "$policy"
branches=$(gh api "repos/$repo/environments/$environment/deployment-branch-policies?per_page=100")
python3 -c 'import json,sys;p=json.load(sys.stdin);b=p["branch_policies"];assert p["total_count"]==1 and len(b)==1 and b[0]["name"]=="main" and b[0]["type"]=="branch", "Signing environment must allow only branch main (no tags)"' <<< "$branches"
printf 'Target: %s / environment %s (main only).\n' "$repo" "$environment"
read -r -p 'Upload/replace the four encrypted signing secrets? Type YES: ' answer
[[ "$answer" == YES ]] || { echo 'Canceled; no secrets uploaded.'; exit 1; }
read -r -s -p 'Original keystore password: ' STORE_PASSWORD; printf '\n'
read -r -s -p 'Original key alias: ' KEY_ALIAS; printf '\n'
read -r -s -p 'Original key password: ' KEY_PASSWORD; printf '\n'
trap 'unset STORE_PASSWORD KEY_ALIAS KEY_PASSWORD' EXIT
[[ -n "$STORE_PASSWORD" && -n "$KEY_ALIAS" && -n "$KEY_PASSWORD" ]] || { echo 'All values are required.' >&2; exit 1; }
export STORE_PASSWORD
fingerprint=$(keytool -exportcert -keystore "$keystore" -alias "$KEY_ALIAS" -storepass:env STORE_PASSWORD 2>/dev/null |
  python3 -c 'import sys,hashlib;print(hashlib.sha256(sys.stdin.buffer.read()).hexdigest())')
[[ "$fingerprint" == "$expected" ]] || { echo 'Wrong signing certificate; no secrets uploaded.' >&2; exit 1; }
# GitHub CLI encrypts each value before transmission; do not use --body arguments.
base64 < "$keystore" | gh secret set TOUCH_ZOOM_KEYSTORE_BASE64 --repo "$repo" --env "$environment"
printf '%s' "$STORE_PASSWORD" | gh secret set TOUCH_ZOOM_STORE_PASSWORD --repo "$repo" --env "$environment"
printf '%s' "$KEY_ALIAS" | gh secret set TOUCH_ZOOM_KEY_ALIAS --repo "$repo" --env "$environment"
printf '%s' "$KEY_PASSWORD" | gh secret set TOUCH_ZOOM_KEY_PASSWORD --repo "$repo" --env "$environment"
unset STORE_PASSWORD KEY_ALIAS KEY_PASSWORD
# Names only: this does not prove the encrypted values match until signing succeeds.
gh secret list --repo "$repo" --env "$environment"
echo 'Secret uploads completed. Review environment protection and run Manual signed APK on main.'
