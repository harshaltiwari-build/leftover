package app.leftover.domain

import app.leftover.data.db.AccountEntity
import app.leftover.data.db.AccountType
import app.leftover.data.db.BillEntity
import app.leftover.data.db.CategoryEntity
import app.leftover.data.db.CategoryKind
import app.leftover.data.db.GoalEntity
import app.leftover.data.db.PersonEntity
import app.leftover.data.db.SharedExpenseEntity
import app.leftover.data.db.SplitShareEntity
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LedgerTest {
    private val today = LocalDate.of(2026, 10, 25)
    private val cycle = PayCycle.forPayday(28, today)
    private fun millis(date: LocalDate) = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val bank = AccountEntity(id = 1, name = "HDFC", type = AccountType.BANK, openingBalance = rupees(10000), includeInLeft = true)
    private val savings = AccountEntity(id = 2, name = "Goal savings", type = AccountType.SAVINGS, openingBalance = 0, includeInLeft = false)
    private val card = AccountEntity(id = 3, name = "ICICI card", type = AccountType.CARD, openingBalance = 0, includeInLeft = true, dueDay = 28)
    private val food = CategoryEntity(id = 1, name = "Eating out", icon = "food", color = "peach", kind = CategoryKind.EXPENSE)

    private fun tx(type: TxType, amount: Long, account: Long = 1, to: Long? = null, category: Long? = 1, split: Long = 0, date: LocalDate = today, goal: Long? = null) =
        TransactionEntity(type = type, amount = rupees(amount), accountId = account, toAccountId = to, categoryId = category, splitOthers = rupees(split), occurredAt = millis(date), goalId = goal)

    private fun ledger(vararg txs: TransactionEntity, goals: List<GoalEntity> = emptyList(), bills: List<BillEntity> = emptyList()) =
        Ledger(listOf(bank, savings, card), listOf(food), txs.toList(), goals, bills, cycle, today)

    @Test fun expensesReduceTheAccountAndIncomeRaisesIt() {
        val l = ledger(tx(TxType.EXPENSE, 500), tx(TxType.INCOME, 2000, category = null))
        assertEquals(rupees(11500), l.balance(1))
    }

    @Test fun transfersMoveMoneyWithoutCountingAsSpending() {
        val l = ledger(tx(TxType.TRANSFER, 2000, account = 1, to = 2, category = null))
        assertEquals(rupees(8000), l.balance(1))
        assertEquals(rupees(2000), l.balance(2))
        assertEquals(0L, l.totalSpent())
    }

    @Test fun cardPurchasesAreOwedAndRepaymentsDoNotDoubleCount() {
        val purchase = tx(TxType.EXPENSE, 2500, account = 3)
        val repay = tx(TxType.TRANSFER, 2500, account = 1, to = 3, category = null)
        assertEquals(rupees(2500), ledger(purchase).cardOwed(card))
        val l = ledger(purchase, repay)
        assertEquals(0L, l.cardOwed(card))
        assertEquals(rupees(2500), l.totalSpent())
    }

    @Test fun splitExpensesOnlyCountTheUsersOwnShare() {
        val l = ledger(tx(TxType.EXPENSE, 2400, split = 1800))
        assertEquals(rupees(600), l.spent(1))
        assertEquals(rupees(7600), l.balance(1))
    }

    @Test fun refundsReduceSpending() {
        val l = ledger(tx(TxType.EXPENSE, 899), tx(TxType.REFUND, 899))
        assertEquals(0L, l.spent(1))
    }

    @Test fun spendingOutsideTheCycleIsIgnored() {
        val l = ledger(tx(TxType.EXPENSE, 700, date = LocalDate.of(2026, 9, 20)), tx(TxType.EXPENSE, 300))
        assertEquals(rupees(300), l.spent(1))
    }

    @Test fun goalSavingsLeaveSpendableMoney() {
        val goal = GoalEntity(id = 1, name = "Phone", target = rupees(60000), perCycle = rupees(2000), cover = "lavender")
        val saved = tx(TxType.GOAL_SAVE, 2000, account = 1, to = 2, category = null, goal = 1)
        val without = ledger(goals = listOf(goal)).leftToSpend
        val with = ledger(saved, goals = listOf(goal)).leftToSpend
        assertEquals(rupees(8000), without.left)
        assertEquals(rupees(8000), with.left)
        assertEquals(rupees(2000), ledger(saved).goalSaved(1))
    }

    @Test fun billsBeforePaydayReduceLeftToSpend() {
        val gym = BillEntity(name = "Gym", amount = rupees(2000), dueDay = 27, icon = "gym")
        val rent = BillEntity(name = "Rent", amount = rupees(9000), dueDay = 1, icon = "home")
        assertEquals(rupees(8000), ledger(bills = listOf(gym, rent)).leftToSpend.left)
    }

    @Test fun cardDueBeforePaydayIsReserved() {
        val l = ledger(tx(TxType.EXPENSE, 2500, account = 3))
        assertEquals(rupees(10000 - 2500), l.leftToSpend.left)
    }

    @Test fun filtersAndSearchesActivity() {
        val coffee = tx(TxType.EXPENSE, 180).copy(merchant = "Blue Tokai")
        val salary = tx(TxType.INCOME, 42000, category = null)
        val l = ledger(coffee, salary)
        assertEquals(listOf(coffee), ActivityFilter.apply(l.transactions, ActivityGroup.EXPENSES, "", l))
        assertEquals(listOf(coffee), ActivityFilter.apply(l.transactions, ActivityGroup.ALL, "tokai", l))
        assertEquals(listOf(salary), ActivityFilter.apply(l.transactions, ActivityGroup.ALL, "42,000", l))
        assertEquals(2, ActivityFilter.apply(l.transactions, ActivityGroup.ALL, "", l).size)
    }

    // ---- shared money ----

    private val rohan = PersonEntity(id = 1, name = "Rohan")

    private fun sharedLedger(book: SharedBook, vararg txs: TransactionEntity) =
        Ledger(listOf(bank, savings, card), listOf(food), txs.toList(), emptyList(), emptyList(), cycle, today, book)

    @Test fun moneyOwedByFriendsIsComingBackButNeverCountedInLeftToSpend() {
        val bill = tx(TxType.EXPENSE, 2400, split = 1200)
        val expense = SharedExpenseEntity(id = 1, title = "Dinner", total = rupees(2400), paidBy = ME, categoryId = 1, occurredAt = millis(today), transactionId = 1)
        val book = SharedBook(listOf(rohan), emptyList(), emptyList(), listOf(expense), listOf(SplitShareEntity(expenseId = 1, personId = 1, amount = rupees(1200)), SplitShareEntity(expenseId = 1, personId = ME, amount = rupees(1200))), emptyList())
        val l = sharedLedger(book, bill)
        assertEquals(rupees(1200), l.leftToSpend.comingBack)
        assertEquals(rupees(7600), l.leftToSpend.left)
        assertEquals(rupees(1200), l.totalSpent())
    }

    @Test fun aBillAFriendPaidCountsYourShareAsSpendingWithoutMovingAnyAccount() {
        val expense = SharedExpenseEntity(id = 1, title = "Cab", total = rupees(800), paidBy = 1, categoryId = 1, occurredAt = millis(today))
        val book = SharedBook(listOf(rohan), emptyList(), emptyList(), listOf(expense), listOf(SplitShareEntity(expenseId = 1, personId = ME, amount = rupees(400)), SplitShareEntity(expenseId = 1, personId = 1, amount = rupees(400))), emptyList())
        val l = sharedLedger(book)
        assertEquals(rupees(10000), l.balance(1))
        assertEquals(rupees(400), l.spent(1))
        assertEquals(rupees(400), l.totalSpent())
        assertEquals(rupees(400), l.leftToSpend.owedByYou)
    }

    @Test fun payingAFriendBackMovesMoneyButIsNotSpending() {
        val l = ledger(tx(TxType.SETTLE_OUT, 400, category = null))
        assertEquals(rupees(9600), l.balance(1))
        assertEquals(0L, l.totalSpent())
    }

    @Test fun aFriendPayingYouBackIsNotIncome() {
        val l = ledger(tx(TxType.REPAYMENT, 600, category = null))
        assertEquals(rupees(10600), l.balance(1))
        assertEquals(0L, l.income())
    }
}
