package app.leftover.domain

import app.leftover.data.db.GroupEntity
import app.leftover.data.db.GroupMemberEntity
import app.leftover.data.db.PersonEntity
import app.leftover.data.db.SettleDirection
import app.leftover.data.db.SettlementEntity
import app.leftover.data.db.SharedExpenseEntity
import app.leftover.data.db.SplitShareEntity
import kotlin.math.min

/** One payment someone needs to make to square up a group. */
data class Transfer(val from: Long, val to: Long, val amount: Paise)

data class GroupSummary(
    val group: GroupEntity,
    val total: Paise,
    val yourShare: Paise,
    val byCategory: List<Pair<Long?, Paise>>,
    /** Net position per member: positive means they are owed money, negative means they owe it. */
    val nets: Map<Long, Paise>,
    val plan: List<Transfer>,
)

/**
 * All shared money, as seen from your side. Balances are always between you and one person:
 * positive means they owe you, negative means you owe them.
 */
class SharedBook(
    val people: List<PersonEntity>,
    val groups: List<GroupEntity>,
    val members: List<GroupMemberEntity>,
    val expenses: List<SharedExpenseEntity>,
    val shares: List<SplitShareEntity>,
    val settlements: List<SettlementEntity>,
) {
    private val sharesByExpense = shares.groupBy { it.expenseId }
    private val peopleById = people.associateBy { it.id }

    fun person(id: Long): PersonEntity? = peopleById[id]

    fun shareOf(expenseId: Long, personId: Long): Paise =
        sharesByExpense[expenseId].orEmpty().filter { it.personId == personId }.sumOf { it.amount }

    fun sharesOf(expenseId: Long): List<SplitShareEntity> = sharesByExpense[expenseId].orEmpty()

    /** Net balance with one person across every expense and settlement. */
    fun balanceWith(personId: Long): Paise {
        var balance = 0L
        for (expense in expenses) {
            if (expense.paidBy == ME) balance += shareOf(expense.id, personId)
            else if (expense.paidBy == personId) balance -= shareOf(expense.id, ME)
        }
        // Settling pays the balance down toward zero: if they paid you, what they owe you shrinks.
        for (s in settlements.filter { it.personId == personId }) {
            balance += if (s.direction == SettleDirection.THEY_PAID) -s.amount else s.amount
        }
        return balance
    }

    val balances: Map<Long, Paise> by lazy { people.associate { it.id to balanceWith(it.id) } }

    val owedToYou: Paise by lazy { balances.values.filter { it > 0 }.sum() }

    val youOwe: Paise by lazy { balances.values.filter { it < 0 }.sumOf { -it } }

    /** Your share of bills somebody else paid. It is spending even though your account never moved. */
    fun othersPaidShare(inCycle: PayCycle? = null, upToDayOfCycle: Int? = null): Paise = expenses
        .filter {
            val date = it.occurredAt.toLocalDate()
            it.paidBy != ME && (inCycle == null || inCycle.contains(date)) &&
                (upToDayOfCycle == null || inCycle == null || inCycle.dayOfCycle(date) <= upToDayOfCycle)
        }
        .sumOf { shareOf(it.id, ME) }

    /** Same as [othersPaidShare], limited to one category. */
    fun othersPaidShareIn(categoryId: Long, inCycle: PayCycle? = null): Paise = expenses
        .filter { it.paidBy != ME && it.categoryId == categoryId && (inCycle == null || inCycle.contains(it.occurredAt.toLocalDate())) }
        .sumOf { shareOf(it.id, ME) }

    fun groupMembers(groupId: Long): List<PersonEntity> =
        members.filter { it.groupId == groupId }.mapNotNull { peopleById[it.personId] }

    fun expensesOf(groupId: Long): List<SharedExpenseEntity> = expenses.filter { it.groupId == groupId }.sortedByDescending { it.occurredAt }

    fun expensesWith(personId: Long): List<SharedExpenseEntity> = expenses
        .filter { (it.paidBy == ME && shareOf(it.id, personId) > 0) || (it.paidBy == personId && shareOf(it.id, ME) > 0) }
        .sortedByDescending { it.occurredAt }

    fun summary(groupId: Long): GroupSummary? {
        val group = groups.firstOrNull { it.id == groupId } ?: return null
        val groupExpenses = expensesOf(groupId)
        val nets = HashMap<Long, Paise>()
        for (expense in groupExpenses) {
            nets[expense.paidBy] = (nets[expense.paidBy] ?: 0L) + expense.total
            for (share in sharesOf(expense.id)) nets[share.personId] = (nets[share.personId] ?: 0L) - share.amount
        }
        for (s in settlements.filter { it.groupId == groupId }) {
            val delta = if (s.direction == SettleDirection.YOU_PAID) s.amount else -s.amount
            nets[ME] = (nets[ME] ?: 0L) + delta
            nets[s.personId] = (nets[s.personId] ?: 0L) - delta
        }
        for (member in groupMembers(groupId)) nets.putIfAbsent(member.id, 0L)
        nets.putIfAbsent(ME, 0L)
        val byCategory = groupExpenses.groupBy { it.categoryId }.map { (category, list) -> category to list.sumOf { it.total } }.sortedByDescending { it.second }
        return GroupSummary(
            group = group,
            total = groupExpenses.sumOf { it.total },
            yourShare = groupExpenses.sumOf { shareOf(it.id, ME) },
            byCategory = byCategory,
            nets = nets,
            plan = GroupSettlement.simplify(nets),
        )
    }

    companion object {
        val Empty = SharedBook(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}

/** Works out the fewest payments that square a group, instead of everyone paying everyone. */
object GroupSettlement {
    fun simplify(nets: Map<Long, Paise>): List<Transfer> {
        val creditors = nets.filter { it.value > 0 }.map { it.key to it.value }.sortedByDescending { it.second }.toMutableList()
        val debtors = nets.filter { it.value < 0 }.map { it.key to -it.value }.sortedByDescending { it.second }.toMutableList()
        val plan = mutableListOf<Transfer>()
        while (creditors.isNotEmpty() && debtors.isNotEmpty()) {
            val (creditor, owed) = creditors.first()
            val (debtor, owes) = debtors.first()
            val amount = min(owed, owes)
            plan += Transfer(from = debtor, to = creditor, amount = amount)
            if (owed - amount > 0) creditors[0] = creditor to (owed - amount) else creditors.removeAt(0)
            if (owes - amount > 0) debtors[0] = debtor to (owes - amount) else debtors.removeAt(0)
            creditors.sortByDescending { it.second }
            debtors.sortByDescending { it.second }
        }
        return plan
    }
}
