# Local release-bundle preparation

This is an optional packaging workflow, separate from the branch APK test workflow
in [CI.md](../CI.md). It does not establish store readiness or choose a signing scheme.

The current build configuration is in [app/build.gradle](../app/build.gradle):
minimum API 34, target API 36, compile SDK 37.0 and a non-debuggable release variant.
Minification is disabled to preserve the explicit API 37 helper boundary.

## Build locally

Use the toolchain in [README.md](../README.md). With the original keystore available
on a trusted computer, set `TOUCH_ZOOM_KEYSTORE`, `TOUCH_ZOOM_STORE_PASSWORD`,
`TOUCH_ZOOM_KEY_ALIAS` and `TOUCH_ZOOM_KEY_PASSWORD` privately, then run:

```sh
bash scripts/test.sh
bash scripts/build-play-bundle.sh
```

The script builds without passing signing variables to Gradle, signs the bundle
locally, verifies its entries and pins the certificate to the original identity.
Its default output directory is `build/play-preparation`, which is not committed.
AAB signing is different from APK signing; this command does not produce the signed
installable APK used by the branch testing workflow. Byte-identical signed output
is not promised because build/signature metadata can include timestamps.

The script does not upload, create credentials, configure GitHub secrets or enroll
app signing. `scripts/setup-signing.sh` changes GitHub configuration and is not
part of this local packaging flow.

## Validation and remaining decisions

Historical packaging checks validated an earlier prepared bundle and generated a
universal APK locally with matching package/version/SDK metadata and original
certificate. Bundle-entry verification rejected an injected unsigned payload.
Those checks are not a current-build result, Android runtime test or store approval.

Follow [VALIDATION.md](../VALIDATION.md) for current device gaps, including target-36
layout/navigation behavior, keyboard focus, folding, menu input and TalkBack.

Before any store submission, verify the account, accepted package, version history,
current requirements, privacy policy and accessibility declarations. Upload keys
and app-signing keys have different roles: a locally original-key-signed bundle
does not decide which certificate signs installed APKs. A different installed-APK
certificate can break updates from existing sideloaded installations. Any key
transfer, enrollment or submission requires a separate deliberate decision.
