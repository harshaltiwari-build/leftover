package app.leftover.data

import app.leftover.data.db.AccountEntity
import app.leftover.data.db.AccountType
import app.leftover.data.db.BillEntity
import app.leftover.data.db.CategoryEntity
import app.leftover.data.db.CaptureRuleEntity
import app.leftover.data.db.CardPerkEntity
import app.leftover.data.db.CategoryKind
import app.leftover.data.db.ChallengeEntity
import app.leftover.data.db.RecurringSplitEntity
import app.leftover.data.db.TagEntity
import app.leftover.data.db.TemplateEntity
import app.leftover.data.db.TransactionTagEntity
import app.leftover.data.db.EmiEntity
import app.leftover.data.db.GoalEntity
import app.leftover.data.db.GroupEntity
import app.leftover.data.db.GroupKind
import app.leftover.data.db.GroupMemberEntity
import app.leftover.data.db.PersonEntity
import app.leftover.data.db.SettleDirection
import app.leftover.data.db.SettleMethod
import app.leftover.data.db.SettlementEntity
import app.leftover.data.db.SharedExpenseEntity
import app.leftover.data.db.SplitShareEntity
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxSource
import app.leftover.data.db.TxType
import app.leftover.data.db.WishEntity
import app.leftover.data.db.WishStatus
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** The settings a backup carries. Anything tied to this phone (SMS tracking, app lock) is left out. */
data class BackupSettings(
    val name: String,
    val paydayDay: Int,
    val incomeType: String,
    val usualIncome: Long,
    val hideBalances: Boolean,
    val themeMode: ThemeMode,
    val billReminders: Boolean,
    val irregularIncome: Boolean = false,
    val voiceLanguage: String = "en-IN",
)

/** Everything the user entered. Bank-text data (senders, waiting items) is not included: it is rebuilt from new texts. */
data class BackupData(
    val settings: BackupSettings,
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val goals: List<GoalEntity>,
    val bills: List<BillEntity>,
    val wishes: List<WishEntity>,
    val people: List<PersonEntity>,
    val groups: List<GroupEntity>,
    val members: List<GroupMemberEntity>,
    val expenses: List<SharedExpenseEntity>,
    val shares: List<SplitShareEntity>,
    val settlements: List<SettlementEntity>,
    val emis: List<EmiEntity> = emptyList(),
    val rules: List<CaptureRuleEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val txTags: List<TransactionTagEntity> = emptyList(),
    val templates: List<TemplateEntity> = emptyList(),
    val perks: List<CardPerkEntity> = emptyList(),
    val challenges: List<ChallengeEntity> = emptyList(),
    val recurring: List<RecurringSplitEntity> = emptyList(),
)

class BackupException(message: String) : Exception(message)

/**
 * Turns a [BackupData] into a JSON file and back. The file is plain text, so anyone who has it can read it.
 * Reading is strict: a file that isn't a Leftover backup, or is from a newer version, is refused before anything is changed.
 */
object BackupCodec {
    const val FORMAT = "leftover-backup"
    const val VERSION = 1

