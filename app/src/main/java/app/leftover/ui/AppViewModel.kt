package app.leftover.ui

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import app.leftover.BuildConfig
import app.leftover.data.UpdateInstaller
import app.leftover.data.UpdateManager
import app.leftover.data.UpdateState

import kotlinx.coroutines.sync.withLock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.leftover.LeftoverApp
import android.net.Uri
import app.leftover.backup.AutoBackup
import app.leftover.data.BackupCodec
import app.leftover.data.BackupCrypto
import app.leftover.data.WrongPassphraseException
import app.leftover.data.BackupException
import app.leftover.data.NewSharedExpense
import app.leftover.data.OnboardingAnswers
import app.leftover.data.Snapshot
import app.leftover.data.ReceiptStore
import app.leftover.data.ProfilePhotoStore
import app.leftover.domain.Pin
import app.leftover.domain.Money
import app.leftover.domain.ScreenshotAutoSave
import app.leftover.domain.ScreenshotProposal
import app.leftover.ui.add.EntryDraft
import app.leftover.ui.add.ScreenshotIntake
import app.leftover.data.ThemeMode
import app.leftover.data.db.AccountEntity
import app.leftover.data.db.AccountType
import app.leftover.data.db.BillEntity
import app.leftover.data.db.EmiEntity
import app.leftover.data.db.CategoryEntity
import app.leftover.data.db.CardPerkEntity
import app.leftover.data.db.ChallengeEntity
import app.leftover.data.db.RecurringSplitEntity
import app.leftover.data.db.GoalEntity
import app.leftover.data.db.GroupEntity
import app.leftover.data.db.PersonEntity
import app.leftover.data.db.MandateEntity
import app.leftover.data.db.MandateStatus
import app.leftover.data.db.PendingSmsEntity
import app.leftover.data.db.TagEntity
import app.leftover.data.db.TemplateEntity
import app.leftover.data.db.SettleDirection
import app.leftover.data.db.SettleMethod
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.WishEntity
import app.leftover.data.db.TxType
import app.leftover.data.db.WishStatus
import app.leftover.domain.PendingReason
import app.leftover.domain.SmsKind
import app.leftover.domain.LinkEntry
import app.leftover.domain.ME
import app.leftover.domain.toLocalDate
import app.leftover.sms.ScanResult
import app.leftover.sms.SmsInboxScanner
import app.leftover.ui.split.PersonColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** What happened when a backup file was read. */
sealed interface RestoreOutcome {
    data object Done : RestoreOutcome
    data class NeedsPassphrase(val text: String) : RestoreOutcome
    data class Failed(val message: String) : RestoreOutcome
}

/** A short "Saved · Undo" message shown above the bottom bar. */
/**
 * A short confirmation with Undo. Undo deletes [undoTransactionId] (something just added) or puts [restore] back
 * (something just deleted), along with its tags.
 */
data class Toast(
    val title: String, val subtitle: String, val undoTransactionId: Long? = null, val restore: TransactionEntity? = null, val restoreTags: Set<Long> = emptySet(),
    /** An edited entry as it was before, put back by Undo. */
    val revert: TransactionEntity? = null,
)

