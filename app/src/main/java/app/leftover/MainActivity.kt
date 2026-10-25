package app.leftover

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import app.leftover.domain.CaptureLink
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.leftover.data.ThemeMode
import app.leftover.ui.AppNav
import app.leftover.ui.AppViewModel
import app.leftover.ui.DeepLinks
import app.leftover.ui.lock.LockScreen
import app.leftover.ui.theme.LeftoverTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import android.view.WindowManager

class MainActivity : FragmentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    private fun handleLink(intent: Intent?) {
        val link = intent?.data ?: return
        if (link.scheme == DeepLinks.SCHEME && link.host == "capture") {
            val entry = CaptureLink.parse(link.getQueryParameter("amount"), link.getQueryParameter("merchant"), link.getQueryParameter("note"), link.getQueryParameter("kind"))
            viewModel.captureFromLink(entry) { message ->
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                if (entry != null && message.startsWith("Added")) viewModel.requestRoute(app.leftover.ui.Routes.REVIEW)
            }
            return
        }
        viewModel.requestRoute(DeepLinks.toRoute(link.scheme, link.host, link.getQueryParameter("kind")))
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) viewModel.onLeftApp()
    }

    /** Blanks the recent-apps preview. Android 13+ has a switch for exactly this; older versions need the secure flag. */
    private fun hideFromRecents(hide: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!hide)
        else if (hide) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onStart() {
        super.onStart()
        viewModel.onBackInApp()
        // An exported CSV only needs to exist while the share sheet is open.
        java.io.File(cacheDir, "leftover-export.csv").delete()
        viewModel.materialiseRecurring()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { viewModel.snapshot.value == null }
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleLink(intent)
        enableEdgeToEdge()
        setContent {
            val snapshot by viewModel.snapshot.collectAsState()
            val locked by viewModel.locked.collectAsState()
            val keepOutOfRecents = snapshot?.settings?.let { it.hideInSwitcher || it.appLock } == true
            LaunchedEffect(keepOutOfRecents) { hideFromRecents(keepOutOfRecents) }
            LeftoverTheme(snapshot?.settings?.themeMode ?: ThemeMode.SYSTEM) {
                Box(Modifier.fillMaxSize()) {
                    // While locked, nothing under the lock screen is drawn for screen readers either.
                    Box(Modifier.fillMaxSize().then(if (locked) Modifier.clearAndSetSemantics {} else Modifier)) { AppNav(viewModel) }
                    if (locked) LockScreen(onUnlocked = viewModel::unlock)
                }
            }
        }
    }
}
