# Contributing

Keep changes focused and describe their user-visible effect. Preserve existing
settings and signing continuity; never commit keys, passwords, private backups,
local SDK configuration or device captures.

Before proposing a change:

```sh
bash scripts/test.sh
gradle :app:assembleRelease :app:lintRelease :app:assembleDebugAndroidTest
```

Use the toolchain in [README.md](README.md). Add regression tests for changed
policies, and distinguish helper/source checks from Android runtime tests. Gesture,
focus, overlay or keyboard changes need the relevant [device checklist](VALIDATION.md)
and, where possible, [dedicated-emulator scenarios](RUNTIME_TESTS.md).

Keep accessibility capabilities minimal. Avoid polling, hidden APIs and new runtime
dependencies unless the change justifies them. Do not infer compositor behavior or
physical-device responsiveness from source inspection or software-emulator timing.

Update current documentation rather than appending release logs to the README.
Put version history in [CHANGELOG.md](CHANGELOG.md). Report test gaps and exact
reproduction steps without including personal device data.

Branch CI produces an unsigned APK for review and local original-key signing.
Publishing a branch does not authorize merging it or changing signing setup; see
[CI.md](CI.md). Contributions retain the project's GPL-2.0-or-later license.
