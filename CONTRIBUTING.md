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
./gradlew :app:testDebugUnitTest   # fast, no device needed
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
- Run `./gradlew :app:testDebugUnitTest` and make sure it passes.
- For UI changes, include a screenshot in light and dark.
- By contributing you agree your work is licensed under GPL-3.0.

## Reporting problems

Open an issue with your Android version, what you expected and what happened. Please **don't paste real financial data** into issues; describe the numbers or use made-up ones.
