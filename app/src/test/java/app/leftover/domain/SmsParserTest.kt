package app.leftover.domain

import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxSource
import app.leftover.data.db.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sample texts are written in the common shapes Indian banks use; real wording varies, which is why unsure texts go to review. */
class SmsParserTest {
    private fun parse(text: String) = SmsParser.parse(text)

    // ---- who is allowed to be read ----

    @Test fun namedSendersAreReadAndPhoneNumbersAreNot() {
        assertTrue(SmsParser.isTransactionalSender("AX-HDFCBK-S"))
        assertTrue(SmsParser.isTransactionalSender("VM-SBIUPI"))
        assertTrue(SmsParser.isTransactionalSender("ICICIB"))
        assertFalse(SmsParser.isTransactionalSender("+919876543210"))
        assertFalse(SmsParser.isTransactionalSender("9876543210"))
        assertFalse(SmsParser.isTransactionalSender("56161"))
        assertFalse(SmsParser.isTransactionalSender(""))
        assertFalse(SmsParser.isTransactionalSender("Mum and Dad"))
    }

    @Test fun senderKeyIgnoresTheRoutingPrefixAndSuffix() {
        assertEquals("HDFCBK", SmsParser.senderKey("AX-HDFCBK-S"))
        assertEquals("HDFCBK", SmsParser.senderKey("VM-HDFCBK"))
        assertEquals("HDFCBK", SmsParser.senderKey("hdfcbk"))
    }

    // ---- what is never read ----

    @Test fun otpMessagesAreIgnoredEvenWithAnAmount() {
        assertNull(parse("123456 is your OTP for transaction of Rs 500 at AMAZON. Do not share with anyone."))
        assertNull(parse("Use verification code 482913 to pay Rs.1,200.00 to SWIGGY"))
    }

    @Test fun failedPromotionalAndReminderTextsAreIgnored() {
        assertNull(parse("Transaction of Rs 500 declined at SWIGGY due to insufficient funds"))
        assertNull(parse("Get pre-approved personal loan up to Rs 5,00,000. Apply now!"))
        assertNull(parse("Your HDFC Credit Card bill of Rs 12,000 is due on 05-11-26. Min amount due Rs 600"))
        assertNull(parse("Spend Rs 5000 this month and win a gift"))
        assertNull(parse("Your A/c XX1234 balance is Rs 5,000"))
    }

    // ---- debits ----

    @Test fun upiDebitWithMerchantAndReference() {
        val p = parse("Sent Rs.180.00 From HDFC Bank A/C *1234 To BLUE TOKAI On 25/10/26 Ref 629912345678 Not You? Call 18002586161")!!
        assertEquals(SmsKind.DEBIT, p.kind)
        assertEquals(18000L, p.amount)
        assertEquals("Blue Tokai", p.merchant)
        assertEquals("1234", p.last4)
        assertEquals("629912345678", p.reference)
        assertFalse(p.isCard)
        assertEquals(Confidence.HIGH, p.confidence)
    }

    @Test fun debitWithoutARupeeSign() {
        val p = parse("Dear UPI user A/C X1234 debited by 250.0 on date 25Oct26 trf to SWIGGY Refno 629912345678. If not u? call 1800111109")!!
        assertEquals(SmsKind.DEBIT, p.kind)
        assertEquals(25000L, p.amount)
        assertEquals("Swiggy", p.merchant)
        assertEquals("1234", p.last4)
    }

    @Test fun creditCardSpendUsesTheSpentAmountNotTheLimit() {
        val p = parse("Rs 1,499.00 spent on ICICI Bank Card XX4321 on 25-Oct-26 at AMAZON PAY INDIA. Avl Limit: Rs 85,000.00. If not you, call 1800 2662.")!!
        assertEquals(SmsKind.DEBIT, p.kind)
        assertEquals(149900L, p.amount)
        assertEquals("Amazon Pay India", p.merchant)
        assertEquals("4321", p.last4)
        assertTrue(p.isCard)
        assertEquals("card:4321", p.accountKey)
    }

    @Test fun theBalanceIsNeverMistakenForTheAmount() {
        val p = parse("Your a/c XX1234 is debited Rs 100 at TEA POINT. Bal Rs 5,000")!!
        assertEquals(10000L, p.amount)
        assertEquals("Tea Point", p.merchant)
    }

    @Test fun atmWithdrawalIsADebit() {
        val p = parse("Rs 2000 withdrawn from A/c XX1234 at ATM MG ROAD on 25-10-26. Avl Bal Rs 8,000")!!
        assertEquals(SmsKind.DEBIT, p.kind)
        assertEquals(200000L, p.amount)
    }

    @Test fun phoneNumberPayeesAreNotStoredAsMerchants() {
        val p = parse("Sent Rs.500.00 From HDFC Bank A/C *1234 To 9876543210@ybl On 25/10/26 Ref 629912345678")!!
        assertEquals("", p.merchant)
    }

    // ---- credits ----

    @Test fun salaryCredit() {
        val p = parse("Rs 42,000.00 credited to your A/c XX1234 on 25-10-26 by NEFT-ACME CORP SALARY. Avl Bal Rs 51,000")!!
        assertEquals(SmsKind.CREDIT, p.kind)
        assertEquals(4_200_000L, p.amount)
        assertFalse(p.possibleTransfer)
    }

