# Privacy

Leftover is designed so that your financial data stays on your phone.

- **No account, no sign-in, no servers.** There is nothing to log in to and nowhere for the app to send data.
- **No internet permission.** The app does not declare `INTERNET`, so it cannot send data over the network. Backups are files you save and keep yourself.
- **No tracking.** No analytics, advertising IDs or third-party SDKs.
- **Local storage only.** Transactions, accounts, goals, bills and settings are stored in an on-device database. Automatic cloud backup is turned off (`android:allowBackup="false"`).
- **You control your data.** Export everything as CSV from Settings, or delete everything with one tap.

## Permissions the app holds

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | Voice entry, requested only when you tap the mic |
| `RECEIVE_SMS` | Automatic tracking. Declared in the app but requested at runtime only when you switch **Track from bank texts** on |
| `READ_SMS` | The one-time **Scan the last 30 days**. Requested only when you tap *Scan now* |
| `POST_NOTIFICATIONS` | The note shown when a bank text is added or needs you, and the reminders you switch on. Requested the first time you switch one of those on (Android 13+) |
| `USE_BIOMETRIC`, `USE_FINGERPRINT` | App lock, through Android's own prompt. Added by the AndroidX Biometric library. Leftover never sees your fingerprint or face, only whether Android said yes |
| `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `ACCESS_NETWORK_STATE` | Added automatically by AndroidX WorkManager, which the home-screen widget library uses to refresh widgets. Leftover does not use them for anything else, and `ACCESS_NETWORK_STATE` only lets Android's scheduler know whether the phone is online. |

**Notification access** is not a permission in this list. It is a special switch in Android settings, offered only if you turn on *Read UPI app notifications* (see below).

You can check this yourself: build the app and run `aapt2 dump permissions app-debug.apk`.

## Voice entry (v0.2)

Voice entry is optional and only asks for the **microphone** permission when you tap the mic. You can always type the sentence instead, and that works without any permission.

Leftover never records, stores or sends audio itself. The words are turned into text by **your phone's speech recognition service**, which is outside Leftover's control:

- On Android 13+ with an on-device recognizer, Leftover uses it, and audio stays on the phone.
- Otherwise Leftover asks the system recognizer to prefer offline mode, but whether audio is processed online depends on your phone's speech service (usually Google's) and its settings. If that matters to you, use the typed option or install an offline speech pack.

Only the recognised *text* reaches Leftover, and it is parsed on the device. Nothing is saved until you confirm the review screen.

## Home-screen widgets (v0.2)

Widgets read the same on-device data. Amounts follow **Hide balances**: when it's on, widgets show `₹ ••••`. The Quick log widget writes entries only when you tap a button.

## Split and friends (v0.3)

Friends' names, their optional UPI ids, groups, split bills and settle-up records are stored in the same on-device database as everything else. They are never uploaded, and Leftover never reads your contacts: you type each name yourself.

**Settle up** opens your UPI app through Android's standard `upi://pay` link, with the friend's UPI id, the amount and a short note. From that point your UPI app (and your bank) handle the payment, outside Leftover's control. Leftover cannot see whether it succeeded, so it asks you before recording anything. This needs no extra permission.

## Automatic tracking from bank texts (v0.4)

Off by default. When you switch it on, Leftover reads incoming texts so it can add bank spends for you. What it does and doesn't do:

- **Only named senders.** It looks at a text's body only when it comes from a bank-style sender name such as `AX-HDFCBK`. Texts from phone numbers, which is where your chats come from, are dropped before the body is read. The same rule applies to the optional inbox scan, which reads sender and date first and opens only the bank-style texts.
- **OTPs are never used.** Any text mentioning an OTP, a verification code or similar is discarded.
- **The text is not kept.** Only the amount, merchant, date, direction, the last four digits of the account and a short fingerprint (to avoid adding the same text twice) are stored, in the on-device database. Nothing leaves the phone; the app has no `INTERNET` permission. A backup file does not include bank-text senders or waiting items, but transactions that were saved from texts are ordinary transactions and are in it like any other.
- **You stay in control.** A spend is saved without asking only when its sender is allowed, its account and merchant are already known, and it reads cleanly. Anything else, and all money coming in, waits in a review queue. Each sender can be switched off, and **Turn off and delete SMS data** removes every transaction that came from texts, the queue and all sender settings. Entries you typed yourself are never touched.
- **Notifications** show amounts on the shade but only a generic line on the lock screen, and show no amounts at all when Hide balances is on.
- Android lets you remove the SMS permission at any time in the phone's settings.
- Google Play restricts the SMS permission, so this feature is meant for builds you install yourself.

Bank wording differs, so the reader is cautious: when it isn't sure, it asks you instead of guessing.

## Payment-app notifications

Off by default and separate from bank texts. If you switch on **Read UPI app notifications**, Android sends you to its *Notification access* screen, where you allow Leftover. Leftover then:

- **Looks only at the apps you tick** in a short list (Google Pay, PhonePe, Paytm, BHIM, CRED, Navi, super.money). A notification from any other app is dropped by its package name before its text is touched.
- **Reads each notification once**, keeps only the amount, who it was, and the time, and drops the text. Nothing leaves the phone.
- **Treats each app like a bank sender.** Until you trust an app, everything it sends waits in the review queue. If your bank also texts the same payment, you get one entry, not two.
- You can turn it off, or withdraw Notification access in Android settings, at any time.

## Reminders

Bills due, payday, budget limits and a Sunday check-in are each off until you switch them on, and they are worked out on the phone by Android's WorkManager. On the lock screen a reminder only says that something is waiting. Amounts are left out of reminders when Hide balances is on.

## App lock and the app switcher

**App lock** uses your phone's own fingerprint, face or screen lock, through Android's biometric prompt. Leftover keeps no PIN of its own. **Hide in the app switcher** blanks the preview in recent apps (on Android 12 and older it does this by blocking screenshots).

## Entries from other apps

If you switch on **Accept entries from other apps**, a `leftover://capture` link can hand Leftover an amount and merchant. Such an entry only ever lands in the review queue; nothing is added without you.

## Backup and restore

**Back up to a file** (Settings → Backup and restore) saves everything you entered (accounts, transactions, goals, bills, EMIs, wishlist, friends, groups, tags, rules and settings) as one file in a place you choose, using Android's own file picker. Leftover doesn't upload it anywhere.

- **Passphrase protection.** If you set a passphrase, the file is encrypted with AES-256-GCM, with a key derived from your passphrase (PBKDF2-HMAC-SHA256, 310,000 rounds). Leftover cannot recover a lost passphrase, because it never leaves your head: the app stores only a key wrapped by the Android Keystore so scheduled backups can run. Without a passphrase the file is plain text that anyone can read, and the screen says so before you save.
- **Automatic backups.** You can pick a folder and a schedule. Leftover writes dated files there and keeps the newest five.
- **Restore** replaces what is on the phone. If the file can't be read or the passphrase is wrong, nothing changes.
- Not included: bank-text senders and waiting items, and phone-specific settings (tracking switches, notification access, app lock, reminders).

## Importing a bank statement

**Import a bank statement** reads a CSV file you choose, on the phone, shows a preview, and saves nothing until you tap Import.

## Later features

Scanning receipts is planned. It will be opt-in and processed on the device, and will be documented here before it ships.
