package app.leftover.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.leftover.notify.ReminderNotifier

/**
 * Returns a function that runs an action only once notifications are allowed, asking Android first if they aren't.
 * If the answer is no, [onDenied] runs instead and the action never does.
 */
@Composable
fun rememberNotificationGate(onDenied: () -> Unit = {}): (() -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pending?.invoke() else onDenied()
        pending = null
    }
    return { action ->
        when {
            ReminderNotifier.allowed(context) -> action()
            Build.VERSION.SDK_INT >= 33 -> { pending = action; launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            else -> onDenied()
        }
    }
}
