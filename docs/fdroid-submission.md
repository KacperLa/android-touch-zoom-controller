# F-Droid submission preparation

## Current scope

This repository is not yet listed on F-Droid. The candidate recipe is
`fdroid/metadata/io.github.kacperla.touchzoom.yml`. Copy it into `metadata/` of an
F-Droid Data fork for submission; the included `fdroid/srclibs/` entry mirrors
F-Droid's existing reproducible-apk-tools entry. The small categories configuration
is only for standalone validation and does not need to be submitted.

The pinned 0.33 tag predates the fastlane assets in this branch. For a 0.33
submission, copy `fastlane/metadata/android/en-US/` to
`metadata/io.github.kacperla.touchzoom/en-US/` in fdroiddata, omitting the 34.txt
changelog until that version is submitted. Merging these files into main alone
will not make them appear in the old tagged checkout.

The first candidate deliberately uses the already public `v0.33-alpha.1` tag
(commit `63919b63837bf1db4f70b8136ac931cc5d8d0715`), version code 33. Main contains
0.34 with per-tap app launching, but that revision has no public tagged release yet.
Do not point a recipe at an expiring Actions artifact or move an existing tag.
The alpha status and limited device testing are disclosed in the description.

## Licensing and permissions

The application is GPL-2.0-or-later, as stated in COPYING. It declares no third-party
runtime dependencies; Android build tooling adds implicit libraries, which must
still pass F-Droid's scanner. It requires Android 14/API 34+, targets API 36, and
uses JDK 17, Gradle 9.3.1, AGP 9.1.1, Android SDK 37.0 and build-tools 36.0.0.

The production accessibility service controls magnification but cannot retrieve
window content. The app has no internet permission. Optional local diagnostics
can contain numeric touch positions, timing, device model and Android version;
the user explicitly chooses whether to copy/share them. No private diagnostics,
device screenshots or signing credentials belong in a submission.

## Preserve signing identity

The candidate specifies the public upstream APK and this certificate SHA-256:

```
446d153476667dee48a1cfa1588cce34ceeb600c1c143b67c0f63506a18e1b0b
```

F-Droid rebuilds the source and verifies the original signature. Do not remove the
binary or AllowedAPKSigningKeys fields merely to get a green build: that would
change the distribution signing strategy and affect in-place updates.

Existing APKs were signed with tooling that changes ZIP padding. The recipe uses
F-Droid's reproducible-apk-tools `zipalign.py --page-size 16 --pad-like-apksigner
--replace` before the signature-copy check. This changes packaging, not app code.
Future signing uses `--alignment-preserved true`; after a compatible new release,
retest and remove the old padding workaround from its new build entry if needed.
Never upload a private keystore to F-Droid. No new signing service is required.

## Validation and remaining steps

1. Run the **F-Droid reproducibility** workflow and inspect its exact-commit result.
   It runs metadata lint, a source rebuild, source/APK scans and signature copying
   with JDK 17 and JDK 21 (the current F-Droid buildserver default).
   [The first verification run](https://github.com/KacperLa/android-touch-zoom-controller/actions/runs/42389327416)
   passed metadata lint, source/APK scanning, a fresh 0.33 rebuild and the original
   signature/certificate checks. A GitHub runner pass is useful evidence, not proof
   that the F-Droid buildserver
   or maintainers will accept the app.
2. Merge the upstream store metadata when reviewed and include the localized
   metadata in fdroiddata as described above when submitting the older 0.33 tag.
   Descriptions, icon and version
   changelogs are in `fastlane/metadata/android/en-US/`. The icon is a rasterization
   of the existing launcher vector. No fake screenshots are included; capture
   clean, non-personal screenshots from a real device/emulator if requested.
3. Decide whether to submit the existing 0.33 alpha or first publish a new tagged
   release containing the metadata and 0.34 features. A new release must have a
   verified original-key APK and permanent download URL before changing the recipe.
4. With the owner's GitLab account, check for an existing request, fork
   `fdroid/fdroiddata`, add the candidate metadata and run its GitLab pipeline.
   Address any buildserver differences before opening a merge request. A Request
   for Packaging is an alternative when packager assistance is needed.
5. Keep manual update control for now. `AutoUpdateMode: None` prevents automatic
   publication of unreviewed alpha tags; `UpdateCheckMode: Tags` discovers them.

Do not claim the app is available on F-Droid until its package page and installable
build exist. Submission and acceptance are separate steps.

## Sources

- [F-Droid submission guide](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/)
- [Inclusion policy](https://f-droid.org/docs/Inclusion_Policy/)
- [Reproducible builds](https://f-droid.org/docs/Reproducible_Builds/)
- [APK padding workaround](https://github.com/obfusk/apksigcopier/issues/105)
