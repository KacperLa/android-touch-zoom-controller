# CI and original-key signing

## What runs

- **Unsigned checks**: pull requests and pushes to `main` run helper tests, Android
  lint, and an **unsigned release** build. No signing secrets are referenced and no
  APK is uploaded. This uses `pull_request`, never `pull_request_target`.
- **Manual signed APK**: manually dispatch on `main` in this repository only.
  It checks out the dispatch commit SHA, tests and builds without secrets, then
  passes only the unsigned APK to a **fresh signing runner**. The signing job does
  not check out source or execute Gradle/project scripts. It aligns, signs, verifies
  the original certificate, and retains the APK and SHA-256 checksum for seven days.
  The unsigned handoff artifact expires after one day. No GitHub Release is created.

Actions are pinned to full commit hashes, token permissions are `contents: read`,
checkout credentials are not persisted, and build caches are disabled. The key is
written only to a temporary private directory, removed on exit and in an always-run
cleanup step. Passwords are passed through environment variables, not command-line
values. Only APK/checksum paths are uploaded, never the key or diagnostic logs.

**Artifacts on a public repository are not private distribution.** Treat the built
APK as publicly accessible to people able to download the workflow artifacts.
A signing key embedded in an encrypted Actions secret is still accessible to any
workflow that is allowed to use that secret; trusted workflow changes remain vital.

## Required setup (not performed by committing these files)

### Optional local setup helper

With the original keystore already on your own trusted computer, JDK `keytool`,
Python 3, and `gh` securely authenticated as **KacperLa**, run:

```sh
bash scripts/setup-signing.sh /private/path/original.jks
```

This helper targets only this repository. It creates `signing` with a main-only
branch policy if absent; if an existing environment permits anything else, it
stops without weakening that policy. It asks before secret upload, reads passwords
without echoing, verifies the original certificate, and streams the four values
into GitHub CLI encryption without storing a base64/password file. It never adds
keys to Git. It does **not** add a required reviewer or protect `main`; configure
those in Settings. Do not run it under a terminal recorder or shell tracing.
If an upload fails partway through, rerun to replace all four values consistently.
Seven mocked success/failure scenarios passed; real secret upload remains untested.

### Manual alternative and protection checklist

1. Create the GitHub Actions environment **`signing`** in repository Settings →
   Environments. Restrict its deployment branches/tags to the branch **`main` only**
   (no tags). Add required review by the owner where supported. Protect `main` and
   review changes to workflows and build files before merging/signing.
2. Add these **environment secrets**, not public variables or repository files:

   | Secret | Value |
   | --- | --- |
   | `TOUCH_ZOOM_KEYSTORE_BASE64` | Base64 encoding of the existing original binary keystore |
   | `TOUCH_ZOOM_STORE_PASSWORD` | Original keystore password |
   | `TOUCH_ZOOM_KEY_ALIAS` | Original key alias |
   | `TOUCH_ZOOM_KEY_PASSWORD` | Original key password |

   Do not substitute a new key. Base64 is encoding, **not encryption**. The secret
   must be stored via GitHub's encrypted secret facility. Do not paste values into
   issues, chat, commits, logs, workflow YAML, or artifact uploads.
3. From a securely authenticated local GitHub CLI, one option for the binary is:

   ```sh
   base64 < /private/path/original.jks | gh secret set TOUCH_ZOOM_KEYSTORE_BASE64 \
     --repo KacperLa/android-touch-zoom-controller --env signing
   ```

   Set the remaining three with `gh secret set NAME --repo
   KacperLa/android-touch-zoom-controller --env signing` using its interactive
   hidden-value prompt. Do not place literal passwords in shell commands/history.
   Keep a separate private backup. This repository does not contain that backup.
4. Confirm the four secret **names** exist and environment restrictions are set.
   Then select Actions → Manual signed APK → Run workflow → `main`. Review the exact
   commit first. Missing secrets fail the signing step; no substitute signer is used.

The expected original signing-certificate SHA-256 fingerprint is public information:

```text
446d153476667dee48a1cfa1588cce34ceeb600c1c143b67c0f63506a18e1b0b
```

The workflow verifies the output against it before upload. It does not print the
certificate or private material. A successful build is not proof of device behavior.
The APK uses the existing application ID and versionCode **15**, with a release
(non-debuggable) build type. Same-key updates require a compatible installed version;
future releases should increment versionCode. Do not assume a newer installed
version can be downgraded, or that a different local debug key can update this app.

## Validation status

Workflow YAML, embedded shell syntax, action pins, trigger/secret separation, and
artifact path restrictions were checked locally. The actual Android workflow and
signing path still require a first successful GitHub run after environment setup.
No private key was accessed to prepare these files.

References: [GitHub encrypted secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets),
[Android apksigner](https://developer.android.com/tools/apksigner).
