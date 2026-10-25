package app.leftover.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.leftover.ui.components.ButtonKind
import app.leftover.ui.components.LButton
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.LeftoverIcons
import app.leftover.ui.theme.Type

/**
 * Locking uses the phone's own screen lock: fingerprint or face where set up, otherwise the phone's PIN, pattern or
 * password. Leftover stores no PIN of its own, so there is nothing for it to leak and nothing to forget.
 */
object AppLock {
    private const val ALLOWED = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** False when the phone has no screen lock set up, in which case there is nothing to unlock with. */
    fun available(context: android.content.Context): Boolean =
        BiometricManager.from(context).canAuthenticate(ALLOWED) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(activity: FragmentActivity, title: String, subtitle: String?, onSuccess: () -> Unit, onFailure: (String) -> Unit = {}) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFailure(errString.toString())
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { if (subtitle != null) setSubtitle(subtitle) }
            .setAllowedAuthenticators(ALLOWED)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
    }
}

/** Covers the whole app until the phone's lock is passed. Nothing underneath is shown or read out. */
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val c = Leftover.colors
    val activity = LocalActivity.current as? FragmentActivity
    var message by remember { mutableStateOf<String?>(null) }

    fun ask() {
        activity ?: return
        message = null
        AppLock.prompt(activity, "Unlock Leftover", "Use your fingerprint, face or screen lock", onUnlocked, onFailure = { message = it })
    }
    LaunchedEffect(Unit) { ask() }

    Box(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().navigationBarsPadding().clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(c.hero), contentAlignment = Alignment.Center) {
                Icon(LeftoverIcons.Lock, null, tint = c.heroNumber, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text("Leftover is locked", style = Type.h2, color = c.ink, textAlign = TextAlign.Center)
            Text("Nothing is shown until you unlock, not even your balance.", style = Type.caption, color = c.muted, textAlign = TextAlign.Center)
            message?.let { Text(it, style = Type.caption, color = c.emberText, textAlign = TextAlign.Center) }
            Spacer(Modifier.height(8.dp))
            LButton("Unlock", onClick = ::ask, kind = ButtonKind.Lime)
        }
    }
}
