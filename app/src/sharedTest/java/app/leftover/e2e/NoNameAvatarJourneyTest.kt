package app.leftover.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Found on a fresh install: with no name set, Home's avatar showed "T" (from "there"). It should show a person. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class NoNameAvatarJourneyTest : JourneyBase() {
    @Test fun withoutANameTheAvatarShowsAPerson() {
        runBlocking { app.settings.setName("") }
        launchHome()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("icon:person"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("T").assertDoesNotExist()
    }
}
