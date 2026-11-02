# Contributing to Leftover

Thanks for helping. Leftover is a small, privacy-first money app, and a few rules keep it that way.

## Ground rules

1. **Private and offline.** No analytics, ads, crash reporters that phone home, or network calls. The app does not declare the `INTERNET` permission, so a pull request that adds it needs a very strong reason. Dependencies must be open source too: no Google Play services, no closed-source SDKs, so the app can ship on F-Droid.
2. **Money maths is tested.** Anything that changes how an amount is counted must come with a unit test in `app/src/test`. Amounts are `Long` paise, never `Double`.
3. **No decorative controls.** Don't add a switch, button or screen that doesn't do anything yet. Put it in the roadmap instead.
4. **Accessible by default.** Touch targets are at least 44 dp, text uses the type scale in `ui/theme/Theme.kt`, every icon-only control has a content description, and meaning is never carried by colour alone.
5. **Respect Hide balances.** Any new place that shows an amount uses `Amount(...)` or `Money.display(...)` so the setting works everywhere.

## Getting set up

```bash
./gradlew :app:testQaUnitTest          # unit, integration and journey tests on the JVM, no device needed
./gradlew :app:connectedQaAndroidTest  # the same journeys on a phone, in a separate .qa app
./gradlew :app:assembleDebug
```

You need JDK 17+ and the Android SDK. Open the project in Android Studio for previews and the emulator.

## Where things go

- `domain/`: pure Kotlin, no Android imports. Start here for logic.
- `data/`: Room entities and DAOs, DataStore settings, `LeftoverRepository`.
- `ui/<area>/`: Compose screens. Shared pieces live in `ui/components`; tokens in `ui/theme`.

Changing the database? Bump the Room `version`, add a `Migration`, and commit the updated schema JSON from `app/schemas`.

## Pull requests

- Keep them small and focused; describe what changed and why.
- Write the test first: unit tests in `app/src/test`, integration tests (Room, settings, repository together) in `app/src/test/.../integration`, and user journeys in `app/src/sharedTest/.../e2e`, which run both on the JVM and on a phone.
- Run `./gradlew :app:testQaUnitTest` and make sure everything passes.
- For UI changes, include a screenshot in light and dark.
- By contributing you agree your work is licensed under GPL-3.0.

## Reporting problems

Open an issue with your Android version, what you expected and what happened. Please **don't paste real financial data** into issues; describe the numbers or use made-up ones.

## Release signing

Releases are signed with a private key that is **not** in this repository. The build looks for `keystore.properties` in the project folder, then in `~/.leftover-signing/` (both are git-ignored):

```
storeFile=/path/to/leftover-release.keystore
storePassword=...
keyAlias=leftover
keyPassword=...
```

Without it, `./gradlew :app:assembleRelease` produces an unsigned APK and everything else (debug builds, tests) works as before. Keep a backup of the key: an app signed with a different key can't update an installed one.

The release key's SHA-256 certificate fingerprint, to check a download with `apksigner verify --print-certs`:

`C7:A8:75:78:36:F9:5E:21:21:78:9C:D6:D2:78:A1:F4:B2:EA:C1:AF:65:8E:8D:EE:7F:7D:68:B7:7B:51:46:91`
