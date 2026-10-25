package app.leftover.domain

import app.leftover.data.db.CategoryEntity
import app.leftover.data.db.CategoryKind
import app.leftover.data.db.DefaultCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceParserTest {
    private val categories = DefaultCategories.all.mapIndexed { index, c -> c.copy(id = index + 1L) } +
        CategoryEntity(id = 99, name = "Pets", icon = "other", color = "grey", kind = CategoryKind.EXPENSE)

    private fun parse(text: String) = VoiceParser.parse(text, categories)

    @Test fun theExampleFromTheDesign() {
        val e = parse("Spent 180 on coffee today at Blue Tokai")
        assertEquals(rupees(180), e.amount)
        assertEquals(EntryKind.EXPENSE, e.kind)
        assertEquals("Coffee", e.category?.name)
        assertEquals("Blue Tokai", e.merchant)
        assertEquals(0, e.daysAgo)
    }

    @Test fun knownMerchantAndYesterday() {
        val e = parse("uber 236 yesterday")
        assertEquals(rupees(236), e.amount)
        assertEquals("Transport", e.category?.name)
        assertEquals("Uber", e.merchant)
        assertEquals(1, e.daysAgo)
    }

    @Test fun incomeFromAClient() {
        val e = parse("got 6,500 from client")
        assertEquals(EntryKind.INCOME, e.kind)
        assertEquals(rupees(6500), e.amount)
        assertEquals("Freelance", e.category?.name)
        assertEquals("Client", e.merchant)
    }

    @Test fun numberWords() {
        assertEquals(rupees(1200), parse("paid twelve hundred for groceries").amount)
        assertEquals(rupees(500), parse("five hundred for dinner").amount)
        assertEquals(rupees(2500), parse("two thousand five hundred on shopping").amount)
    }

    @Test fun shorthandAmounts() {
        assertEquals(rupees(2000), parse("2k on shopping").amount)
        assertEquals(rupees(150000), parse("1.5 lakh salary credited").amount)
        assertEquals(rupees(1250), parse("spent ₹1,250 on dinner").amount)
        assertEquals(12550L, parse("spent 125.5 on lunch").amount)
    }

    @Test fun datesDoNotBecomeTheAmount() {
        val e = parse("spent 50 on chai 2 days ago")
        assertEquals(rupees(50), e.amount)
        assertEquals(2, e.daysAgo)
        assertEquals(2, parse("dinner 850 two days ago").daysAgo)
        assertEquals(2, parse("lunch 200 day before yesterday").daysAgo)
    }

    @Test fun salaryIsIncomeEvenWithoutAVerb() {
        val e = parse("salary 42000")
        assertEquals(EntryKind.INCOME, e.kind)
        assertEquals("Salary", e.category?.name)
    }

    @Test fun customCategoriesMatchByName() {
        assertEquals("Pets", parse("spent 300 on pets").category?.name)
    }

    @Test fun unknownThingsComeBackEmptySoTheScreenCanAsk() {
        val e = parse("spent 300 on a thing")
        assertEquals(rupees(300), e.amount)
        assertNull(e.category)
        assertNull(parse("hello there").amount)
    }

    @Test fun hiddenCategoriesAreNotChosen() {
        val hidden = categories.map { if (it.name == "Coffee") it.copy(hidden = true) else it }
        assertNull(VoiceParser.parse("coffee 100", hidden).category)
    }

    @Test fun keywordsMatchWholeWordsOnly() {
        assertNull(parse("spent 40 on cola").category)
        assertNull(parse("spent 900 on business").category)
        assertEquals("Transport", parse("took a cab 120").category?.name)
    }

    // ---- Hinglish and Devanagari ----

    @Test fun hinglishAmountAndCategory() {
        val e = parse("chai ke bees rupaye")
        assertEquals(rupees(20), e.amount)
        assertEquals("Coffee", e.category?.name)
    }

    @Test fun hindiNumberWordsWithMultipliers() {
        assertEquals(rupees(200), parse("do sau ka petrol").amount)
        assertEquals(rupees(3000), parse("teen hazaar kharch kiye").amount)
        assertEquals(rupees(150), parse("dedh sau ka khana").amount)
        assertEquals(rupees(2500), parse("dhai hazaar ka kharcha").amount)
    }

    @Test fun hinglishKindAndCategories() {
        val spent = parse("sabzi pe 120 kharch kiye aaj")
        assertEquals(EntryKind.EXPENSE, spent.kind)
        assertEquals("Groceries", spent.category?.name)
        assertEquals(rupees(120), spent.amount)
        assertEquals(EntryKind.INCOME, parse("salary mila 42000").kind)
    }

    @Test fun devanagariDigitsAndWords() {
        val e = parse("चाय ४० रुपये")
        assertEquals(rupees(40), e.amount)
        assertEquals("Coffee", e.category?.name)
        assertEquals(rupees(500), parse("पांच सौ खाना").amount)
    }

    @Test fun englishStillWorksAfterTheHindiWords() {
        assertEquals(rupees(1200), parse("twelve hundred on shopping").amount)
        assertEquals(rupees(180), parse("Spent 180 on coffee today at Blue Tokai").amount)
    }
}
