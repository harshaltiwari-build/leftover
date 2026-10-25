# Settings: design plan

A review of the Settings area by a design agent, followed by the plan that the rebuilt Settings follows. Items marked *(inferred)* were not confirmed in the code.

## 1. Audit of the page before this plan

**Controls that did nothing or were hidden**
- **Bill reminders** is stored, written into backups and has a setter, but nothing reads it and no job sends a reminder. It defaults to on, which wrongly suggests reminders work. No row is shown until a scheduler exists.
- **App lock** is stored but has no screen or logic. Planned (see the roadmap).
- **Income type and usual income** could not be edited after onboarding, yet the usual income feeds the budget screen.
- **Switch rows:** only the 52×44 dp switch was tappable, not the label next to it.

**Accessibility**
- The name field was about 23 dp tall (under the 44 dp rule), had no label and used a placeholder with about 2:1 contrast.
- Switch rows announced only "Hide balances, switch", without the helper text.
- Section labels and the page title were not marked as headings.
- Chips and the segmented control used the *tab* role instead of a radio-button group.
- The segmented control was fixed at 44 dp with one line, so it clips at large font sizes.

**Hierarchy and grouping**
- The profile card repeated the payday chips, and "₹ INR" looked like a setting but was not.
- "Your data" mixed setup (Accounts, Categories), capture (Automatic tracking) and data safety (Back up, Restore, CSV). Restore, which replaces everything, looked the same as Back up.
- Confirmations and results appeared below the list, so tapping Restore at the top put its confirmation off-screen.
- The delete confirmation made "Delete" the filled primary button and "Keep my data" the outline.
- Only eight payday dates were offered, with no 2–4, 6, 8, 9 or 31.
- The backup rows did not say the file is plain and unencrypted until after saving.
- The name field wrote on every keystroke. The exported CSV stayed in the cache folder.
- The version footer was not tappable: no licence, source or privacy link.

## 2. Information architecture

Ordered by how often each thing is used and how much it matters if ignored. Backup ranks above theme because it is the only protection in an offline app.

1. **You** and **Hide balances**
2. **Your money:** Accounts and cards · Categories and tags · Money rhythm · Bills and plans
3. **Add entries automatically:** Automatic tracking
4. **Reminders:** Weekly check-in (a switch appears only when it works)
5. **Keep your data safe:** Backup and restore · Privacy and permissions
6. **Look and language:** Appearance and language
7. **About**, then **Delete all data** last

Per-item settings (card statement day, cash wallet, festival funds, tags) live on their own screens, not in Settings.

## 3. Main page rows

| Row | Helper text | Control |
|---|---|---|
| Profile | "Shown on Home" | Row, opens a "Your name" field |
| Hide balances | "Show ₹ •••• instead of amounts, everywhere in the app." | Whole-row switch |
| Accounts and cards | "Banks, cash wallet, credit cards" | Link, value "4 accounts" |
| Categories and tags | "Names, colours, visibility" | Link |
| Money rhythm | "Payday and income" | Link, value "Payday 28th" |
| Automatic tracking | "Add spends from bank texts" | Link, value On/Off, warning if a permission was withdrawn |
| Backup and restore | status line | Link, warning if never backed up or older than 30 days |
| Privacy and permissions | "What Leftover can and can't access" | Link |
| Appearance and language | "Theme and voice language" | Link, value "System" |
| About Leftover | "Version, licence, source code" | Link |
| Delete all data | "Removes everything from this phone" | Destructive text row, 48 dp |

Rows share one `SettingsRow(title, helper, value, onClick)` component. Warning rows use `emberBg` and `emberText`. A disabled row always says why.

## 4. Sub-screens

- **Backup and restore:** a status card (last backup, size, encrypted or not); *Back up now*; *Protect with a passphrase* (on by default, with a hard "no recovery" warning); optional automatic backup to a folder you pick; restore from a file (asks for the passphrase when needed and offers a safety backup first); export and import CSV; a note that bank-text senders and the tracking switch are not included.
- **Automatic tracking:** master switch and status; review queue and 30-day scan; banks and wallets (trust per sender); capture rules; UPI app notifications with a per-app allow-list; entries from other apps by link (review queue only); delete SMS data.
- **Money rhythm:** payday 1–31 plus last day of month; income type; usual income (respects Hide balances); irregular-income mode.
- **Appearance and language:** theme; voice language.
- **Privacy and permissions:** a plain list with live granted / not granted status (microphone, text messages, notifications, notification access, Internet: none) and a button to open Android's permission screen.
- **About:** version, GPL-3.0, source link as copyable text, font credit, what's new.

## 5. Wireframe

```
Settings
+--------------------------------------+
| (A) Asha                           > |
| Hide balances                  [ O ] |
+--------------------------------------+
 YOUR MONEY
| Accounts and cards      4 accounts > |
| Categories and tags                > |
| Money rhythm         Payday 28th   > |
 ADD ENTRIES AUTOMATICALLY
| Automatic tracking             On  > |
 KEEP YOUR DATA SAFE
| Backup and restore   ! Never backed up >|
| Privacy and permissions            > |
 LOOK AND LANGUAGE
| Appearance and language     System > |
| About Leftover            v0.4     > |
  Delete all data
```

## 6. Interaction and states

- **Restore** opens its own confirmation, not a card at the bottom. **Delete all data** is a full page that offers a backup first; the safe button is the primary one.
- **Permissions** are asked only when something is switched on. A denied permission leaves the switch off with a banner that says "Open Android settings". Status is re-checked when the screen resumes.
- **Errors name the cause:** "Wrong passphrase", "This isn't a Leftover backup", "Folder is no longer available. Pick it again".
- **Large text:** rows have a minimum height only and wrap values under the label. Segmented controls wrap instead of clipping.
- **TalkBack:** each row reads as one item (title, helper, value, state). Section labels are headings. Options use radio-button roles.
- **Hide balances:** every amount in Settings goes through the shared `Amount` component.

## 7. Copy

- Hide balances: "Show ₹ •••• instead of amounts, everywhere in the app."
- Never backed up: "You haven't backed up yet. If this phone is lost, so is your data."
- Passphrase: "Pick a passphrase you'll remember. Leftover has no account, so we can't reset it for you."
- Restore: "Replaces everything on this phone with what's in the file."
- Automatic tracking: "Add spends from bank texts. Leftover only reads texts from bank names, never from people."
- Permissions: "You can take any permission back in Android settings. Leftover has no internet access at all."
- Delete: "This removes every account, entry, goal and bill from this phone. It can't be undone."

## 8. Build order

**P0** (small): whole-row switches with merged accessibility text; a labelled, 44 dp name field that saves once; feedback and confirmations at the top of the page; "Keep my data" as the primary delete button; say "plain file, not encrypted" before saving; delete the cached CSV after sharing; no bill-reminder row until it works.

**P1:** regroup the page; shared `SettingsRow`; radio-button roles and heading semantics; Money rhythm screen with the full payday range and editable income; Backup and restore screen with status and passphrase encryption; Privacy and permissions screen; About screen; stronger switch contrast.

**P2:** scheduled backup to a folder; rules editor and notification allow-list; weekly check-in reminder; voice language selector; CSV import; irregular-income mode; a 200% font pass.
