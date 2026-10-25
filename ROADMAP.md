# Roadmap

The full design has 45+ screens. v0.1 shipped the core, v0.2 added widgets and voice, and v0.3 added Split, v0.4 added automatic tracking from bank texts, and v0.5 adds the money-you-owe tools, reminders, app lock and encrypted backups; the rest is staged so each release is usable and honest: **nothing appears in the app until it works.**

## v0.1: core (shipped)

Opening animation, onboarding, Home, Quick add, Activity, Accounts, Budgets, Goals, Bills, Wishlist, "Can I afford this?", Insights, Recap, Settings, CSV export.

## v0.2: capture without opening the app

- [x] **Home-screen widgets** (Jetpack Glance): *Left to spend* in small, medium and large layouts with "− Expense" / "+ Income" buttons, and a *Quick log* widget with one-tap buttons built from your own repeat spends plus a real Undo. Both respect *Hide balances* and follow light/dark.
- [x] **Voice entry**: Android `SpeechRecognizer` (on-device when available) plus a typed fallback; understands amounts like "1,250", "2k", "1.5 lakh" and "twelve hundred", dates like "yesterday", and always shows an editable review before saving.
- [ ] Lock-screen widgets (only Android 14+ tablets support them) and a widget configuration screen.
- [x] **Hindi speech and Hinglish.** Pick English or Hindi in Settings → Appearance and language; the parser knows Hindi number words and Devanagari digits.
- [ ] **Receipt and UPI-screenshot capture**: CameraX plus on-device ML Kit text recognition, confidence shown, items splittable across categories.
- [x] **Statement import (CSV)**: column mapping, date format, preview, duplicate flags, nothing saved until you tap Import.
- [ ] PDF statements, automatic transfer detection on import, and per-row duplicate decisions.

## v0.3: Split and shared money

- [x] **People, groups and shared expenses**, with a *Friends owe you / You owe* summary on the new Split tab (it replaces Insights in the bottom bar; Insights is now opened from Home and Split).
- [x] **Split a bill** equally, by exact amounts or by shares. Nothing saves until the parts add up to the total (largest-remainder rounding, so no paise are lost). Only your share counts toward your budgets, whether you or a friend paid.
- [x] **Settle up** by opening your UPI app with the payee, amount and note filled in, then asking "Did the payment go through?". The app never assumes a payment succeeded. Cash and "paid another way" are recorded directly, and a friend paying you is recorded by hand (Android has no way to request money).
- [x] **Groups** for flatmates, trips and events: group total and your share, an optional budget for trips and events, spending by category, who owes whom, and the fewest payments to settle.
- [x] Money friends owe you shows as *Coming back* and is **not** counted in Left to spend until they pay.
- [ ] Trip and event dates, and archiving or deleting a group.
- [ ] Editing a split bill (today: delete it and add it again).
- [x] **Repeating shared bills** such as rent: turn on "Repeat every month" when splitting equally and Leftover adds the split again on its day.
- [ ] Settling a group payment that doesn't involve you. Only your own payments are tracked.
- [ ] Remove the unused `shared` column from bills (needs a database migration).

## v0.4: automatic tracking (Android only)

- [x] Opt-in **bank SMS** reading, parsed on the device. Only named senders (`AX-HDFCBK`) are read, never phone numbers; any text mentioning an OTP is discarded; the text is never stored, only amount, merchant, date, direction and the last four digits.
- [x] **Notification** for each tracked item, with one-tap category buttons for new merchants and an Undo for items saved on their own. The lock screen shows no amounts.
- [x] **Review queue** (also reachable from a Home banner) for new merchants, new accounts, money coming in, possible transfers and card payments, possible duplicates of something you typed, and texts the reader isn't sure about.
- [x] **Per-sender control**, an optional one-time **scan of the last 30 days**, and **Turn off and delete SMS data**.
- [x] **UPI-app notifications** as a second source, from an allow-list of apps you tick, with a separate Notification-access switch and de-duplication against bank texts.
- [ ] More bank formats. The reader is tested on common shapes only, so unfamiliar wording goes to review; reports of texts it misreads (with the numbers and names removed) are welcome.
- [ ] Linking an account to its digits from the account screen, not only from the review queue.
- [ ] Re-run the scan automatically after a restore or reinstall.
- ⚠️ Google Play restricts the SMS permission and requires a permission declaration. Sideloaded / F-Droid builds are not affected.

## v0.5: money you owe, reminders and safety

- [x] **EMIs**: schedule, outstanding balance, prepay what-if with the interest it saves, and an EMI line in Left to spend.
- [x] **Credit-card statement cycle**: statement and due days, billed vs unbilled, interest-free days for a purchase today, and only the billed part set aside in Left to spend. Record a bill payment as a transfer.
- [x] **Card extras**: credit-limit use, annual-fee waiver progress, reward points and their expiry, and a fees-and-charges card in Insights. A fee detector labels bank-charge texts.
- [x] **Cash wallet** with ATM withdrawals and a "count your cash" fix-up.
- [x] **Irregular-income mode**, **tags and events**, **quick-add templates**, **festival funds**.
- [x] **Weekly check-in and challenges**, with no streaks.
- [x] **Reminders**: bills due, payday, budget limits and the weekly check-in, each switched on by you, plus one-off "remind me on payday" and "remind me to cancel".
- [x] **App lock** with the phone's own biometric or screen lock, and **hide in the app switcher**.
- [x] **Backup**: passphrase-encrypted files, scheduled backups to a folder, restore from the welcome screen.
- [x] **Settings** regrouped into Your money, Add entries automatically, Reminders, Keep your data safe and Look and language, with sub-screens for Money rhythm, Appearance and language, Privacy and permissions, About and Delete.
- [x] Large-text pass on shared components (buttons, list rows, chips and segmented controls wrap instead of clipping).

## Not built, on purpose or not yet

- [ ] **Receipt and UPI-screenshot capture.** Text recognition needs a library. Google's ML Kit is closed-source, which F-Droid and this project's rules rule out; Tesseract is open source but heavy. A photo-attachment-only version (no reading) is possible.
- [ ] **Splitting one transaction across several categories**, and **swipe actions** on Activity rows.
- [ ] **Cycle switch** (payday / weekly / monthly) on Budgets.
- [ ] **Trip dates, a "my share / whole group" toggle and a linked goal** on group screens.
- [ ] **"Ask about your money."** The design shows an assistant that answers questions. It can't use an online model without an internet permission; an on-device, rule-based version that always shows the transactions behind each answer is a possible shape and needs a decision.
- [ ] A widget quick-entry sheet over the launcher, a bill-reminder widget and a today-spent line on the small widget.
- [ ] Remind or message a friend. Leftover has no way to contact anyone.

## Also planned

- [x] Backup to a file and restore from it. Restoring replaces everything; there is no merge.
- [x] Encrypt the backup file with a passphrase.
- [ ] Android 360 dp layout pass on every screen, and a full 200% font-size pass screen by screen.
- [ ] Hindi and other Indian-language UI.
- [ ] Instrumented (device) tests for the Compose screens.

Ideas and votes welcome in the issue tracker.