    fun encode(data: BackupData, exportedAt: Long): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("exportedAt", exportedAt)
        put("settings", JSONObject().apply {
            put("name", data.settings.name)
            put("paydayDay", data.settings.paydayDay)
            put("incomeType", data.settings.incomeType)
            put("usualIncome", data.settings.usualIncome)
            put("hideBalances", data.settings.hideBalances)
            put("themeMode", data.settings.themeMode.name)
            put("billReminders", data.settings.billReminders)
            put("irregularIncome", data.settings.irregularIncome)
            put("voiceLanguage", data.settings.voiceLanguage)
        })
        put("accounts", array(data.accounts) { a ->
            JSONObject().put("id", a.id).put("name", a.name).put("type", a.type.name).put("openingBalance", a.openingBalance)
                .put("includeInLeft", a.includeInLeft).putOpt("dueDay", a.dueDay).put("archived", a.archived).put("createdAt", a.createdAt).putOpt("statementDay", a.statementDay)
        })
        put("categories", array(data.categories) { c ->
            JSONObject().put("id", c.id).put("name", c.name).put("icon", c.icon).put("color", c.color).put("kind", c.kind.name)
                .putOpt("budgetLimit", c.budgetLimit).put("rollover", c.rollover).put("hidden", c.hidden).put("sortOrder", c.sortOrder)
        })
        put("transactions", array(data.transactions) { t ->
            JSONObject().put("id", t.id).put("type", t.type.name).put("amount", t.amount).put("accountId", t.accountId)
                .putOpt("toAccountId", t.toAccountId).putOpt("categoryId", t.categoryId).putOpt("goalId", t.goalId)
                .put("merchant", t.merchant).put("note", t.note).put("occurredAt", t.occurredAt).put("splitOthers", t.splitOthers)
                .put("excludeFromBudgets", t.excludeFromBudgets).put("source", t.source.name).put("createdAt", t.createdAt)
        })
        put("goals", array(data.goals) { g ->
            JSONObject().put("id", g.id).put("name", g.name).put("target", g.target).put("perCycle", g.perCycle).put("cover", g.cover)
                .putOpt("targetEpochDay", g.targetEpochDay).put("autoSave", g.autoSave).put("archived", g.archived).put("createdAt", g.createdAt)
        })
        put("bills", array(data.bills) { b ->
            JSONObject().put("id", b.id).put("name", b.name).put("amount", b.amount).put("dueDay", b.dueDay).put("icon", b.icon)
                .putOpt("categoryId", b.categoryId).putOpt("accountId", b.accountId).put("remindDaysBefore", b.remindDaysBefore)
                .put("isSubscription", b.isSubscription).put("shared", b.shared).putOpt("paidThroughEpochDay", b.paidThroughEpochDay).put("archived", b.archived)
        })
        put("wishes", array(data.wishes) { w ->
            JSONObject().put("id", w.id).put("name", w.name).put("price", w.price).putOpt("categoryId", w.categoryId).put("addedAt", w.addedAt)
                .put("decideAt", w.decideAt).put("status", w.status.name).putOpt("resolvedAt", w.resolvedAt)
        })
        put("people", array(data.people) { p ->
            JSONObject().put("id", p.id).put("name", p.name).putOpt("upiId", p.upiId).put("color", p.color).put("archived", p.archived)
        })
        put("groups", array(data.groups) { g ->
            JSONObject().put("id", g.id).put("name", g.name).put("kind", g.kind.name).putOpt("budget", g.budget)
                .putOpt("startEpochDay", g.startEpochDay).putOpt("endEpochDay", g.endEpochDay).put("archived", g.archived)
        })
        put("members", array(data.members) { m -> JSONObject().put("groupId", m.groupId).put("personId", m.personId) })
        put("expenses", array(data.expenses) { e ->
            JSONObject().put("id", e.id).put("title", e.title).put("total", e.total).put("paidBy", e.paidBy).putOpt("groupId", e.groupId)
                .putOpt("categoryId", e.categoryId).put("occurredAt", e.occurredAt).putOpt("transactionId", e.transactionId).put("note", e.note)
        })
        put("shares", array(data.shares) { s ->
            JSONObject().put("id", s.id).put("expenseId", s.expenseId).put("personId", s.personId).put("amount", s.amount)
        })
        put("emis", array(data.emis) { e ->
            JSONObject().put("id", e.id).put("name", e.name).put("monthlyAmount", e.monthlyAmount).put("totalMonths", e.totalMonths)
                .put("paidMonths", e.paidMonths).put("dueDay", e.dueDay).putOpt("annualRateBps", e.annualRateBps).putOpt("accountId", e.accountId)
                .putOpt("paidThroughEpochDay", e.paidThroughEpochDay).put("archived", e.archived).put("createdAt", e.createdAt)
        })
        put("rules", array(data.rules) { r -> JSONObject().put("id", r.id).put("pattern", r.pattern).put("categoryId", r.categoryId) })
        put("tags", array(data.tags) { t -> JSONObject().put("id", t.id).put("name", t.name).put("color", t.color).putOpt("budget", t.budget) })
        put("txTags", array(data.txTags) { t -> JSONObject().put("transactionId", t.transactionId).put("tagId", t.tagId) })
        put("templates", array(data.templates) { t ->
            JSONObject().put("id", t.id).put("name", t.name).put("amount", t.amount).putOpt("categoryId", t.categoryId).put("merchant", t.merchant).putOpt("accountId", t.accountId)
        })
        put("perks", array(data.perks) { p ->
            JSONObject().put("accountId", p.accountId).putOpt("annualFee", p.annualFee).putOpt("waiverSpend", p.waiverSpend).putOpt("yearStartEpochDay", p.yearStartEpochDay)
                .put("points", p.points).putOpt("pointsExpiryEpochDay", p.pointsExpiryEpochDay).putOpt("creditLimit", p.creditLimit)
        })
        put("challenges", array(data.challenges) { c ->
            JSONObject().put("id", c.id).put("name", c.name).putOpt("categoryId", c.categoryId).put("startEpochDay", c.startEpochDay).put("endEpochDay", c.endEpochDay)
        })
        put("recurring", array(data.recurring) { r ->
            JSONObject().put("id", r.id).put("title", r.title).put("total", r.total).put("paidBy", r.paidBy).put("participants", r.participants).put("includesMe", r.includesMe)
                .putOpt("groupId", r.groupId).putOpt("categoryId", r.categoryId).putOpt("accountId", r.accountId).put("dueDay", r.dueDay)
                .putOpt("lastCreatedEpochDay", r.lastCreatedEpochDay).put("archived", r.archived)
        })
        put("settlements", array(data.settlements) { s ->
            JSONObject().put("id", s.id).put("personId", s.personId).put("amount", s.amount).put("direction", s.direction.name)
                .put("method", s.method.name).put("occurredAt", s.occurredAt).putOpt("groupId", s.groupId)
                .putOpt("transactionId", s.transactionId).put("note", s.note)
        })
    }.toString()

    fun decode(text: String): BackupData {
        val root = try {
            JSONObject(text)
        } catch (_: JSONException) {
            throw BackupException("That file isn't a Leftover backup.")
        }
        if (root.optString("format") != FORMAT) throw BackupException("That file isn't a Leftover backup.")
        if (root.optInt("version", 0) > VERSION) throw BackupException("This backup is from a newer version of Leftover. Update the app and try again.")
        try {
            val s = root.getJSONObject("settings")
            val data = BackupData(
                settings = BackupSettings(
                    name = s.optString("name"),
                    paydayDay = s.optInt("paydayDay", 1).coerceIn(1, 31),
                    incomeType = s.optString("incomeType", "Salary"),
                    usualIncome = s.optLong("usualIncome", 0),
                    hideBalances = s.optBoolean("hideBalances", false),
                    themeMode = enumOf(s.optString("themeMode"), ThemeMode.SYSTEM),
                    billReminders = s.optBoolean("billReminders", true),
                    irregularIncome = s.optBoolean("irregularIncome", false),
                    voiceLanguage = s.optString("voiceLanguage", "en-IN").ifBlank { "en-IN" },
                ),
                accounts = list(root, "accounts") { o ->
                    AccountEntity(o.getLong("id"), o.getString("name"), enumOf(o.getString("type"), AccountType.BANK), o.getLong("openingBalance"),
                        o.getBoolean("includeInLeft"), o.intOrNull("dueDay"), o.optBoolean("archived"), o.optLong("createdAt"), o.intOrNull("statementDay"))
                },
                categories = list(root, "categories") { o ->
                    CategoryEntity(o.getLong("id"), o.getString("name"), o.getString("icon"), o.getString("color"), enumOf(o.getString("kind"), CategoryKind.EXPENSE),
                        o.longOrNull("budgetLimit"), o.optBoolean("rollover"), o.optBoolean("hidden"), o.optInt("sortOrder"))
                },
                transactions = list(root, "transactions") { o ->
                    TransactionEntity(o.getLong("id"), enumOf(o.getString("type"), TxType.EXPENSE), o.getLong("amount"), o.getLong("accountId"),
                        o.longOrNull("toAccountId"), o.longOrNull("categoryId"), o.longOrNull("goalId"), o.optString("merchant"), o.optString("note"),
                        o.getLong("occurredAt"), o.optLong("splitOthers"), o.optBoolean("excludeFromBudgets"), enumOf(o.optString("source"), TxSource.MANUAL), o.optLong("createdAt"))
                },
                goals = list(root, "goals") { o ->
                    GoalEntity(o.getLong("id"), o.getString("name"), o.getLong("target"), o.getLong("perCycle"), o.getString("cover"),
                        o.longOrNull("targetEpochDay"), o.optBoolean("autoSave", true), o.optBoolean("archived"), o.optLong("createdAt"))
                },
                bills = list(root, "bills") { o ->
                    BillEntity(o.getLong("id"), o.getString("name"), o.getLong("amount"), o.getInt("dueDay"), o.getString("icon"), o.longOrNull("categoryId"),
                        o.longOrNull("accountId"), o.optInt("remindDaysBefore", 1), o.optBoolean("isSubscription"), o.optBoolean("shared"),
                        o.longOrNull("paidThroughEpochDay"), o.optBoolean("archived"))
                },
                wishes = list(root, "wishes") { o ->
                    WishEntity(o.getLong("id"), o.getString("name"), o.getLong("price"), o.longOrNull("categoryId"), o.optLong("addedAt"), o.getLong("decideAt"),
                        enumOf(o.optString("status"), WishStatus.WAITING), o.longOrNull("resolvedAt"))
                },
                people = list(root, "people") { o ->
                    PersonEntity(o.getLong("id"), o.getString("name"), o.stringOrNull("upiId"), o.optString("color", "sky"), o.optBoolean("archived"))
                },
                groups = list(root, "groups") { o ->
                    GroupEntity(o.getLong("id"), o.getString("name"), enumOf(o.getString("kind"), GroupKind.FRIENDS), o.longOrNull("budget"),
                        o.longOrNull("startEpochDay"), o.longOrNull("endEpochDay"), o.optBoolean("archived"))
                },
                members = list(root, "members") { o -> GroupMemberEntity(o.getLong("groupId"), o.getLong("personId")) },
                expenses = list(root, "expenses") { o ->
                    SharedExpenseEntity(o.getLong("id"), o.getString("title"), o.getLong("total"), o.getLong("paidBy"), o.longOrNull("groupId"),
                        o.longOrNull("categoryId"), o.getLong("occurredAt"), o.longOrNull("transactionId"), o.optString("note"))
                },
                shares = list(root, "shares") { o -> SplitShareEntity(o.getLong("id"), o.getLong("expenseId"), o.getLong("personId"), o.getLong("amount")) },
                emis = list(root, "emis") { o ->
                    EmiEntity(o.getLong("id"), o.getString("name"), o.getLong("monthlyAmount"), o.getInt("totalMonths"), o.optInt("paidMonths"), o.getInt("dueDay"),
                        o.intOrNull("annualRateBps"), o.longOrNull("accountId"), o.longOrNull("paidThroughEpochDay"), o.optBoolean("archived"), o.optLong("createdAt"))
                },
                rules = list(root, "rules") { o -> CaptureRuleEntity(o.getLong("id"), o.getString("pattern"), o.getLong("categoryId")) },
                tags = list(root, "tags") { o -> TagEntity(o.getLong("id"), o.getString("name"), o.optString("color", "lavender"), o.longOrNull("budget")) },
                txTags = list(root, "txTags") { o -> TransactionTagEntity(o.getLong("transactionId"), o.getLong("tagId")) },
                templates = list(root, "templates") { o ->
                    TemplateEntity(o.getLong("id"), o.getString("name"), o.getLong("amount"), o.longOrNull("categoryId"), o.optString("merchant"), o.longOrNull("accountId"))
                },
                perks = list(root, "perks") { o ->
                    CardPerkEntity(o.getLong("accountId"), o.longOrNull("annualFee"), o.longOrNull("waiverSpend"), o.longOrNull("yearStartEpochDay"), o.optLong("points"), o.longOrNull("pointsExpiryEpochDay"), o.longOrNull("creditLimit"))
                },
                challenges = list(root, "challenges") { o ->
                    ChallengeEntity(o.getLong("id"), o.getString("name"), o.longOrNull("categoryId"), o.getLong("startEpochDay"), o.getLong("endEpochDay"))
                },
                recurring = list(root, "recurring") { o ->
                    RecurringSplitEntity(
                        o.getLong("id"), o.getString("title"), o.getLong("total"), o.getLong("paidBy"), o.optString("participants"), o.optBoolean("includesMe", true),
                        o.longOrNull("groupId"), o.longOrNull("categoryId"), o.longOrNull("accountId"), o.getInt("dueDay"), o.longOrNull("lastCreatedEpochDay"), o.optBoolean("archived"),
                    )
                },
                settlements = list(root, "settlements") { o ->
                    SettlementEntity(o.getLong("id"), o.getLong("personId"), o.getLong("amount"), enumOf(o.getString("direction"), SettleDirection.YOU_PAID),
                        enumOf(o.getString("method"), SettleMethod.CASH), o.getLong("occurredAt"), o.longOrNull("groupId"), o.longOrNull("transactionId"), o.optString("note"))
                },
            )
            if (data.accounts.isEmpty()) throw BackupException("This backup has no accounts, so there is nothing to restore.")
            return data
        } catch (e: JSONException) {
            throw BackupException("This backup is damaged and can't be read.")
        }
    }

    private fun <T> array(items: List<T>, encode: (T) -> JSONObject): JSONArray = JSONArray().also { out -> items.forEach { out.put(encode(it)) } }

    private fun <T> list(root: JSONObject, key: String, decode: (JSONObject) -> T): List<T> {
        val array = root.optJSONArray(key) ?: return emptyList()
        return List(array.length()) { decode(array.getJSONObject(it)) }
    }

    private inline fun <reified E : Enum<E>> enumOf(name: String, fallback: E): E = enumValues<E>().firstOrNull { it.name == name } ?: fallback

    private fun JSONObject.longOrNull(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null
    private fun JSONObject.intOrNull(key: String): Int? = if (has(key) && !isNull(key)) getInt(key) else null
    private fun JSONObject.stringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
}
