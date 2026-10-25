package app.leftover.data

import app.leftover.data.db.AccountEntity
import app.leftover.data.db.AccountType
import app.leftover.data.db.BillEntity
import app.leftover.data.db.CategoryEntity
import app.leftover.data.db.CaptureRuleEntity
import app.leftover.data.db.CardPerkEntity
import app.leftover.data.db.ChallengeEntity
import app.leftover.data.db.RecurringSplitEntity
import app.leftover.data.db.TagEntity
import app.leftover.data.db.TemplateEntity
import app.leftover.data.db.TransactionTagEntity
import app.leftover.data.db.DefaultCategories
import app.leftover.data.db.MandateEntity
import app.leftover.data.db.MandateStatus
import app.leftover.data.db.EmiEntity
import androidx.room.withTransaction
import app.leftover.data.db.GoalEntity
import app.leftover.data.db.GroupEntity
import app.leftover.data.db.GroupMemberEntity
import app.leftover.data.db.LeftoverDatabase
import app.leftover.data.db.PersonEntity
import app.leftover.data.db.SettleDirection
import app.leftover.data.db.SettleMethod
import app.leftover.data.db.PendingSmsEntity
import app.leftover.data.db.SettlementEntity
import app.leftover.data.db.SmsAccountLinkEntity
import app.leftover.data.db.SmsKeyEntity
import app.leftover.data.db.SmsSenderEntity
import app.leftover.data.db.TxSource
import app.leftover.data.db.SharedExpenseEntity
import app.leftover.data.db.SplitShareEntity
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxType
import app.leftover.data.db.WishEntity
import app.leftover.data.db.WishStatus
import app.leftover.domain.Ledger
import app.leftover.domain.ME
import app.leftover.domain.PayCycle
import app.leftover.domain.SharedBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Everything the UI needs, recomputed whenever any table or setting changes. */
data class Snapshot(
    val settings: Settings,
    val ledger: Ledger,
    val wishes: List<WishEntity>,
    val smsSenders: List<SmsSenderEntity> = emptyList(),
    val emis: List<EmiEntity> = emptyList(),
    val rules: List<CaptureRuleEntity> = emptyList(),
    val mandates: List<MandateEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val txTags: List<TransactionTagEntity> = emptyList(),
    val templates: List<TemplateEntity> = emptyList(),
    val perks: List<CardPerkEntity> = emptyList(),
    val challenges: List<ChallengeEntity> = emptyList(),
    val recurring: List<RecurringSplitEntity> = emptyList(),
    val pendingSms: List<PendingSmsEntity> = emptyList(),
) {
    val accounts get() = ledger.accounts
    val categories get() = ledger.categories
    val transactions get() = ledger.transactions
    val goals get() = ledger.goals
    val bills get() = ledger.bills
    val cycle get() = ledger.cycle
    val today get() = ledger.today
    val shared get() = ledger.shared
}

/** A bill to split. [shares] holds what each participant owes, including your own share under [ME]. */
data class NewSharedExpense(
    val title: String,
    val total: Long,
    val paidBy: Long,
    val shares: Map<Long, Long>,
    val groupId: Long? = null,
    val categoryId: Long? = null,
    /** The account the money left, when you paid. Ignored when a friend paid. */
    val accountId: Long? = null,
    val occurredAt: Long = System.currentTimeMillis(),
    val note: String = "",
)

/** First-run answers, committed in one go when onboarding finishes. */
data class OnboardingAnswers(
    val firstExpenseAmount: Long?,
    val firstExpenseCategory: String?,
    val incomeType: String,
    val usualIncome: Long,
    val paydayDay: Int,
    val balanceNow: Long,
    val bills: List<Pair<String, Long>>,
)

class LeftoverRepository(private val db: LeftoverDatabase, private val settingsRepository: SettingsRepository) {

    private data class Tables(
        val accounts: List<AccountEntity>,
        val categories: List<CategoryEntity>,
        val transactions: List<TransactionEntity>,
        val goals: List<GoalEntity>,
        val bills: List<BillEntity>,
    )

