package app.leftover.domain

import app.leftover.data.db.AccountEntity
import app.leftover.data.db.AccountType
import app.leftover.data.db.BillEntity
import app.leftover.data.db.CategoryEntity
import app.leftover.data.db.EmiEntity
import app.leftover.data.db.GoalEntity
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun Long.toLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

private val dayMonth = DateTimeFormatter.ofPattern("d MMM")

/** Pure calculations over the stored records. Every screen derives its numbers from here. */
class Ledger(
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val goals: List<GoalEntity>,
    val bills: List<BillEntity>,
    val cycle: PayCycle,
    val today: LocalDate,
    val shared: SharedBook = SharedBook.Empty,
    val emis: List<EmiEntity> = emptyList(),
) {
    private val categoryById = categories.associateBy { it.id }

    fun category(id: Long?): CategoryEntity? = id?.let { categoryById[it] }

    /** Signed effect of one transaction on one account. Card balances go negative as money is owed. */
    fun effect(tx: TransactionEntity, accountId: Long): Long {
        var change = 0L
        if (tx.accountId == accountId) {
            change += when (tx.type) {
                TxType.EXPENSE, TxType.TRANSFER, TxType.SETTLE_OUT, TxType.GOAL_SAVE -> -tx.amount
                TxType.INCOME, TxType.REFUND, TxType.REPAYMENT, TxType.GOAL_WITHDRAW -> tx.amount
            }
        }
        if (tx.toAccountId == accountId) {
            change += when (tx.type) {
                TxType.TRANSFER, TxType.GOAL_SAVE -> tx.amount
                TxType.GOAL_WITHDRAW -> -tx.amount
                else -> 0
            }
        }
        return change
    }

    val balances: Map<Long, Long> by lazy {
        accounts.associate { account -> account.id to account.openingBalance + transactions.sumOf { effect(it, account.id) } }
    }

    fun balance(accountId: Long): Long = balances[accountId] ?: 0

    /** What the user has spent from a category in the given cycle: their own share, minus refunds. */
    fun spent(categoryId: Long, inCycle: PayCycle = cycle): Long = transactions
        .filter { it.categoryId == categoryId && inCycle.contains(it.occurredAt.toLocalDate()) && !it.excludeFromBudgets }
        .sumOf {
            when (it.type) {
                TxType.EXPENSE -> it.amount - it.splitOthers
                TxType.REFUND -> -it.amount
                else -> 0
            }
        } + shared.othersPaidShareIn(categoryId, inCycle)

    /** Total spending in a cycle, optionally only up to a given day of the cycle (for fair comparisons). */
    fun totalSpent(inCycle: PayCycle = cycle, upToDayOfCycle: Int? = null): Long = transactions
        .filter {
            val date = it.occurredAt.toLocalDate()
            inCycle.contains(date) && !it.excludeFromBudgets && (upToDayOfCycle == null || inCycle.dayOfCycle(date) <= upToDayOfCycle)
        }
        .sumOf {
            when (it.type) {
                TxType.EXPENSE -> it.amount - it.splitOthers
                TxType.REFUND -> -it.amount
                else -> 0
            }
        } + shared.othersPaidShare(inCycle, upToDayOfCycle)

    fun income(inCycle: PayCycle = cycle): Long = transactions
        .filter { it.type == TxType.INCOME && inCycle.contains(it.occurredAt.toLocalDate()) }
        .sumOf { it.amount }

    /** The income plan for irregular earners, from up to the last five completed cycles. Null until there is enough history. */
    val incomePlan: IncomePlan? by lazy {
        val earlier = generateSequence(cycle.previous()) { it.previous() }.take(5).toList().reversed().map { income(it) }
        IncomeSmoothing.plan(earlier, income())
    }

    fun goalSaved(goalId: Long): Long = transactions.filter { it.goalId == goalId }.sumOf {
        when (it.type) {
            TxType.GOAL_SAVE -> it.amount
            TxType.GOAL_WITHDRAW -> -it.amount
            else -> 0
        }
    }

    fun goalSavedThisCycle(goalId: Long): Long = transactions
        .filter { it.goalId == goalId && it.type == TxType.GOAL_SAVE && cycle.contains(it.occurredAt.toLocalDate()) }
        .sumOf { it.amount }

    fun billNextDue(bill: BillEntity): LocalDate =
        DueDates.nextUnpaid(bill.dueDay, bill.paidThroughEpochDay?.let(LocalDate::ofEpochDay), today)

    fun cardOwed(card: AccountEntity): Long = (-balance(card.id)).coerceAtLeast(0)

    /** Where the card is in its billing cycle, when both its statement day and due day are known. */
    fun cardCycle(card: AccountEntity): CardCycleInfo? {
        val statementDay = card.statementDay ?: return null
        val dueDay = card.dueDay ?: return null
        val last = CardCycle.lastStatement(statementDay, today)
        val unbilled = transactions.filter { it.accountId == card.id && it.occurredAt.toLocalDate().isAfter(last) }.sumOf {
            when (it.type) {
                TxType.EXPENSE -> it.amount
                TxType.REFUND -> -it.amount
                else -> 0L
            }
        }
        return CardCycle.info(statementDay, dueDay, cardOwed(card), unbilled, today)
    }

    /** Whether the money lasts until payday at the pace of the last few days. */
    val runway: Runway by lazy {
        RunwayCalc.project(leftToSpend.left, transactions, bills.map { it.name }.toSet(), today, cycle.nextPayday)
    }

    val leftToSpend: LeftToSpend by lazy {
        val inAccounts = accounts
            .filter { it.includeInLeft && (it.type == AccountType.BANK || it.type == AccountType.CASH) }
            .sumOf { balance(it.id) }
        val billLines = bills.filter { !it.archived }.mapNotNull { bill ->
            val due = billNextDue(bill)
            if (!due.isAfter(cycle.nextPayday)) Deduction(bill.name, bill.amount, "Due ${due.format(dayMonth)}") else null
        }
        val emiLines = emis.filter { !it.archived && it.remainingMonths > 0 }.mapNotNull { emi ->
            val due = EmiSchedule.summary(emi, today).nextDue ?: return@mapNotNull null
            if (!due.isAfter(cycle.nextPayday)) Deduction(emi.name, emi.monthlyAmount, "EMI · due ${due.format(dayMonth)}") else null
        }
        val cardLines = accounts.filter { it.type == AccountType.CARD && it.includeInLeft }.mapNotNull { card ->
            val owed = cardOwed(card)
            val cycle = cardCycle(card)
            if (cycle != null) {
                // With a statement day, only what has been billed is due; spending since lands on the next bill.
                if (cycle.billed > 0 && !cycle.billDue.isAfter(this.cycle.nextPayday)) Deduction(card.name, cycle.billed, "Billed · due ${cycle.billDue.format(dayMonth)}") else null
            } else {
                val due = card.dueDay?.let { DueDates.nextUnpaid(it, null, today) }
                if (owed > 0 && (due == null || !due.isAfter(this.cycle.nextPayday))) {
                    Deduction(card.name, owed, due?.let { "Due ${it.format(dayMonth)}" } ?: "Owed now")
                } else null
            }
        }
        val goalLines = goals.filter { !it.archived }.mapNotNull { goal ->
            val remainingTarget = (goal.target - goalSaved(goal.id)).coerceAtLeast(0)
            val stillToReserve = (goal.perCycle - goalSavedThisCycle(goal.id)).coerceAtLeast(0).coerceAtMost(remainingTarget)
            if (stillToReserve > 0) Deduction(goal.name, stillToReserve, "This cycle") else null
        }
        LeftToSpend(
            inAccounts = inAccounts,
            bills = billLines + emiLines,
            cardDues = cardLines,
            goals = goalLines,
            comingBack = shared.owedToYou,
            daysUntilPayday = cycle.daysUntilPayday(today),
            owedByYou = shared.youOwe,
        )
    }
}
