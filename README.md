<p align="center">
  <img src="docs/brand/logo.png" alt="Leftover" width="420">
</p>

<p align="center"><b>Know what you can spend. Track it quickly. Save for what matters.</b></p>

Leftover is a free, open-source Android money app for students, first-job earners and freelancers in India. Its one big idea is a single number you can trust, **Left to spend**: the money you can use before your next payday, after bills, card dues and goal savings are set aside.

- **Offline-first and private.** Everything lives in a database on your phone. No account, no bank login, no analytics, no ads.
- **Built around payday, not the calendar.** Budgets, goals and comparisons follow your pay cycle.
- **Trustworthy accounting.** Transfers between your accounts and credit-card repayments are never counted as spending; refunds reduce spending; money friends owe you isn't counted until it arrives.
- **Every number can show its maths.** Tap **How?** on Home to see exactly where Left to spend comes from.

> **Status: early (v0.5).** The core app works end to end. See the [roadmap](ROADMAP.md) for what's next.

## What's in v0.5

| Area | Features |
|---|---|
| Start | Animated opening, onboarding that records a first expense, payday and balance set-up |
| Home | Left to spend with daily pace and breakdown, hide balances, bills coming up, insight, recent activity |
| Capture | Quick add (amount first, custom keypad, any past date, new categories on the spot), expense / income / transfer, undo; **voice entry** ("spent 180 on coffee today") with a review step and a typed fallback |
| Widgets | **Left to spend** (small / medium / large, with Expense and Income buttons) and **Quick log** (one-tap repeat spends with Undo); both follow Hide balances and dark mode |
| Activity | Day-grouped list, search and filters, transaction detail (change type, category, note), accounts |
| Plan | Budgets with pace marker and rollover, editable limits and categories, goals with a "what if I save…" slider, bills and subscriptions, wishlist cooldown, "Can I afford this?" |
| Auto-tracking | Optional: reads bank texts from named senders (never from people), adds clean spends, asks about the rest in a review queue, notifies with one-tap categories; off by default and deletable in one tap |
| Split | Friends and groups, split a bill equally / by amount / by shares, settle up through your UPI app, trip and event budgets, fewest payments to settle |
| Insights | Trend with your usual level, category breakdown with the reason behind each change, fees and charges, last-cycle recap |
| Money you owe | EMI tracker with prepay what-ifs, credit-card statement cycles with interest-free days, fee-waiver progress, credit-limit use and reward-point expiry |
| Cash | A cash wallet with ATM withdrawals as transfers and a "count your cash" fix-up |
| Irregular income | A mode that plans on your leanest recent cycle and treats the rest as a buffer |
| Tags and events | Tag any spend, see what a trip or a wedding cost across categories, quick-add templates |
| Festival funds | Diwali, Eid, Raksha Bandhan and wedding-season goals with a monthly amount that gets you there |
| Weekly check-in | A look back at the week and self-set challenges. No streaks, nothing resets |
| Reminders | Bills due, payday, budget limits, weekly check-in. Off until you switch them on; "remind me on payday" and "remind me to cancel" |
| Automatic tracking | Bank texts, UPI AutoPay mandates, capture rules, optional payment-app notifications from apps you choose, entries from other apps by link |
| Import | CSV statement import with column mapping, duplicate flags and a preview |
| Privacy | Hide balances everywhere, optional app lock with your phone's own lock, hide in the app switcher |
| Backup | Passphrase-encrypted backup files, scheduled backups to a folder you pick, restore from the welcome screen |
| Voice | English and Hindi, including Hinglish ("chai pe bees rupaye") |
| Data | CSV export, delete everything, light / dark / system theme |

## Build it

You need **JDK 17 or newer** and the **Android SDK** (API 37). Android Studio includes both.

```bash
git clone https://github.com/harshaltiwari-build/leftover.git
cd leftover
./gradlew :app:assembleDebug     # APK in app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest # run the unit tests
```

Open the folder in Android Studio to run it on a device or emulator. On first launch Android Studio creates `local.properties` with your SDK path; the repository does not include it.

Before publishing your own build, change `applicationId` in [`app/build.gradle.kts`](app/build.gradle.kts).

## How it's organised

```
app/src/main/java/app/leftover/
  domain/   Pure Kotlin: money, pay cycles, Left to spend, ledger, goals, insights.
            No Android imports, fully unit-tested.
  data/     Room database, DataStore settings, the repository that joins them.
  ui/       Jetpack Compose screens, one package per area, plus the design system in ui/theme.
```

The accounting rules live in `domain/Ledger.kt` and are covered by `LedgerTest`. If you change how money is counted, change a test first.

## Design

The design lives in a Figma-style canvas with 45+ screens (foundations, onboarding, every feature, states, dark mode, Android, widgets and SMS tracking). Colours, type and spacing are mirrored in `ui/theme/Theme.kt`. The brand assets are in [`docs/brand`](docs/brand).

| Token | Value |
|---|---|
| Ground / ink | `#F8F8F2` / `#20251B` |
| Accent | lime `#D6F478` |
| Type | Manrope, tabular figures; scale 12 · 13 · 15 · 18 · 22 · 28 · 36 · 48 · 64 |
| Corners | 8 · 12 · 16 · 22 · 28 · pill |
| Touch targets | at least 44 dp |

## Contributing

Contributions are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) first; the short version is: small focused pull requests, tests for any money logic, and keep the app offline and private.

## Privacy

Leftover does not declare the `INTERNET` permission, so it cannot send your data anywhere; it only leaves your phone if you export or back it up to a file yourself. The full list of permissions and why each exists is in [PRIVACY.md](PRIVACY.md).

## License

GPL-3.0. See [LICENSE](LICENSE). The Manrope font is under the SIL Open Font License ([fonts/OFL-Manrope.txt](fonts/OFL-Manrope.txt)).