    private data class SharedTables(
        val people: List<PersonEntity>,
        val groups: List<GroupEntity>,
        val members: List<GroupMemberEntity>,
        val expenses: List<SharedExpenseEntity>,
        val shares: List<SplitShareEntity>,
    )

    private val sharedTables: Flow<SharedTables> = combine(
        db.shared().observePeople(),
        db.shared().observeGroups(),
        db.shared().observeMembers(),
        db.shared().observeExpenses(),
        db.shared().observeShares(),
    ) { people, groups, members, expenses, shares -> SharedTables(people, groups, members, expenses, shares) }

    private data class Extras(val wishes: List<WishEntity>, val senders: List<SmsSenderEntity>, val pending: List<PendingSmsEntity>, val emis: List<EmiEntity>, val rules: List<CaptureRuleEntity>, val mandates: List<MandateEntity>, val more: MoreTables)

    private data class MoreTables(
        val tags: List<TagEntity>, val txTags: List<TransactionTagEntity>, val templates: List<TemplateEntity>,
        val perks: List<CardPerkEntity>, val challenges: List<ChallengeEntity>, val recurring: List<RecurringSplitEntity>,
    )

    private val moreTables: Flow<MoreTables> = combine(
        db.more().observeTags(), db.more().observeTxTags(), db.more().observeTemplates(), db.more().observePerks(),
        combine(db.more().observeChallenges(), db.more().observeRecurring()) { c, r -> c to r },
    ) { tags, txTags, templates, perks, cr -> MoreTables(tags, txTags, templates, perks, cr.first, cr.second) }

    private val extras: Flow<Extras> = combine(db.wishlist().observeAll(), db.sms().observeSenders(), db.sms().observePending(), db.emis().observeAll(), combine(db.sms().observeRules(), db.sms().observeMandates(), moreTables) { r, m, more -> Triple(r, m, more) }) { wishes, senders, pending, emis, rmm ->
        Extras(wishes, senders, pending, emis, rmm.first, rmm.second, rmm.third)
    }

    val snapshot: Flow<Snapshot> = combine(
        combine(
            db.accounts().observeAll(),
            db.categories().observeAll(),
            db.transactions().observeAll(),
            db.goals().observeAll(),
            db.bills().observeAll(),
        ) { accounts, categories, transactions, goals, bills -> Tables(accounts, categories, transactions, goals, bills) },
        extras,
        sharedTables,
        db.shared().observeSettlements(),
        settingsRepository.settings,
    ) { tables, extras, sharedTables, settlements, settings ->
        val today = LocalDate.now()
        val cycle = PayCycle.forPayday(settings.paydayDay, today)
        Snapshot(
            settings = settings,
            ledger = Ledger(
                tables.accounts, tables.categories, tables.transactions, tables.goals, tables.bills, cycle, today,
                shared = SharedBook(sharedTables.people, sharedTables.groups, sharedTables.members, sharedTables.expenses, sharedTables.shares, settlements),
                emis = extras.emis,
            ),
            wishes = extras.wishes,
            smsSenders = extras.senders,
            pendingSms = extras.pending,
            emis = extras.emis,
            rules = extras.rules,
            mandates = extras.mandates,
            tags = extras.more.tags, txTags = extras.more.txTags, templates = extras.more.templates,
            perks = extras.more.perks, challenges = extras.more.challenges, recurring = extras.more.recurring,
        )
    }

    suspend fun currentSnapshot(): Snapshot = snapshot.first()

    suspend fun ensureCategories() {
        if (db.categories().count() == 0) db.categories().insertAll(DefaultCategories.all)
    }

