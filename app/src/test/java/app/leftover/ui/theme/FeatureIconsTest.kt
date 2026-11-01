package app.leftover.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every feature gets its own icon: none missing, none falling back to "Other", no two in one place looking the same. */
class FeatureIconsTest {
    private fun assertDistinct(group: String, icons: Map<String, androidx.compose.ui.graphics.vector.ImageVector>, expected: List<String>) {
        assertEquals("$group labels", expected, icons.keys.toList())
        assertTrue("$group falls back to Other: $icons", icons.values.none { it.name == LeftoverIcons.Other.name })
        val names = icons.values.map { it.name }
        assertEquals("$group repeats an icon: $names", names.size, names.toSet().size)
    }

    @Test fun `tab bar`() = assertDistinct("tabs", FeatureIcons.tabs, listOf("Home", "Activity", "Plan", "Insights"))

    @Test fun `home quick actions, with Afford and Goals told apart`() {
        assertDistinct("quick actions", FeatureIcons.quickActions, listOf("Say it", "Afford?", "Split", "Goals"))
        assertNotEquals(FeatureIcons.quickActions.getValue("Afford?").name, FeatureIcons.quickActions.getValue("Goals").name)
    }

    @Test fun `plan sections`() = assertDistinct("plan", FeatureIcons.planTabs, listOf("Budgets", "Goals", "Bills", "Wishlist"))

    @Test fun `profile money rhythm`() = assertDistinct("rhythm", FeatureIcons.rhythm, listOf("Payday", "Comes in", "Bills and EMIs", "Spending budget", "Saving"))

    @Test fun `settings rows`() = assertDistinct(
        "settings", FeatureIcons.settings,
        listOf(
            "Accounts and cards", "Categories", "Tags and events", "Money rhythm", "Bills and plans", "Automatic tracking",
            "Reminders", "Backup and restore", "Privacy and permissions", "Appearance and language", "About Leftover",
        ),
    )

    @Test fun `savings no longer draws the same target as Plan`() {
        assertNotEquals(LeftoverIcons.Target.name, LeftoverIcons.Savings.name)
        assertNotEquals(LeftoverIcons.Target.root.toString(), LeftoverIcons.Savings.root.toString())
    }

    @Test fun `every built-in category key has its own icon`() {
        listOf("food", "coffee", "groceries", "transport", "shopping", "snacks", "fun", "personal", "income", "savings", "bolt", "wifi", "phone", "tv", "music", "gym", "home")
            .forEach { assertNotEquals(it, LeftoverIcons.Other.name, LeftoverIcons.forKey(it).name) }
    }

    @Test fun `a new category gets an icon guessed from its name`() {
        assertEquals("coffee", LeftoverIcons.guessKey("Chai and coffee"))
        assertEquals("transport", LeftoverIcons.guessKey("Uber / Ola"))
        assertEquals("bolt", LeftoverIcons.guessKey("Electricity"))
        assertEquals("other", LeftoverIcons.guessKey("Stuff"))
    }
}
