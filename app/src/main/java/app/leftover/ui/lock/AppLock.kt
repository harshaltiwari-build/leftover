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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import app.leftover.domain.Pin
import app.leftover.ui.components.ButtonKind
import app.leftover.ui.components.LButton
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.LeftoverIcons
import app.leftover.ui.theme.Type

/**
 * Locking uses the phone's own screen lock: fingerprint or face where set up, otherwise the phone's PIN, pattern or
 * password. An optional Leftover PIN can be added on top, or instead on phones without a screen lock; only its salted
 * hash is stored.
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

/**
 * Covers the whole app until it's unlocked. Nothing underneath is shown or read out. With a Leftover PIN the pad
 * comes first and the phone's lock is offered as the other way in; without one, the phone's lock is asked for at once.
 */
@Composable
fun LockScreen(hasPin: Boolean, onTryPin: (String, (String?) -> Unit) -> Unit, onUnlocked: () -> Unit) {
    val c = Leftover.colors
    val activity = LocalActivity.current as? FragmentActivity
    val context = androidx.compose.ui.platform.LocalContext.current
    val phoneLock = remember { AppLock.available(context) }
    var message by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }

    fun ask() {
        activity ?: return
        message = null
        AppLock.prompt(activity, "Unlock Leftover", "Use your fingerprint, face or screen lock", onUnlocked, onFailure = { message = it })
    }
    fun submit() {
        if (checking || !Pin.valid(pin)) return
        checking = true
        onTryPin(pin) { error -> checking = false; pin = ""; message = error }
    }
    LaunchedEffect(Unit) { if (!hasPin) ask() }

    Box(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().navigationBarsPadding().clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(Modifier.padding(horizontal = 32.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(c.hero), contentAlignment = Alignment.Center) {
                Icon(LeftoverIcons.Lock, null, tint = c.heroNumber, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text("Leftover is locked", style = Type.h2, color = c.ink, textAlign = TextAlign.Center)
            if (hasPin) {
                Text("Enter your Leftover PIN", style = Type.caption, color = c.muted, textAlign = TextAlign.Center)
                PinDots(pin.length)
                message?.let { Text(it, style = Type.caption, color = c.emberText, textAlign = TextAlign.Center, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
                PinPad(
                    onDigit = { d -> if (pin.length < Pin.MAX_LENGTH) { pin += d; message = null; if (pin.length == Pin.MAX_LENGTH) submit() } },
                    onDelete = { pin = pin.dropLast(1) },
                    onDone = ::submit, doneEnabled = Pin.valid(pin) && !checking,
                )
                if (phoneLock) {
                    Text(
                        "Use fingerprint or screen lock instead", style = Type.captionBold, color = c.ink,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = ::ask).padding(12.dp),
                    )
                }
            } else {
                Text("Nothing is shown until you unlock, not even your balance.", style = Type.caption, color = c.muted, textAlign = TextAlign.Center)
                message?.let { Text(it, style = Type.caption, color = c.emberText, textAlign = TextAlign.Center) }
                Spacer(Modifier.height(8.dp))
                LButton("Unlock", onClick = ::ask, kind = ButtonKind.Lime)
            }
        }
    }
}

/** Filled dots for the digits typed so far. Screen readers hear the count, never the digits. */
@Composable
fun PinDots(count: Int) {
    val c = Leftover.colors
    Row(
        Modifier.padding(vertical = 8.dp).semantics { contentDescription = "$count ${if (count == 1) "digit" else "digits"} entered" },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(Pin.MAX_LENGTH) { i ->
            Box(Modifier.size(14.dp).clip(RoundedCornerShape(50)).background(if (i < count) c.ink else c.track))
        }
    }
}

/** Digits, delete and a tick. No "00" key, unlike the money keypad. */
@Composable
fun PinPad(onDigit: (Char) -> Unit, onDelete: () -> Unit, onDone: () -> Unit, doneEnabled: Boolean) {
    val c = Leftover.colors
    val rows = listOf("123", "456", "789", "<0>")
    Column(Modifier.widthIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    val label = when (key) { '<' -> "Delete"; '>' -> "Unlock"; else -> key.toString() }
                    val enabled = key != '>' || doneEnabled
                    Box(
                        Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(18.dp))
                            .background(when (key) { '>' -> if (doneEnabled) c.lime else c.surfaceAlt; '<' -> androidx.compose.ui.graphics.Color.Transparent; else -> c.surface })
                            .clickable(enabled = enabled, role = Role.Button) { when (key) { '<' -> onDelete(); '>' -> onDone(); else -> onDigit(key) } }
                            .semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (key) {
                            '<' -> Icon(LeftoverIcons.Delete, null, tint = c.ink, modifier = Modifier.size(24.dp))
                            '>' -> Text("OK", style = Type.bodyExtra, color = if (doneEnabled) c.onLime else c.muted)
                            else -> Text(key.toString(), style = Type.keypad, color = c.ink)
                        }
                    }
                }
            }
        }
    }
}

/** Choose a PIN, then type it again. Calls [onSet] with the PIN once both match. */
@Composable
fun PinSetupDialog(onSet: (String) -> Unit, onCancel: () -> Unit) {
    val c = Leftover.colors
    var first by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun next() {
        if (!Pin.valid(pin)) return
        val chosen = first
        when {
            chosen == null -> { first = pin; pin = ""; error = null }
            chosen == pin -> onSet(pin)
            else -> { first = null; pin = ""; error = "Those didn't match. Start again." }
        }
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier.clip(RoundedCornerShape(28.dp)).background(c.ground).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(if (first == null) "Choose a PIN" else "Type it again", style = Type.h2, color = c.ink)
            Text(
                if (first == null) "4 to 6 digits, just for Leftover. If you forget it you can still get in with your phone's screen lock, if it has one."
                else "So a typo doesn't lock you out.",
                style = Type.caption, color = c.muted, textAlign = TextAlign.Center,
            )
            PinDots(pin.length)
            error?.let { Text(it, style = Type.caption, color = c.emberText) }
            PinPad(onDigit = { d -> if (pin.length < Pin.MAX_LENGTH) pin += d }, onDelete = { pin = pin.dropLast(1) }, onDone = ::next, doneEnabled = Pin.valid(pin))
            Text("Cancel", style = Type.captionBold, color = c.muted, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onCancel).padding(12.dp))
        }
    }
}