    suspend fun completeOnboarding(answers: OnboardingAnswers) {
        ensureCategories()
        val expense = answers.firstExpenseAmount ?: 0
        // The balance is what the user has now, after their first expense, so the opening balance adds it back.
        val accountId = db.accounts().insert(
            AccountEntity(name = "Main account", type = AccountType.BANK, openingBalance = answers.balanceNow + expense, includeInLeft = true),
        )
        if (expense > 0) {
            val categoryId = db.categories().idByName(answers.firstExpenseCategory ?: "Other")
            addTransaction(
                TransactionEntity(type = TxType.EXPENSE, amount = expense, accountId = accountId, categoryId = categoryId, occurredAt = System.currentTimeMillis()),
            )
        }
        val dueDay = if (answers.paydayDay > 1) answers.paydayDay - 1 else 28
        db.bills().insertAll(answers.bills.map { (name, amount) ->
            BillEntity(name = name, amount = amount, dueDay = dueDay, icon = billIcon(name), accountId = accountId, isSubscription = name == "Spotify")
        })
        settingsRepository.completeOnboarding(answers.paydayDay, answers.incomeType, answers.usualIncome)
    }

    /** Wipes every table and the settings, returning the app to first-run state. */
    suspend fun deleteEverything() {
        withContext(Dispatchers.IO) { db.clearAllTables() }
        settingsRepository.clear()
        ensureCategories()
    }

    suspend fun addTransaction(tx: TransactionEntity): Long = db.transactions().insert(tx)
    suspend fun updateTransaction(tx: TransactionEntity) = db.transactions().update(tx)

    /** Deleting a transaction also removes the split or settlement it belongs to, so balances never drift from the ledger. */
    suspend fun deleteTransaction(id: Long) = db.withTransaction {
        val shared = db.shared()
        shared.expenseIdsForTransaction(id).forEach { expenseId ->
            shared.deleteSharesOf(expenseId)
            shared.deleteExpense(expenseId)
        }
        shared.deleteSettlementsForTransaction(id)
        db.more().clearTagsOf(id)
        db.transactions().deleteById(id)
    }
    fun observeTransaction(id: Long) = db.transactions().observe(id)

    suspend fun addAccount(account: AccountEntity): Long = db.accounts().insert(account)
    suspend fun updateAccount(account: AccountEntity) = db.accounts().update(account)

    suspend fun updateCategory(category: CategoryEntity) = db.categories().update(category)
    suspend fun updateCategories(categories: List<CategoryEntity>) = db.categories().updateAll(categories)
    suspend fun addCategory(category: CategoryEntity) = db.categories().insert(category)

    /** Folds [from] into [to]: its entries, bills and rules move across, budgets add together, and [from] is removed. */
    suspend fun mergeCategory(from: Long, to: Long) {
        if (from == to) return
        db.withTransaction {
            val dao = db.categories()
            val all = dao.all()
            val source = all.firstOrNull { it.id == from } ?: return@withTransaction
            val target = all.firstOrNull { it.id == to } ?: return@withTransaction
            dao.moveTransactions(from, to); dao.moveBills(from, to); dao.moveWishes(from, to); dao.moveSharedExpenses(from, to)
            dao.moveTemplates(from, to); dao.moveChallenges(from, to); dao.moveRecurring(from, to); dao.moveRules(from, to); dao.movePending(from, to)
            val limit = listOfNotNull(source.budgetLimit, target.budgetLimit).takeIf { it.isNotEmpty() }?.sum()
            dao.update(target.copy(budgetLimit = limit, rollover = target.rollover || source.rollover))
            dao.delete(from)
        }
    }

    /** The account that holds money set aside for goals; created on first use and never counted in Left to spend. */
    suspend fun goalSavingsAccountId(): Long =
        db.accounts().firstOfType(AccountType.SAVINGS)?.id
            ?: db.accounts().insert(AccountEntity(name = "Goal savings", type = AccountType.SAVINGS, openingBalance = 0, includeInLeft = false))