/** The single view model for the app: it exposes one [Snapshot] and funnels every write through the repository. */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LeftoverApp
    private val repository = app.repository
    private val settings = app.settings

    /** Null until the first emission, so the launch animation can wait for real data. */
    val snapshot: StateFlow<Snapshot?> = repository.snapshot.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _pendingRoute = MutableStateFlow<String?>(null)

    /** A screen requested from outside the app (a widget tap), opened once the app is ready for it. */
    val pendingRoute: StateFlow<String?> = _pendingRoute.asStateFlow()

    fun requestRoute(route: String?) { _pendingRoute.value = route }

    private val _toast = MutableStateFlow<Toast?>(null)
    val toast: StateFlow<Toast?> = _toast.asStateFlow()
    private var toastJob: Job? = null

    init {
        viewModelScope.launch { repository.ensureCategories() }
    }

    fun showToast(toast: Toast) {
        toastJob?.cancel()
        _toast.value = toast
        toastJob = viewModelScope.launch {
            delay(5_000)
            _toast.value = null
        }
    }

    fun dismissToast() {
        toastJob?.cancel()
        _toast.value = null
    }

    /** Removes the transaction the toast was about and hides the toast. */
    fun undoToast() {
        val current = _toast.value
        dismissToast()
        val id = current?.undoTransactionId
        if (id != null) run { repository.deleteTransaction(id) }
        current?.revert?.let { before -> run { repository.updateTransaction(before) } }
        val back = current?.restore
        if (back != null) run {
            repository.addTransaction(back)
            if (current.restoreTags.isNotEmpty()) repository.setTransactionTags(back.id, current.restoreTags)
        }
    }

    /** Deletes a plain entry from a list swipe, with Undo. Entries tied to a split or settlement go through their detail screen instead. */
    fun deleteWithUndo(tx: TransactionEntity, tags: Set<Long>) = run {
        repository.deleteTransaction(tx.id)
        showToast(Toast("Deleted", (tx.merchant.ifBlank { tx.note }.ifBlank { "Entry" }) + " · balances updated", restore = tx, restoreTags = tags))
    }

    private var draft: EntryDraft? = null

    /** Hands the waiting screenshot draft to Quick add, once. */
    fun takeDraft(): EntryDraft? = draft.also { draft = null }

    /**
     * A payment screenshot (or a payment app's text) shared from another app. It's read on the phone. A clear,
     * successful payment saves itself with Undo (see [ScreenshotAutoSave]); anything else opens
     * Quick add with the amount, payee, date and reference filled in for the person to check.
     */
    fun captureShared(image: Uri?, text: String?) = viewModelScope.launch {
        if (image != null) showToast(Toast("Reading your screenshot…", "On this phone · usually a second or two"))
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { ScreenshotIntake.read(app, image, text) }
        dismissToast()
        handleScreenshot(result)
    }

    /** One screenshot decision at a time, so the same picture shared twice can't be saved twice. */
    private val screenshotLock = kotlinx.coroutines.sync.Mutex()

    /** Saves what was read from a screenshot when it's clear enough, or opens Quick add to check it. */
    fun handleScreenshot(result: EntryDraft) = viewModelScope.launch { screenshotLock.withLock { decideScreenshot(result) } }

    private suspend fun decideScreenshot(result: EntryDraft) {
        val s = repository.currentSnapshot()
        // Read the lock from the settings themselves: at a cold start [locked] may not have caught up yet.
        val lockedNow = s.settings.appLock && !unlocked.value
        val shot = ScreenshotAutoSave.Shot(
            result.read, result.income, result.status, result.amount, result.merchant, result.date, result.time, result.note, result.receipt,
        )
        when (val decision = ScreenshotAutoSave.decide(shot, s.ledger, s.settings.autoSaveScreenshots, lockedNow)) {
            is ScreenshotAutoSave.Decision.Save -> {
                val id = repository.addTransaction(decision.tx)
                requestRoute(Routes.HOME)
                showToast(Toast("Saved ${Money.format(decision.tx.amount)} · ${decision.tx.merchant}", "From your screenshot · Undo if it's wrong", id))
            }
            is ScreenshotAutoSave.Decision.Ask -> {
                draft = result
                // A clear successful payment you made is shown for you to confirm; anything else opens the full form.
                val offer = ScreenshotProposal.of(shot, s.ledger)
                if (offer != null) {
                    _proposal.value = ProposalOffer(offer, result.app)
                    requestRoute(Routes.CONFIRM_SHOT)
                } else requestRoute(Routes.addDraft(result.income))
            }
        }
    }

    /** What a read screenshot offers, waiting on the confirm screen for Add, Edit or Cancel. */
    data class ProposalOffer(val proposal: ScreenshotProposal, val app: String)

    private val _proposal = MutableStateFlow<ProposalOffer?>(null)
    val proposal: StateFlow<ProposalOffer?> = _proposal.asStateFlow()

    /** Adds the offered payment, in the category chosen on the confirm screen, with Undo. Once only: a second tap does nothing. */
    fun addProposal(categoryId: Long?) = viewModelScope.launch {
        screenshotLock.withLock {
            val offer = _proposal.value ?: return@withLock
            _proposal.value = null
            draft = null
            val tx = offer.proposal.tx.copy(categoryId = categoryId ?: offer.proposal.tx.categoryId)
            val id = repository.addTransaction(tx)
            showToast(Toast("Saved ${Money.format(tx.amount)} · ${tx.merchant.ifBlank { "Payment" }}", "From your screenshot · Undo if it's wrong", id))
        }
    }

    /** Leaves the confirm screen without adding anything; Edit keeps the draft for the full form, Cancel drops everything. */
    fun leaveProposal(keepDraft: Boolean) {
        _proposal.value = null
        if (!keepDraft) draft = null
    }

    fun setAutoSaveScreenshots(value: Boolean) = run { settings.setAutoSaveScreenshots(value) }

    // Updates: only ever on a tap, or the optional once-a-day check.
    val updates = UpdateManager(
        source = { app.updateSource }, installedVersion = BuildConfig.VERSION_NAME, dir = java.io.File(app.cacheDir, "updates"),
        verify = { file, info -> app.updateVerify(file, info) }, scope = viewModelScope,
    )
    val updateState: StateFlow<UpdateState> = updates.state

    fun checkForUpdates() = updates.check()
    fun downloadUpdate() = updates.download()
    fun setAutoCheckUpdates(value: Boolean) = run { settings.setAutoCheckUpdates(value) }

    /** Hands the verified file to Android's installer (or first to the screen that allows Leftover to install apps). */
    fun installUpdate(context: android.content.Context) {
        val ready = updates.state.value as? UpdateState.Ready ?: return
        if (!UpdateInstaller.start(context, ready.file)) {
            showToast(Toast("Allow Leftover to install apps", "Switch it on, come back, and tap Install now again."))
        }
    }

    private fun checkForUpdatesIfDue() = viewModelScope.launch {
        val s = snapshot.filterNotNull().first()
        updates.autoCheckIfDue(s.settings.autoCheckUpdates, s.settings.lastUpdateCheckAt) { settings.setLastUpdateCheckAt(it) }
    }

    // After `updates` exists: the optional daily check, once per app start.
    init { checkForUpdatesIfDue() }

    /** One photo change at a time, so two quick picks can't leave the setting pointing at a deleted file. */
    private val photoLock = kotlinx.coroutines.sync.Mutex()

    fun pickProfilePhoto(uri: android.net.Uri) = run {
        photoLock.withLock {
            val name = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ProfilePhotoStore.importFrom(app, uri) }
            if (name == null) showToast(Toast("Couldn't use that picture", "Try another photo")) else useProfilePhoto(name)
        }
    }

    fun finishProfileCamera(name: String, taken: Boolean) = run {
        photoLock.withLock {
            val ok = taken && kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ProfilePhotoStore.finishCamera(app, name) }
            if (ok) useProfilePhoto(name)
            else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ProfilePhotoStore.keepOnly(app, settings.settings.first().profilePhoto) }
        }
    }

    fun removeProfilePhoto() = run { photoLock.withLock { useProfilePhoto(null) } }

    private suspend fun useProfilePhoto(name: String?) {
        settings.setProfilePhoto(name)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ProfilePhotoStore.keepOnly(app, name) }
    }

    fun attachReceipt(tx: TransactionEntity, fileName: String?) = run { repository.updateTransaction(tx.copy(receiptFile = fileName)) }

    /** Drops receipt photos that no entry points to any more, such as after a delete. */
    fun sweepReceipts() = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        val s = repository.currentSnapshot()
        ReceiptStore.sweep(app, s.transactions.mapNotNull { it.receiptFile }.toSet())
    }

    /** Saves a transaction and confirms it with an undoable toast. */
    fun saveWithToast(tx: TransactionEntity, title: String, subtitle: String, tags: Set<Long> = emptySet()) = run {
        val id = repository.addTransaction(tx)
        if (tags.isNotEmpty()) repository.setTransactionTags(id, tags)
        showToast(Toast(title, subtitle, id))
    }

    private fun run(block: suspend () -> Unit) = viewModelScope.launch { block() }

    fun completeOnboarding(answers: OnboardingAnswers, then: () -> Unit) = run {
        repository.completeOnboarding(answers)
        then()
    }

    // Transactions
    fun addTransaction(tx: TransactionEntity) = run { repository.addTransaction(tx) }
    fun updateTransaction(tx: TransactionEntity) = run { repository.updateTransaction(tx) }

    /** Moves an entry to another category from an Activity swipe, with Undo. */
    fun recategorize(tx: TransactionEntity, categoryId: Long) = run {
        val name = repository.currentSnapshot().categories.firstOrNull { it.id == categoryId }?.name ?: return@run
        repository.updateTransaction(tx.copy(categoryId = categoryId))
        showToast(Toast("Moved to $name", tx.merchant.ifBlank { tx.note }.ifBlank { "Entry" }, revert = tx))
    }
    fun deleteTransaction(id: Long) = run { repository.deleteTransaction(id) }

    // Accounts and categories
    fun addAccount(account: AccountEntity) = run { repository.addAccount(account) }
    fun updateAccount(account: AccountEntity) = run { repository.updateAccount(account) }
    fun setAccountHidden(account: AccountEntity, hidden: Boolean) = run { repository.setAccountHidden(account, hidden) }
    fun mergeCategory(from: Long, into: Long) = run { repository.mergeCategory(from, into) }
    fun addCategory(category: CategoryEntity) = run { repository.addCategory(category) }
    fun updateCategory(category: CategoryEntity) = run { repository.updateCategory(category) }
    fun updateCategories(categories: List<CategoryEntity>) = run { repository.updateCategories(categories) }

    // Goals, bills, wishlist
    fun addGoal(goal: GoalEntity) = run { repository.addGoal(goal) }
    fun updateGoal(goal: GoalEntity) = run { repository.updateGoal(goal) }

    /** Moves money into or out of a goal's savings account; the money stops counting toward Left to spend. */
    fun moveGoalMoney(goal: GoalEntity, amount: Long, otherAccountId: Long, deposit: Boolean) = run {
        repository.moveGoalMoney(goal, amount, otherAccountId, deposit)
    }

    fun saveBill(bill: BillEntity) = run { repository.saveBill(bill) }
    fun markBillPaid(bill: BillEntity, due: LocalDate, fromAccountId: Long) = run { repository.markBillPaid(bill, due, fromAccountId) }
    fun addPerson(name: String, upiId: String?) = run {
        val taken = snapshot.value?.shared?.people?.size ?: 0
        repository.addPerson(name, upiId, PersonColors[taken % PersonColors.size])
    }
    fun updatePerson(person: PersonEntity) = run { repository.updatePerson(person) }
    fun addGroup(group: GroupEntity, memberIds: Set<Long>) = run { repository.addGroup(group, memberIds) }
    fun updateGroup(group: GroupEntity, memberIds: Set<Long>) = run { repository.updateGroup(group, memberIds) }
    private companion object {
        /** How long the app can be out of sight before it asks to be unlocked again. */
        const val RELOCK_AFTER_MILLIS = 30_000L
    }

    // App lock: unlocked for this session until the app has been out of sight for a while.
    private val unlocked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = combine(snapshot, unlocked) { s, open -> s?.settings?.appLock == true && !open }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private var leftAt = 0L
    fun unlock() { unlocked.value = true }

    /** Saves a new PIN (hashed) and switches app lock on, since a PIN on its own would do nothing. */
    fun setPin(pin: String, then: () -> Unit) = viewModelScope.launch {
        val salt = Pin.newSalt()
        val hash = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { Pin.hash(pin, salt) }
        settings.setPin(hash, salt)
        settings.setAppLock(true)
        unlocked.value = true
        then()
    }

    /** Removes the PIN. App lock stays on only if the phone's own lock can still open it. */
    fun clearPin(phoneLockAvailable: Boolean) = run {
        settings.setPin(null, null)
        if (!phoneLockAvailable) settings.setAppLock(false)
    }

    /** Checks a PIN at the lock screen. Calls back with null on success, or a message to show. */
    fun tryPin(pin: String, onResult: (String?) -> Unit) = viewModelScope.launch {
        val s = snapshot.value?.settings ?: return@launch
        val now = System.currentTimeMillis()
        if (s.pinLockedUntil > now) { onResult("Too many tries. Wait ${((s.pinLockedUntil - now) / 1000) + 1} seconds."); return@launch }
        val ok = s.pinHash != null && s.pinSalt != null &&
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { Pin.matches(pin, s.pinHash, s.pinSalt) }
        if (ok) {
            settings.setPinFailures(0, 0L)
            unlocked.value = true
            onResult(null)
        } else {
            val failures = s.pinFailures + 1
            val wait = Pin.waitAfter(failures)
            settings.setPinFailures(failures, if (wait > 0) now + wait else 0L)
            onResult(if (wait > 0) "Wrong PIN. Try again in ${wait / 1000} seconds." else "Wrong PIN. ${Pin.FREE_TRIES - failures} ${if (Pin.FREE_TRIES - failures == 1) "try" else "tries"} before a wait.")
        }
    }
    fun setAppLock(on: Boolean) = run { settings.setAppLock(on); unlocked.value = true }
    fun setHideInSwitcher(on: Boolean) = run { settings.setHideInSwitcher(on) }
    fun onLeftApp() { leftAt = android.os.SystemClock.elapsedRealtime() }
    fun onBackInApp() {
        if (leftAt != 0L && android.os.SystemClock.elapsedRealtime() - leftAt > RELOCK_AFTER_MILLIS) unlocked.value = false
        leftAt = 0L
    }

    fun restoreWish(wish: WishEntity) = run { repository.restoreWish(wish) }
    fun keepSubscription(id: Long) = run { repository.keepSubscription(id) }
    fun materialiseRecurring() = run { repository.materialiseRecurring() }
    fun stopRecurringSplit(id: Long) = run { repository.stopRecurringSplit(id) }
    fun addSharedExpense(expense: NewSharedExpense, toast: Toast, repeatMonthly: Boolean = false) = run {
        repository.addSharedExpense(expense)
        if (repeatMonthly) {
            val day = expense.occurredAt.toLocalDate().let { it.dayOfMonth }
            repository.addRecurringSplit(
                RecurringSplitEntity(
                    title = expense.title, total = expense.total, paidBy = expense.paidBy,
                    participants = expense.shares.keys.filter { it != ME }.joinToString(","), includesMe = ME in expense.shares,
                    groupId = expense.groupId, categoryId = expense.categoryId, accountId = expense.accountId, dueDay = day,
                    lastCreatedEpochDay = expense.occurredAt.toLocalDate().toEpochDay(),
                ),
            )
        }
        showToast(toast)
    }
    fun deleteSharedExpense(id: Long) = run { repository.deleteSharedExpense(id) }
    fun deleteSettlement(id: Long) = run { repository.deleteSettlement(id) }
    fun addSettlement(personId: Long, amount: Long, direction: SettleDirection, method: SettleMethod, accountId: Long, groupId: Long? = null) = run {
        repository.addSettlement(personId, amount, direction, method, accountId, groupId)
    }
    fun setSmsTracking(on: Boolean) = run { repository.setSmsTracking(on) }
    fun setSmsAutoSave(on: Boolean) = run { repository.setSmsAutoSave(on) }
    fun setSmsNotify(on: Boolean) = run { repository.setSmsNotify(on) }

    /** Saves every waiting payment that is clear: a known account and category, and a plain spend. */
    fun acceptSuggested() = run {
        snapshot.value?.pendingSms.orEmpty().filter { it.kind == SmsKind.DEBIT && it.accountId != null && it.suggestedCategoryId != null && it.reason != PendingReason.POSSIBLE_TRANSFER && it.reason != PendingReason.POSSIBLE_DUPLICATE }
            .forEach { repository.approvePending(it, TxType.EXPENSE, it.suggestedCategoryId, it.accountId!!) }
    }
    fun setCaptureNotifications(on: Boolean) = run { repository.setCaptureNotifications(on) }
    fun setNotificationApp(packageName: String, allowed: Boolean) = run { repository.setNotificationApp(packageName, allowed) }
    fun setSenderTrusted(sender: String, trusted: Boolean) = run { repository.setSenderTrusted(sender, trusted) }
    fun approvePending(pending: PendingSmsEntity, type: TxType, categoryId: Long?, accountId: Long) = run { repository.approvePending(pending, type, categoryId, accountId) }
    fun dismissPending(id: Long) = run { repository.dismissPending(id) }
    fun deleteSmsData() = run { repository.deleteSmsData() }

    /** Looks back through the inbox once. Returns null to [onDone] when the SMS permission is missing. */
    fun scanInbox(days: Int, onDone: (ScanResult?) -> Unit) {
        viewModelScope.launch {
            val since = System.currentTimeMillis() - days * 86_400_000L
            onDone(SmsInboxScanner.scan(app, app.ingestor, since))
        }
    }

    /** Writes a backup file to [uri], sealed with the passphrase key when protection is on. [onDone] gets null on success or a message. */
    fun writeBackup(uri: Uri, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val problem = try {
                val made = repository.createBackupText()
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(made.text.toByteArray()) } ?: error("Couldn't open the file")
                }
                settings.recordBackup(System.currentTimeMillis(), null)
                null
            } catch (e: BackupException) {
                e.message
            } catch (_: Exception) {
                "Couldn't save the backup."
            }
            onDone(problem)
        }
    }

    /** Reads the file at [uri]. A plain backup is restored at once; an encrypted one asks for its passphrase first. */
    fun readAndRestore(uri: Uri, onDone: (RestoreOutcome) -> Unit) {
        viewModelScope.launch {
            val outcome = try {
                val text = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) } ?: throw BackupException("Couldn't open that file.")
                }
                if (BackupCrypto.looksEncrypted(text)) RestoreOutcome.NeedsPassphrase(text)
                else {
                    repository.restoreBackup(BackupCodec.decode(text))
                    RestoreOutcome.Done
                }
            } catch (e: BackupException) {
                RestoreOutcome.Failed(e.message.orEmpty())
            } catch (_: Exception) {
                RestoreOutcome.Failed("Something went wrong reading that file.")
            }
            onDone(outcome)
        }
    }

    /** Opens an encrypted backup with [passphrase] and restores it. */
    fun restoreEncrypted(text: String, passphrase: CharArray, onDone: (RestoreOutcome) -> Unit) {
        viewModelScope.launch {
            val outcome = try {
                val plain = withContext(Dispatchers.Default) { BackupCrypto.decrypt(text, passphrase) }
                repository.restoreBackup(BackupCodec.decode(plain))
                RestoreOutcome.Done
            } catch (e: WrongPassphraseException) {
                RestoreOutcome.Failed(e.message.orEmpty())
            } catch (e: BackupException) {
                RestoreOutcome.Failed(e.message.orEmpty())
            } catch (_: Exception) {
                RestoreOutcome.Failed("Something went wrong restoring that backup.")
            } finally {
                passphrase.fill('\u0000')
            }
            onDone(outcome)
        }
    }

    fun setBackupPassphrase(passphrase: CharArray, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.setBackupPassphrase(passphrase)
            passphrase.fill('\u0000')
            onDone()
        }
    }

    fun clearBackupPassphrase() = run { repository.clearBackupPassphrase() }

    /** Turns scheduled backups on or off. They need a folder; turning one on without it does nothing. */
    fun setAutoBackup(on: Boolean, folder: String?, everyDays: Int) = run {
        val enabled = on && folder != null
        settings.setAutoBackup(enabled, folder, everyDays)
        if (enabled) AutoBackup.schedule(app, everyDays) else AutoBackup.cancel(app)
    }

    fun addRule(pattern: String, categoryId: Long) = run { repository.addRule(pattern, categoryId) }
    fun deleteRule(id: Long) = run { repository.deleteRule(id) }
    fun setSenderIgnored(sender: String, ignored: Boolean) = run { repository.setSenderIgnored(sender, ignored) }
    fun setAcceptLinks(on: Boolean) = run { repository.setAcceptLinks(on) }
    fun setMandateStatus(mandate: MandateEntity, status: MandateStatus) = run { repository.setMandateStatus(mandate, status) }
    fun trackMandate(mandate: MandateEntity) = run {
        val bank = snapshot.value?.accounts?.firstOrNull { it.type == AccountType.BANK }?.id
        repository.trackMandate(mandate, LocalDate.now().dayOfMonth, bank)
    }

    /** An entry from another app's link. Returns a message for the user: it is queued for review, or refused. */
    fun captureFromLink(entry: LinkEntry?, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val current = repository.currentSnapshot()
            onResult(
                when {
                    entry == null -> "Leftover couldn't read that entry."
                    !current.settings.acceptLinks -> "Turn on \"Accept entries from other apps\" in Automatic tracking first."
                    else -> { repository.queueFromApp(entry); "Added to your review queue." }
                },
            )
        }
    }

    fun importTransactions(transactions: List<TransactionEntity>, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.importTransactions(transactions)
            onDone()
        }
    }

    fun addTag(name: String, color: String, budget: Long?) = run { repository.addTag(name, color, budget) }
    fun updateTag(tag: TagEntity) = run { repository.updateTag(tag) }
    fun deleteTag(id: Long) = run { repository.deleteTag(id) }
    fun setTransactionTags(transactionId: Long, tags: Set<Long>) = run { repository.setTransactionTags(transactionId, tags) }
    fun addTemplate(template: TemplateEntity) = run { repository.addTemplate(template) }
    fun deleteTemplate(id: Long) = run { repository.deleteTemplate(id) }

    fun addEmi(emi: EmiEntity) = run { repository.addEmi(emi) }
    fun deleteEmi(emi: EmiEntity) = run { repository.updateEmi(emi.copy(archived = true)) }
    fun markEmiPaid(emi: EmiEntity, due: LocalDate, fallbackAccount: Long?) = run {
        val account = emi.accountId ?: fallbackAccount ?: return@run
        repository.markEmiPaid(emi, due, account)
    }

    fun addWish(wish: WishEntity) = run { repository.addWish(wish) }
    fun resolveWish(wish: WishEntity, status: WishStatus) = run { repository.resolveWish(wish, status) }
    fun postponeWish(wish: WishEntity, days: Int) = run { repository.postponeWish(wish, days) }

    fun deleteEverything(then: () -> Unit) = run {
        AutoBackup.cancel(app)
        repository.deleteEverything()
        ReceiptStore.deleteAll(app)
        ProfilePhotoStore.deleteAll(app)
        then()
    }

    // Settings
    fun setHideBalances(value: Boolean) = run { settings.setHideBalances(value) }
    fun setThemeMode(value: ThemeMode) = run { settings.setThemeMode(value) }
    fun setBillReminders(value: Boolean) = run { settings.setBillReminders(value) }
    fun setPaydayReminder(value: Boolean) = run { settings.setPaydayReminder(value) }
    fun setBudgetAlerts(value: Boolean) = run { settings.setBudgetAlerts(value) }
    fun setWeeklyCheckIn(value: Boolean) = run { settings.setWeeklyCheckIn(value) }
    fun saveCardPerk(perk: CardPerkEntity) = run { repository.saveCardPerk(perk) }
    fun addChallenge(challenge: ChallengeEntity) = run { repository.addChallenge(challenge) }
    fun deleteChallenge(id: Long) = run { repository.deleteChallenge(id) }
    fun setIncome(type: String, usual: Long) = run { settings.setIncome(type, usual) }
    fun setIrregularIncome(value: Boolean) = run { settings.setIrregularIncome(value) }
    fun setVoiceLanguage(value: String) = run { settings.setVoiceLanguage(value) }
    fun setPaydayDay(value: Int) = run { settings.setPaydayDay(value) }
    fun setName(value: String) = run { settings.setName(value) }
    fun setPureBlack(value: Boolean) = run { settings.setPureBlack(value) }
    fun setSpendingBudget(paise: Long) = run { settings.setSpendingBudget(paise) }
    fun setAccent(value: app.leftover.data.Accent) = run { settings.setAccent(value) }
    fun setFont(value: app.leftover.data.AppFont) = run { settings.setFont(value) }
    fun setDailyLog(value: Boolean) = run { settings.setDailyLog(value) }
    fun setBackupReminder(value: Boolean) = run { settings.setBackupReminder(value) }
}
