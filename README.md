# Voquab

A low-effort vocabulary builder for words met while reading. One weekly word
to use in a real conversation (with a streak), plus a few cards a day
delivered mostly through notifications and a widget. See [`PLAN.md`](PLAN.md)
for the full design and build phases.

Part of the personal Android app suite, distributed via
[Groom Hub](https://github.com/MatejGroombridge/personal-app-store-frontend).

## Build

Requires JDK 17+, Android SDK 35.

```bash
./gradlew :app:assembleDebug
```

For a signed release build, set up `keystore.properties` at the repo root:

```properties
storeFile=/path/to/release.jks
storePassword=...
keyAlias=main
keyPassword=...
```

then `./gradlew :app:assembleRelease`.

## Release

Cut a new version with the changeset helper:

```bash
./bin/changeset
```

It bumps `versionName` + `versionCode` in `app/build.gradle.kts`, prepends a
new entry to `CHANGELOG.md`, commits, tags `vX.Y.Z`, and pushes — which
triggers `.github/workflows/release.yml` to build, sign, attach the APK to a
GitHub Release, and patch the central manifest. Within ~3 minutes the Groom
Hub app on your phone offers the new version.

## AI Agent

[`agent.md`](agent.md) is the family-wide guide for AI coding agents (and
human developers): architecture, conventions, build config, signing, the
release workflow, and the design language. [`PLAN.md`](PLAN.md) is the
Voquab-specific plan.

## Repo layout

```
.
├── .github/workflows/release.yml   ← release pipeline
├── app/                            ← the Android app module
│   ├── build.gradle.kts
│   └── src/main/...
├── bin/changeset                   ← interactive release helper
├── CHANGELOG.md                    ← human-readable + machine-consumed release notes
├── PLAN.md                         ← product + build plan
├── build.gradle.kts                ← root build file
├── gradle/libs.versions.toml       ← dependency catalog
├── gradle.properties
└── settings.gradle.kts
```
