# Builds and original-key signing

## Unsigned branch checks

Pull requests and pushes to `main` or `cleanup/**` run helper/source tests, Android release lint,
release APK assembly and test-APK compilation without signing secrets. The workflow
uses `pull_request`, not `pull_request_target`. Compiling instrumentation is not
running it on an Android device.

The artifact `touch-zoom-unsigned-<commit SHA>` contains:

- `app-release-unsigned.apk`
- `SOURCE_COMMIT.txt`
- `SHA256SUMS`

Lint output is a separate artifact. Check that the run succeeded and that its source
commit matches the branch revision being tested before downloading. Verify the
checksum after extraction. The unsigned APK cannot be installed until signed.
Branch integration and its successful checks do not imply a merge into `main`.

## Sign a downloaded APK locally

Use a trusted computer holding the original private keystore. Keep signing material
outside the repository and outside downloaded artifacts. Align the APK, sign with
that existing key using Android build-tools `apksigner`, then verify the certificate,
alignment, package and version before installation. Never use a different debug key
as a fallback when updating an existing installation.

The original signing-certificate SHA-256 fingerprint is public:

```text
446d153476667dee48a1cfa1588cce34ceeb600c1c143b67c0f63506a18e1b0b
```

Use password prompts or environment-based password inputs, not literal passwords
in commands, shell history or logs. The local signing path does not require uploading
keys to GitHub or changing signing identity. Keep a separate secure backup.

The package is `io.github.kacperla.touchzoom`. This cleanup uses version code **33**
and version name **0.33-repository-cleanup**. Check the installed version and original
certificate before updating. Android may reject downgrades even with the same key.
Uninstalling to work around a mismatch removes local settings.

## Optional manual signing workflow

**Manual signed APK** is restricted to trusted `main` in this repository. It checks
out the exact dispatch commit, tests and builds without secrets, then transfers only
the unsigned APK to a fresh signing runner. The signing runner does not check out
source, execute project scripts or run Gradle. It aligns, signs and verifies the
original certificate before uploading the APK and checksum.

The unsigned handoff expires after one day; signed artifacts after seven days.
No GitHub Release is created. Actions are pinned, token permissions are
`contents: read`, checkout credentials are not persisted and build caches are
disabled. Temporary key material is cleaned up on exit and by an always-run step.

**Public-repository artifacts are not private distribution.** Treat APKs as
accessible to people able to download the workflow artifacts. An encrypted Actions
secret can still be accessed by workflows allowed to use it; trusted workflow
changes and environment protection matter.

### Setup, only if choosing GitHub-hosted signing

Committing the workflow does not configure signing. Before supplying any secrets:

1. Create a `signing` environment restricted to **the `main` branch only**, not tags.
   Add owner review where supported, protect `main`, and review workflow/build changes.
2. Add these environment secrets through GitHub's encrypted secret facility:
   `TOUCH_ZOOM_KEYSTORE_BASE64`, `TOUCH_ZOOM_STORE_PASSWORD`,
   `TOUCH_ZOOM_KEY_ALIAS`, and `TOUCH_ZOOM_KEY_PASSWORD`.
3. Use the existing original key. Base64 is encoding, not encryption. Never paste
   private values into issues, chat, commits, workflow files or artifacts.
4. Check secret names and branch restrictions, review the exact main commit, then
   manually dispatch. Missing or mismatched signing material must fail, not fall back.

The optional `scripts/setup-signing.sh` helper uses authenticated `gh`, Python 3
and JDK `keytool`. It verifies the original certificate, asks before uploading secrets
and refuses to weaken an existing environment's branch policy. It does **not** add
required reviewers or protect `main`. Running it changes GitHub configuration and
is not necessary for local signing. Do not use shell tracing or terminal recording.

A successful build or signature verification does not certify device behavior or
choose a future distribution signing scheme. See [VALIDATION.md](VALIDATION.md).

References: [GitHub encrypted secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets),
[Android apksigner](https://developer.android.com/tools/apksigner).