    suspend fun moveGoalMoney(goal: GoalEntity, amount: Long, otherAccountId: Long, deposit: Boolean) {
        val savings = goalSavingsAccountId()
        addTransaction(
            TransactionEntity(
                type = if (deposit) TxType.GOAL_SAVE else TxType.GOAL_WITHDRAW,
                amount = amount,
                accountId = if (deposit) otherAccountId else savings,
                toAccountId = if (deposit) savings else otherAccountId,
                goalId = goal.id,
                merchant = goal.name,
                occurredAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun addGoal(goal: GoalEntity): Long = db.goals().insert(goal)
    suspend fun updateGoal(goal: GoalEntity) = db.goals().update(goal)

    suspend fun saveBill(bill: BillEntity) = db.bills().upsert(bill)

    suspend fun markBillPaid(bill: BillEntity, dueDate: LocalDate, fromAccountId: Long) {
        db.bills().upsert(bill.copy(paidThroughEpochDay = dueDate.toEpochDay()))
        addTransaction(
            TransactionEntity(type = TxType.EXPENSE, amount = bill.amount, accountId = fromAccountId, categoryId = bill.categoryId, merchant = bill.name, occurredAt = System.currentTimeMillis()),
        )
    }

    suspend fun addPerson(name: String, upiId: String?, color: String): Long =
        db.shared().insertPerson(PersonEntity(name = name.trim(), upiId = upiId?.trim()?.ifBlank { null }, color = color))

    suspend fun updatePerson(person: PersonEntity) = db.shared().updatePerson(person)

    suspend fun addGroup(group: GroupEntity, memberIds: Set<Long>): Long = db.withTransaction {
        val id = db.shared().insertGroup(group)
        db.shared().insertMembers(memberIds.map { GroupMemberEntity(id, it) })
        id
    }

    suspend fun updateGroup(group: GroupEntity, memberIds: Set<Long>) = db.withTransaction {
        db.shared().updateGroup(group)
        db.shared().clearMembers(group.id)
        db.shared().insertMembers(memberIds.map { GroupMemberEntity(group.id, it) })
    }

    /**
     * Saves a split bill. When you paid, your account moves by the full total but budgets only count your share
     * (the rest is recorded as [TransactionEntity.splitOthers]). When a friend paid, nothing leaves your accounts
     * and only your own share counts as spending.
     */
    suspend fun addRecurringSplit(rule: RecurringSplitEntity) { db.more().insertRecurring(rule) }
    suspend fun stopRecurringSplit(id: Long) {
        db.more().allRecurring().firstOrNull { it.id == id }?.let { db.more().updateRecurring(it.copy(archived = true)) }
    }

    /** Creates this month's split for each repeating shared bill that has come due. Safe to run any number of times. */
    suspend fun materialiseRecurring(today: java.time.LocalDate = java.time.LocalDate.now()): Int {
        var created = 0
        db.more().allRecurring().filter { !it.archived }.forEach { rule ->
            val dates = app.leftover.domain.RecurringSplits.due(rule, today)
            dates.forEach { date ->
                addSharedExpense(
                    NewSharedExpense(
                        title = rule.title, total = rule.total, paidBy = rule.paidBy, shares = app.leftover.domain.RecurringSplits.shares(rule),
                        groupId = rule.groupId, categoryId = rule.categoryId, accountId = rule.accountId.takeIf { rule.paidBy == app.leftover.domain.ME },
                        occurredAt = date.atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    ),
                )
                created++
            }
            // Mark the month as done even if the cap skipped older ones.
            val newest = app.leftover.domain.RecurringSplits.due(rule, today, maxCatchUp = Int.MAX_VALUE).lastOrNull()
            if (newest != null) db.more().updateRecurring(rule.copy(lastCreatedEpochDay = newest.toEpochDay()))
        }
        return created
    }

    suspend fun addSharedExpense(expense: NewSharedExpense): Long = db.withTransaction {
        val myShare = expense.shares[ME] ?: 0
        val transactionId = if (expense.paidBy == ME && expense.accountId != null) {
            addTransaction(
                TransactionEntity(
                    type = TxType.EXPENSE,
                    amount = expense.total,
                    accountId = expense.accountId,
                    categoryId = expense.categoryId,
                    merchant = expense.title,
                    note = expense.note,
                    occurredAt = expense.occurredAt,
                    splitOthers = expense.total - myShare,
                ),
            )
        } else null
        val id = db.shared().insertExpense(
            SharedExpenseEntity(
                title = expense.title, total = expense.total, paidBy = expense.paidBy, groupId = expense.groupId,
                categoryId = expense.categoryId, occurredAt = expense.occurredAt, transactionId = transactionId, note = expense.note,
            ),
        )
        db.shared().insertShares(expense.shares.filter { it.value > 0 }.map { (person, amount) -> SplitShareEntity(expenseId = id, personId = person, amount = amount) })
        id
    }

    suspend fun deleteSharedExpense(id: Long) = db.withTransaction {
        val expense = db.shared().expense(id) ?: return@withTransaction
        db.shared().deleteSharesOf(id)
        db.shared().deleteExpense(id)
        expense.transactionId?.let { db.transactions().deleteById(it) }
    }

    suspend fun deleteSettlement(id: Long) = db.withTransaction {
        val settlement = db.shared().settlement(id) ?: return@withTransaction
        db.shared().deleteSettlement(id)
        settlement.transactionId?.let { db.transactions().deleteById(it) }
    }

    /** Records money changing hands with a friend. Either way it moves your account but is never income or spending. */
    suspend fun addSettlement(
        personId: Long,
        amount: Long,
        direction: SettleDirection,
        method: SettleMethod,
        accountId: Long,
        groupId: Long? = null,
        note: String = "",
    ): Long = db.withTransaction {
        val person = db.shared().observePeople().first().firstOrNull { it.id == personId }
        val now = System.currentTimeMillis()
        val transactionId = addTransaction(
            TransactionEntity(
                type = if (direction == SettleDirection.THEY_PAID) TxType.REPAYMENT else TxType.SETTLE_OUT,
                amount = amount,
                accountId = accountId,
                merchant = person?.name.orEmpty(),
                note = note,
                occurredAt = now,
            ),
        )
        db.shared().insertSettlement(
            SettlementEntity(personId = personId, amount = amount, direction = direction, method = method, occurredAt = now, groupId = groupId, transactionId = transactionId, note = note),
        )
    }

    // ---- backup and restore ----

    /** Everything the user entered, as the text of a backup file. */
    suspend fun exportBackup(): String {
        val settings = settingsRepository.settings.first()
        val data = BackupData(
            settings = BackupSettings(settings.name, settings.paydayDay, settings.incomeType, settings.usualIncome, settings.hideBalances, settings.themeMode, settings.billReminders, settings.irregularIncome, settings.voiceLanguage),
            accounts = db.accounts().all(), categories = db.categories().all(), transactions = db.transactions().all(), goals = db.goals().all(),
            bills = db.bills().all(), wishes = db.wishlist().all(), people = db.shared().allPeople(), groups = db.shared().allGroups(),
            members = db.shared().allMembers(), expenses = db.shared().allExpenses(), shares = db.shared().allShares(), settlements = db.shared().allSettlements(),
            emis = db.emis().all(), rules = db.more().allRules(), tags = db.more().allTags(), txTags = db.more().allTxTags(),
            templates = db.more().allTemplates(), perks = db.more().allPerks(), challenges = db.more().allChallenges(), recurring = db.more().allRecurring(),
        )
        return BackupCodec.encode(data, System.currentTimeMillis())
    }

    /** A backup ready to save: sealed with the user's passphrase key when protection is on. */
    data class MadeBackup(val text: String, val encrypted: Boolean)

    suspend fun createBackupText(): MadeBackup {
        val plain = exportBackup()
        val settings = settingsRepository.settings.first()
        if (!settings.backupProtected) return MadeBackup(plain, false)
        val key = app.leftover.backup.BackupVault.unwrap(settings.backupWrappedKey!!)
            ?: throw BackupException("Backup protection needs to be set up again on this phone. Open Backup and restore and set a passphrase.")
        val salt = java.util.Base64.getDecoder().decode(settings.backupSalt!!)
        return MadeBackup(withContext(Dispatchers.Default) { BackupCrypto.encrypt(plain, key, salt) }, true)
    }

    /** Turns on passphrase protection: the key is made from [passphrase] and kept wrapped by Android Keystore. */
    suspend fun setBackupPassphrase(passphrase: CharArray) {
        val salt = BackupCrypto.newSalt()
        val key = withContext(Dispatchers.Default) { BackupCrypto.deriveKey(passphrase, salt) }
        settingsRepository.setBackupKey(java.util.Base64.getEncoder().encodeToString(salt), app.leftover.backup.BackupVault.wrap(key))
    }

    suspend fun clearBackupPassphrase() = settingsRepository.setBackupKey(null, null)

    /** Replaces everything on this phone with the backup. The old data is only removed if the whole backup goes in. */
    suspend fun restoreBackup(data: BackupData) {
        withContext(Dispatchers.IO) {
            db.withTransaction {
                db.clearAllTables()
                db.categories().insertAll(data.categories)
                db.accounts().insertAll(data.accounts)
                db.transactions().insertAll(data.transactions)
                db.goals().insertAll(data.goals)
                db.bills().insertAll(data.bills)
                db.wishlist().insertAll(data.wishes)
                db.shared().insertPeople(data.people)
                db.shared().insertGroups(data.groups)
                db.shared().insertMembers(data.members)
                db.shared().insertExpenses(data.expenses)
                db.shared().insertShares(data.shares)
                db.shared().insertSettlements(data.settlements)
                db.emis().insertAll(data.emis)
                db.more().insertRules(data.rules)
                db.more().insertTags(data.tags)
                db.more().insertTxTags(data.txTags)
                db.more().insertTemplates(data.templates)
                db.more().insertPerks(data.perks)
                db.more().insertChallenges(data.challenges)
                db.more().insertRecurringAll(data.recurring)
            }
        }
        settingsRepository.restore(data.settings)
    }

    // ---- automatic tracking from bank texts ----

    suspend fun setSmsTracking(on: Boolean) = settingsRepository.setSmsTracking(on)
    suspend fun setSmsAutoSave(on: Boolean) = settingsRepository.setSmsAutoSave(on)
    suspend fun setSmsNotify(on: Boolean) = settingsRepository.setSmsNotify(on)
    suspend fun noteSkipped(kind: app.leftover.domain.SmsParser.Skipped) =
        if (kind == app.leftover.domain.SmsParser.Skipped.OTP) settingsRepository.addSmsSkipped(1, 0) else settingsRepository.addSmsSkipped(0, 1)
    suspend fun setCaptureNotifications(on: Boolean) = settingsRepository.setCaptureNotifications(on)
    suspend fun setNotificationApp(packageName: String, allowed: Boolean) = settingsRepository.setNotificationApp(packageName, allowed)

    /** True when this text was already handled. Texts with a reference number match at any time; others only close together. */
    suspend fun smsSeen(fingerprint: String, at: Long, anyTime: Boolean): Boolean {
        val (from, to) = if (anyTime) Long.MIN_VALUE to Long.MAX_VALUE else (at - app.leftover.domain.SmsParser.windowMillis) to (at + app.leftover.domain.SmsParser.windowMillis)
        return db.sms().countKey(fingerprint, from, to) > 0
    }

    suspend fun rememberSms(fingerprint: String, at: Long) = db.sms().insertKey(SmsKeyEntity(fingerprint = fingerprint, at = at))

    /** Records that this sender sent a transaction text and returns its settings. */
    suspend fun touchSender(sender: String, at: Long): SmsSenderEntity {
        val existing = db.sms().sender(sender)
        val updated = existing?.copy(lastSeen = maxOf(existing.lastSeen, at), seen = existing.seen + 1) ?: SmsSenderEntity(sender = sender, lastSeen = at)
        db.sms().upsertSender(updated)
        return updated
    }

    suspend fun setSenderTrusted(sender: String, trusted: Boolean) {
        db.sms().sender(sender)?.let { db.sms().upsertSender(it.copy(trusted = trusted)) }
    }

    suspend fun addRule(pattern: String, categoryId: Long) { db.sms().insertRule(CaptureRuleEntity(pattern = pattern.trim(), categoryId = categoryId)) }
    suspend fun deleteRule(id: Long) = db.sms().deleteRule(id)

    suspend fun setSenderIgnored(sender: String, ignored: Boolean) {
        db.sms().sender(sender)?.let { db.sms().upsertSender(it.copy(ignored = ignored)) }
    }

    suspend fun senderIgnored(sender: String): Boolean = db.sms().sender(sender)?.ignored == true

    /** Records a recurring debit. Returns true when it is one the user hasn't seen before. */
    suspend fun noteMandate(merchant: String, amount: Long, frequency: String, nextDebit: java.time.LocalDate?, at: Long): Boolean {
        val existing = db.sms().mandate(merchant, amount)
        if (existing == null) {
            db.sms().upsertMandate(MandateEntity(merchant = merchant, amount = amount, frequency = frequency, nextDueEpochDay = nextDebit?.toEpochDay(), firstSeen = at, lastSeen = at))
            return true
        }
        db.sms().upsertMandate(existing.copy(lastSeen = at, frequency = frequency.ifBlank { existing.frequency }, nextDueEpochDay = nextDebit?.toEpochDay() ?: existing.nextDueEpochDay))
        return false
    }

    suspend fun setMandateStatus(mandate: MandateEntity, status: MandateStatus) { db.sms().upsertMandate(mandate.copy(status = status)) }

    /** Adds the mandate to Bills as a subscription so it shows up with the rest of what is due. */
    suspend fun trackMandate(mandate: MandateEntity, fallbackDay: Int, accountId: Long?) = db.withTransaction {
        val day = mandate.nextDueEpochDay?.let { java.time.LocalDate.ofEpochDay(it).dayOfMonth } ?: fallbackDay
        db.bills().upsert(BillEntity(name = mandate.merchant, amount = mandate.amount, dueDay = day, icon = billIcon(mandate.merchant), accountId = accountId, isSubscription = true))
        db.sms().upsertMandate(mandate.copy(status = MandateStatus.TRACKED))
    }

    /** An entry handed over by another app. It waits in the review queue; nothing is saved without the user. */
    suspend fun queueFromApp(entry: app.leftover.domain.LinkEntry): Long = db.sms().insertPending(
        PendingSmsEntity(
            sender = "OTHER APP", kind = entry.kind, amount = entry.amount, merchant = entry.merchant, last4 = null, isCard = false,
            occurredAt = System.currentTimeMillis(), reason = app.leftover.domain.PendingReason.FROM_APP,
            suggestedCategoryId = if (entry.kind == app.leftover.domain.SmsKind.DEBIT) app.leftover.domain.SmsPolicy.categoryFor(entry.merchant, db.transactions().all()) else null,
        ),
    )

    suspend fun setAcceptLinks(on: Boolean) = settingsRepository.setAcceptLinks(on)

    suspend fun linkedAccount(accountKey: String): Long? = db.sms().linkedAccount(accountKey)
    suspend fun addPending(pending: PendingSmsEntity): Long = db.sms().insertPending(pending)
    suspend fun pendingSms(id: Long): PendingSmsEntity? = db.sms().pending(id)
    suspend fun dismissPending(id: Long) = db.sms().deletePending(id)

    /** Saves a waiting text as a real transaction and remembers which account its digits belong to. */
    suspend fun approvePending(pending: PendingSmsEntity, type: TxType, categoryId: Long?, accountId: Long): Long = db.withTransaction {
        pending.last4?.let { digits ->
            db.sms().upsertLink(SmsAccountLinkEntity((if (pending.isCard) "card:" else "acct:") + digits, accountId))
        }
        db.sms().deletePending(pending.id)
        addTransaction(
            TransactionEntity(
                type = type, amount = pending.amount, accountId = accountId, categoryId = categoryId,
                merchant = pending.merchant, occurredAt = pending.occurredAt, source = if (pending.sender.startsWith("APP:")) TxSource.NOTIFICATION else TxSource.SMS,
            ),
        )
    }

    suspend fun saveFromSms(type: TxType, amount: Long, accountId: Long, categoryId: Long?, merchant: String, at: Long, source: TxSource = TxSource.SMS): Long =
        addTransaction(TransactionEntity(type = type, amount = amount, accountId = accountId, categoryId = categoryId, merchant = merchant, occurredAt = at, source = source))

    suspend fun smsTransactionCount(): Int = db.transactions().countFromSms()

    /** Turns tracking off and removes everything that came from texts. Entries the user added themselves stay. */
    suspend fun deleteSmsData() {
        db.withTransaction {
            db.transactions().deleteFromSms()
            db.sms().clearPending()
            db.sms().clearSenders()
            db.sms().clearKeys()
            db.sms().clearLinks()
            db.sms().clearMandates()
        }
        settingsRepository.setSmsTracking(false)
        settingsRepository.setCaptureNotifications(false)
    }

    // ---- tags, templates, card perks, challenges ----

    suspend fun addTag(name: String, color: String, budget: Long?): Long = db.more().insertTag(TagEntity(name = name.trim(), color = color, budget = budget))
    suspend fun updateTag(tag: TagEntity) = db.more().updateTag(tag)
    suspend fun deleteTag(id: Long) = db.withTransaction { db.more().clearTag(id); db.more().deleteTag(id) }

    /** Replaces the tags on one transaction. */
    suspend fun setTransactionTags(transactionId: Long, tagIds: Set<Long>) = db.withTransaction {
        db.more().clearTagsOf(transactionId)
        if (tagIds.isNotEmpty()) db.more().insertTxTags(tagIds.map { TransactionTagEntity(transactionId, it) })
    }

    suspend fun addTemplate(template: TemplateEntity): Long = db.more().insertTemplate(template)
    suspend fun deleteTemplate(id: Long) = db.more().deleteTemplate(id)
    suspend fun saveCardPerk(perk: CardPerkEntity) = db.more().upsertPerk(perk)
    suspend fun addChallenge(challenge: ChallengeEntity): Long = db.more().insertChallenge(challenge)
    suspend fun deleteChallenge(id: Long) = db.more().deleteChallenge(id)

    suspend fun importTransactions(transactions: List<TransactionEntity>) = db.transactions().insertAll(transactions)

    suspend fun addEmi(emi: EmiEntity): Long = db.emis().insert(emi)
    suspend fun updateEmi(emi: EmiEntity) = db.emis().update(emi)

    /** Records one instalment as an expense and moves the EMI on a month. The last one finishes the plan. */
    suspend fun markEmiPaid(emi: EmiEntity, dueDate: LocalDate, fromAccountId: Long) = db.withTransaction {
        val paid = emi.paidMonths + 1
        db.emis().update(emi.copy(paidMonths = paid, paidThroughEpochDay = dueDate.toEpochDay(), archived = paid >= emi.totalMonths))
        addTransaction(TransactionEntity(type = TxType.EXPENSE, amount = emi.monthlyAmount, accountId = fromAccountId, merchant = emi.name, note = "EMI ${paid} of ${emi.totalMonths}", occurredAt = System.currentTimeMillis()))
    }

    suspend fun addWish(wish: WishEntity) = db.wishlist().insert(wish)
    suspend fun restoreWish(wish: WishEntity) = db.wishlist().update(wish)
    suspend fun keepSubscription(id: Long) = settingsRepository.keepSubscription(id)
    suspend fun resolveWish(wish: WishEntity, status: WishStatus) =
        db.wishlist().update(wish.copy(status = status, resolvedAt = if (status == WishStatus.WAITING) null else System.currentTimeMillis()))
    suspend fun postponeWish(wish: WishEntity, days: Int) =
        db.wishlist().update(wish.copy(decideAt = System.currentTimeMillis() + days * 86_400_000L))

    companion object {
        fun billIcon(name: String): String = when (name.lowercase()) {
            "rent" -> "home"
            "recharge", "jio recharge" -> "phone"
            "electricity" -> "bolt"
            "gym" -> "gym"
            "spotify" -> "music"
            "card bill" -> "card"
            "wi-fi", "wifi" -> "wifi"
            "ott", "netflix" -> "tv"
            "emi" -> "card"
            else -> "calendar"
        }
    }
}
