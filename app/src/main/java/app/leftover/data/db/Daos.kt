package app.leftover.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE archived = 0 ORDER BY type, createdAt")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE type = :type AND archived = 0 LIMIT 1")
    suspend fun firstOfType(type: AccountType): AccountEntity?

    @Insert suspend fun insert(account: AccountEntity): Long
    @Query("SELECT * FROM accounts") suspend fun all(): List<AccountEntity>
    @Insert suspend fun insertAll(accounts: List<AccountEntity>)
    @Update suspend fun update(account: AccountEntity)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT id FROM categories WHERE name = :name LIMIT 1")
    suspend fun idByName(name: String): Long?

    @Insert suspend fun insertAll(categories: List<CategoryEntity>)
    @Query("SELECT * FROM categories") suspend fun all(): List<CategoryEntity>
    @Insert suspend fun insert(category: CategoryEntity): Long
    @Update suspend fun update(category: CategoryEntity)
    @Update suspend fun updateAll(categories: List<CategoryEntity>)

    // Merging one category into another: everything that pointed at the old one now points at the new one.
    @Query("UPDATE transactions SET categoryId = :to WHERE categoryId = :from") suspend fun moveTransactions(from: Long, to: Long)
    @Query("UPDATE bills SET categoryId = :to WHERE categoryId = :from") suspend fun moveBills(from: Long, to: Long)
    @Query("UPDATE wishlist SET categoryId = :to WHERE categoryId = :from") suspend fun moveWishes(from: Long, to: Long)
    @Query("UPDATE shared_expenses SET categoryId = :to WHERE categoryId = :from") suspend fun moveSharedExpenses(from: Long, to: Long)
    @Query("UPDATE templates SET categoryId = :to WHERE categoryId = :from") suspend fun moveTemplates(from: Long, to: Long)
    @Query("UPDATE challenges SET categoryId = :to WHERE categoryId = :from") suspend fun moveChallenges(from: Long, to: Long)
    @Query("UPDATE recurring_splits SET categoryId = :to WHERE categoryId = :from") suspend fun moveRecurring(from: Long, to: Long)
    @Query("UPDATE capture_rules SET categoryId = :to WHERE categoryId = :from") suspend fun moveRules(from: Long, to: Long)
    @Query("UPDATE pending_sms SET suggestedCategoryId = :to WHERE suggestedCategoryId = :from") suspend fun movePending(from: Long, to: Long)
    @Query("DELETE FROM categories WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observe(id: Long): Flow<TransactionEntity?>

    @Insert suspend fun insert(tx: TransactionEntity): Long
    @Query("SELECT * FROM transactions") suspend fun all(): List<TransactionEntity>
    @Insert suspend fun insertAll(transactions: List<TransactionEntity>)
    @Update suspend fun update(tx: TransactionEntity)
    @Delete suspend fun delete(tx: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM transactions WHERE source IN ('SMS', 'NOTIFICATION')")
    suspend fun countFromSms(): Int

    @Query("DELETE FROM transactions WHERE source IN ('SMS', 'NOTIFICATION')")
    suspend fun deleteFromSms()
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE archived = 0 ORDER BY createdAt")
    fun observeAll(): Flow<List<GoalEntity>>

    @Insert suspend fun insert(goal: GoalEntity): Long
    @Query("SELECT * FROM goals") suspend fun all(): List<GoalEntity>
    @Insert suspend fun insertAll(goals: List<GoalEntity>)
    @Update suspend fun update(goal: GoalEntity)
}

@Dao
interface BillDao {
    @Query("SELECT * FROM bills WHERE archived = 0 ORDER BY dueDay")
    fun observeAll(): Flow<List<BillEntity>>

    @Upsert suspend fun upsert(bill: BillEntity): Long
    @Insert suspend fun insertAll(bills: List<BillEntity>)
    @Query("SELECT * FROM bills") suspend fun all(): List<BillEntity>
}

@Dao
interface WishDao {
    @Query("SELECT * FROM wishlist ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<WishEntity>>

    @Insert suspend fun insert(wish: WishEntity): Long
    @Query("SELECT * FROM wishlist") suspend fun all(): List<WishEntity>
    @Insert suspend fun insertAll(wishes: List<WishEntity>)
    @Update suspend fun update(wish: WishEntity)
}

@Dao
interface SharedDao {
    @Query("SELECT * FROM people WHERE archived = 0 ORDER BY name")
    fun observePeople(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM friend_groups WHERE archived = 0 ORDER BY id DESC")
    fun observeGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM group_members")
    fun observeMembers(): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM shared_expenses ORDER BY occurredAt DESC, id DESC")
    fun observeExpenses(): Flow<List<SharedExpenseEntity>>

    @Query("SELECT * FROM split_shares")
    fun observeShares(): Flow<List<SplitShareEntity>>

    @Query("SELECT * FROM settlements ORDER BY occurredAt DESC, id DESC")
    fun observeSettlements(): Flow<List<SettlementEntity>>

    @Insert suspend fun insertPerson(person: PersonEntity): Long
    @Query("SELECT * FROM people") suspend fun allPeople(): List<PersonEntity>
    @Query("SELECT * FROM friend_groups") suspend fun allGroups(): List<GroupEntity>
    @Query("SELECT * FROM group_members") suspend fun allMembers(): List<GroupMemberEntity>
    @Query("SELECT * FROM shared_expenses") suspend fun allExpenses(): List<SharedExpenseEntity>
    @Query("SELECT * FROM split_shares") suspend fun allShares(): List<SplitShareEntity>
    @Query("SELECT * FROM settlements") suspend fun allSettlements(): List<SettlementEntity>
    @Insert suspend fun insertPeople(people: List<PersonEntity>)
    @Insert suspend fun insertGroups(groups: List<GroupEntity>)
    @Insert suspend fun insertExpenses(expenses: List<SharedExpenseEntity>)
    @Insert suspend fun insertSettlements(settlements: List<SettlementEntity>)
    @Update suspend fun updatePerson(person: PersonEntity)

    @Insert suspend fun insertGroup(group: GroupEntity): Long
    @Update suspend fun updateGroup(group: GroupEntity)
    @Insert suspend fun insertMembers(members: List<GroupMemberEntity>)

    @Query("DELETE FROM group_members WHERE groupId = :groupId")
    suspend fun clearMembers(groupId: Long)

    @Insert suspend fun insertExpense(expense: SharedExpenseEntity): Long
    @Insert suspend fun insertShares(shares: List<SplitShareEntity>)

    @Query("SELECT id FROM shared_expenses WHERE transactionId = :transactionId")
    suspend fun expenseIdsForTransaction(transactionId: Long): List<Long>

    @Query("DELETE FROM split_shares WHERE expenseId = :expenseId")
    suspend fun deleteSharesOf(expenseId: Long)

    @Query("DELETE FROM shared_expenses WHERE id = :id")
    suspend fun deleteExpense(id: Long)

    @Query("SELECT * FROM shared_expenses WHERE id = :id")
    suspend fun expense(id: Long): SharedExpenseEntity?

    @Insert suspend fun insertSettlement(settlement: SettlementEntity): Long

    @Query("DELETE FROM settlements WHERE transactionId = :transactionId")
    suspend fun deleteSettlementsForTransaction(transactionId: Long)

    @Query("DELETE FROM settlements WHERE id = :id")
    suspend fun deleteSettlement(id: Long)

    @Query("SELECT * FROM settlements WHERE id = :id")
    suspend fun settlement(id: Long): SettlementEntity?
}

@Dao
interface SmsDao {
    @Query("SELECT * FROM sms_senders ORDER BY lastSeen DESC")
    fun observeSenders(): Flow<List<SmsSenderEntity>>

    @Query("SELECT * FROM pending_sms ORDER BY occurredAt DESC, id DESC")
    fun observePending(): Flow<List<PendingSmsEntity>>

    @Query("SELECT * FROM capture_rules ORDER BY id DESC")
    fun observeRules(): Flow<List<CaptureRuleEntity>>

    @Insert suspend fun insertRule(rule: CaptureRuleEntity): Long

    @Query("DELETE FROM capture_rules WHERE id = :id")
    suspend fun deleteRule(id: Long)

    @Query("SELECT * FROM mandates ORDER BY CASE status WHEN 'NEW' THEN 0 WHEN 'TRACKED' THEN 1 ELSE 2 END, lastSeen DESC")
    fun observeMandates(): Flow<List<MandateEntity>>

    @Query("SELECT * FROM mandates WHERE lower(merchant) = lower(:merchant) AND amount = :amount LIMIT 1")
    suspend fun mandate(merchant: String, amount: Long): MandateEntity?

    @Upsert suspend fun upsertMandate(mandate: MandateEntity): Long

    @Query("SELECT * FROM sms_senders WHERE sender = :sender")
    suspend fun sender(sender: String): SmsSenderEntity?

    @Upsert suspend fun upsertSender(sender: SmsSenderEntity)

    @Insert suspend fun insertPending(pending: PendingSmsEntity): Long

    @Query("SELECT * FROM pending_sms WHERE id = :id")
    suspend fun pending(id: Long): PendingSmsEntity?

    @Query("DELETE FROM pending_sms WHERE id = :id")
    suspend fun deletePending(id: Long)

    @Insert suspend fun insertKey(key: SmsKeyEntity)

    @Query("SELECT COUNT(*) FROM sms_keys WHERE fingerprint = :fingerprint AND at BETWEEN :from AND :to")
    suspend fun countKey(fingerprint: String, from: Long, to: Long): Int

    @Query("SELECT accountId FROM sms_account_links WHERE accountKey = :accountKey")
    suspend fun linkedAccount(accountKey: String): Long?

    @Upsert suspend fun upsertLink(link: SmsAccountLinkEntity)

    @Query("SELECT COUNT(*) FROM pending_sms")
    suspend fun pendingCount(): Int

    @Query("DELETE FROM pending_sms") suspend fun clearPending()
    @Query("DELETE FROM sms_senders") suspend fun clearSenders()
    @Query("DELETE FROM sms_keys") suspend fun clearKeys()
    @Query("DELETE FROM mandates") suspend fun clearMandates()
    @Query("DELETE FROM sms_account_links") suspend fun clearLinks()
}

@Dao
interface EmiDao {
    @Query("SELECT * FROM emis WHERE archived = 0 ORDER BY dueDay, id")
    fun observeAll(): Flow<List<EmiEntity>>

    @Query("SELECT * FROM emis") suspend fun all(): List<EmiEntity>
    @Insert suspend fun insert(emi: EmiEntity): Long
    @Insert suspend fun insertAll(emis: List<EmiEntity>)
    @Update suspend fun update(emi: EmiEntity)
}

@Dao
interface MoreDao {
    @Query("SELECT * FROM tags ORDER BY name") fun observeTags(): Flow<List<TagEntity>>
    @Query("SELECT * FROM transaction_tags") fun observeTxTags(): Flow<List<TransactionTagEntity>>
    @Query("SELECT * FROM tags") suspend fun allTags(): List<TagEntity>
    @Query("SELECT * FROM transaction_tags") suspend fun allTxTags(): List<TransactionTagEntity>
    @Insert suspend fun insertTag(tag: TagEntity): Long
    @Insert suspend fun insertTags(tags: List<TagEntity>)
    @Update suspend fun updateTag(tag: TagEntity)
    @Query("DELETE FROM tags WHERE id = :id") suspend fun deleteTag(id: Long)
    @Query("DELETE FROM transaction_tags WHERE tagId = :tagId") suspend fun clearTag(tagId: Long)
    @Query("DELETE FROM transaction_tags WHERE transactionId = :transactionId") suspend fun clearTagsOf(transactionId: Long)
    @Insert suspend fun insertTxTags(links: List<TransactionTagEntity>)

    @Query("SELECT * FROM templates ORDER BY id DESC") fun observeTemplates(): Flow<List<TemplateEntity>>
    @Query("SELECT * FROM templates") suspend fun allTemplates(): List<TemplateEntity>
    @Insert suspend fun insertTemplate(template: TemplateEntity): Long
    @Insert suspend fun insertTemplates(templates: List<TemplateEntity>)
    @Query("DELETE FROM templates WHERE id = :id") suspend fun deleteTemplate(id: Long)

    @Query("SELECT * FROM card_perks") fun observePerks(): Flow<List<CardPerkEntity>>
    @Query("SELECT * FROM card_perks") suspend fun allPerks(): List<CardPerkEntity>
    @Upsert suspend fun upsertPerk(perk: CardPerkEntity)
    @Insert suspend fun insertPerks(perks: List<CardPerkEntity>)

    @Query("SELECT * FROM challenges ORDER BY endEpochDay DESC") fun observeChallenges(): Flow<List<ChallengeEntity>>
    @Query("SELECT * FROM challenges") suspend fun allChallenges(): List<ChallengeEntity>
    @Insert suspend fun insertChallenge(challenge: ChallengeEntity): Long
    @Insert suspend fun insertChallenges(challenges: List<ChallengeEntity>)
    @Query("DELETE FROM challenges WHERE id = :id") suspend fun deleteChallenge(id: Long)

    @Query("SELECT * FROM recurring_splits WHERE archived = 0 ORDER BY dueDay") fun observeRecurring(): Flow<List<RecurringSplitEntity>>
    @Query("SELECT * FROM recurring_splits") suspend fun allRecurring(): List<RecurringSplitEntity>
    @Insert suspend fun insertRecurring(rule: RecurringSplitEntity): Long
    @Insert suspend fun insertRecurringAll(rules: List<RecurringSplitEntity>)
    @Update suspend fun updateRecurring(rule: RecurringSplitEntity)

    @Query("SELECT * FROM capture_rules") suspend fun allRules(): List<CaptureRuleEntity>
    @Insert suspend fun insertRules(rules: List<CaptureRuleEntity>)
}
