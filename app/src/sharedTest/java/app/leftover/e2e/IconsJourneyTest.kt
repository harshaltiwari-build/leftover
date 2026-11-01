package app.leftover.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** Icons show on the tab bar, Home's actions, Profile's rhythm rows and Settings rows. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class IconsJourneyTest : JourneyBase() {
    private fun assertIcons(vararg names: String) = names.forEach { name ->
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("icon:$name"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun iconsAppearAcrossTheApp() {
        launchHome()
        assertIcons("home", "list", "pie", "trend", "mic", "tag", "people", "flag")
        navigate { compose.onNodeWithContentDescription("Your profile").performClick() }
        compose.waitUntilAtLeastOneExists(hasText("Your money rhythm"), 10_000)
        assertIcons("calendar", "income", "receipt", "wallet", "jar")
        navigate { compose.onNodeWithContentDescription("Settings").performClick() }
        assertIcons("bank", "grid", "hash", "message", "bell", "shield", "lock", "palette", "info")
    }
}