    @Test fun upiCreditNamesTheSender() {
        val p = parse("Rs 500.00 received from RAHUL KUMAR (UPI Ref No 629912345679) in your A/c XX1234.")!!
        assertEquals(SmsKind.CREDIT, p.kind)
        assertEquals("Rahul Kumar", p.merchant)
        assertEquals("629912345679", p.reference)
    }

    @Test fun cardPaymentReceivedLooksLikeAMoveBetweenOwnAccounts() {
        val p = parse("Payment of Rs 5,000.00 received towards your ICICI Bank Credit Card XX4321. Thank you.")!!
        assertEquals(SmsKind.CREDIT, p.kind)
        assertTrue(p.possibleTransfer)
    }

    @Test fun refundWithoutAMerchantIsLowConfidence() {
        val p = parse("Refund of Rs 899.00 for your order credited to your card XX4321")!!
        assertEquals(SmsKind.CREDIT, p.kind)
        assertEquals(Confidence.LOW, p.confidence)
    }

    // ---- the same text twice ----

    @Test fun theSameTextHasTheSameFingerprint() {
        val a = parse("Sent Rs.180.00 From HDFC Bank A/C *1234 To BLUE TOKAI On 25/10/26 Ref 629912345678")!!
        val b = parse("Sent Rs.180.00 From HDFC Bank A/C *1234 To BLUE TOKAI On 25/10/26 Ref 629912345678. Not You? Call 1800")!!
        assertEquals(SmsParser.fingerprint("HDFCBK", a), SmsParser.fingerprint("HDFCBK", b))
    }

    @Test fun differentAmountsOrSendersDiffer() {
        val a = parse("Sent Rs.180.00 From HDFC Bank A/C *1234 To BLUE TOKAI On 25/10/26 Ref 629912345678")!!
        val b = a.copy(amount = 19000)
        assertNotEquals(SmsParser.fingerprint("HDFCBK", a), SmsParser.fingerprint("HDFCBK", b))
        assertNotEquals(SmsParser.fingerprint("HDFCBK", a), SmsParser.fingerprint("SBIINB", a))
    }

    // ---- the decision to save without asking ----

    private val debit = ParsedSms(SmsKind.DEBIT, 18000, "Blue Tokai", "1234", false, "629912345678", false, Confidence.HIGH)
    private val ready = SmsContext(senderTrusted = true, accountId = 1, categoryId = 3, duplicateOfExisting = false)

    @Test fun onlyAKnownTrustedCleanDebitIsSavedWithoutAsking() {
        assertEquals(SmsDecision.AutoSave, SmsPolicy.decide(debit, ready))
    }

    @Test fun everyMissingPieceSendsTheTextToReview() {
        fun reason(parsed: ParsedSms = debit, context: SmsContext = ready) = (SmsPolicy.decide(parsed, context) as SmsDecision.Review).reason
        assertEquals(PendingReason.NEW_SENDER, reason(context = ready.copy(senderTrusted = false)))
        assertEquals(PendingReason.NEW_ACCOUNT, reason(context = ready.copy(accountId = null)))
        assertEquals(PendingReason.NEW_MERCHANT, reason(context = ready.copy(categoryId = null)))
        assertEquals(PendingReason.UNSURE, reason(parsed = debit.copy(confidence = Confidence.LOW)))
        assertEquals(PendingReason.CREDIT, reason(parsed = debit.copy(kind = SmsKind.CREDIT)))
        assertEquals(PendingReason.POSSIBLE_TRANSFER, reason(parsed = debit.copy(possibleTransfer = true)))
        assertEquals(PendingReason.POSSIBLE_DUPLICATE, reason(context = ready.copy(duplicateOfExisting = true)))
    }

    // ---- remembering merchants ----

    private fun tx(merchant: String, category: Long?, at: Long, type: TxType = TxType.EXPENSE, amount: Long = 18000, source: TxSource = TxSource.MANUAL) =
        TransactionEntity(type = type, amount = amount, accountId = 1, categoryId = category, merchant = merchant, occurredAt = at, source = source)

    @Test fun aRepeatMerchantReusesTheLatestCategory() {
        val history = listOf(tx("Blue Tokai", 2, at = 100), tx("blue  tokai", 5, at = 200), tx("Swiggy", 1, at = 300))
        assertEquals(5L, SmsPolicy.categoryFor("BLUE TOKAI", history))
        assertNull(SmsPolicy.categoryFor("Zepto", history))
        assertNull(SmsPolicy.categoryFor("", history))
    }

    @Test fun aManualEntryForTheSameAmountNearbyCountsAsAPossibleDuplicate() {
        val now = 10_000_000L
        assertTrue(SmsPolicy.looksLikeExisting(debit, now, listOf(tx("Coffee", 2, at = now - 3_600_000))))
        assertFalse(SmsPolicy.looksLikeExisting(debit, now, listOf(tx("Coffee", 2, at = now - 8 * 3_600_000))))
        assertFalse(SmsPolicy.looksLikeExisting(debit, now, listOf(tx("Coffee", 2, at = now, amount = 19000))))
        assertFalse(SmsPolicy.looksLikeExisting(debit, now, listOf(tx("Coffee", 2, at = now, source = TxSource.SMS))))
    }
}
