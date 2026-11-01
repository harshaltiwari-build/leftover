package app.leftover.e2e

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.leftover.data.ProfilePhotoStore
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A photo on Home and Profile, then removed. Picking from the gallery is the system's own screen, so the test puts a
 * photo in the way the picker's result does, then drives everything after that.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ProfilePhotoJourneyTest : JourneyBase() {
    @Test fun aPhotoShowsOnHomeAndProfileAndCanBeRemoved() {
        val f = File(app.cacheDir, "me.png")
        f.outputStream().use { Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val name = ProfilePhotoStore.importFrom(app, Uri.fromFile(f))!!
        runBlocking { app.settings.setProfilePhoto(name) }

        launchHome()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Your photo"), 10_000)
        navigate { compose.onNodeWithContentDescription("Your profile").performClick() }
        compose.waitUntilAtLeastOneExists(hasText("Change photo"), 10_000)
        compose.onNodeWithText("Change photo").performScrollTo().performClick()
        compose.onNodeWithText("Choose from gallery").assertExists()
        compose.onNodeWithText("Take a photo").assertExists()
        compose.onNodeWithText("Remove photo").performScrollTo().performClick()

        compose.waitUntilAtLeastOneExists(hasText("Add photo"), 10_000)
        assertNull(runBlocking { app.settings.settings.first().profilePhoto })
        compose.waitUntil(5_000) { !ProfilePhotoStore.file(app, name).exists() }
        assertFalse(ProfilePhotoStore.file(app, name).exists())
    }
}
