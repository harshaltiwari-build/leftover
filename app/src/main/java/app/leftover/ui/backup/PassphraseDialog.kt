package app.leftover.ui.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.leftover.ui.components.ButtonKind
import app.leftover.ui.components.Card
import app.leftover.ui.components.LButton
import app.leftover.ui.components.LabeledField
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.Type

const val MIN_PASSPHRASE = 8

/**
 * Asks for a passphrase over the current screen. When [creating] it asks twice and explains that nobody can reset it.
 * The text is handed back as a CharArray, which the caller wipes after use.
 */
@Composable
fun PassphraseDialog(
    title: String,
    message: String,
    confirmLabel: String,
    creating: Boolean,
    error: String?,
    busy: Boolean,
    onSubmit: (CharArray) -> Unit,
    onCancel: () -> Unit,
) {
    val c = Leftover.colors
    var first by rememberSaveable { mutableStateOf("") }
    var second by rememberSaveable { mutableStateOf("") }
    val tooShort = creating && first.isNotEmpty() && first.length < MIN_PASSPHRASE
    val mismatch = creating && second.isNotEmpty() && first != second
    val ready = first.isNotEmpty() && (!creating || (first.length >= MIN_PASSPHRASE && first == second))

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(enabled = false) {}.imePadding().padding(20.dp), contentAlignment = Alignment.Center) {
        Card(padding = PaddingValues(20.dp)) {
            Text(title, style = Type.title, color = c.ink)
            Spacer(Modifier.height(6.dp))
            Text(message, style = Type.caption, color = c.muted)
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledField("Passphrase", first, "", { first = it.take(100) }, KeyboardType.Password, password = true, error = if (tooShort) "At least $MIN_PASSPHRASE characters" else null)
                if (creating) LabeledField("Type it again", second, "", { second = it.take(100) }, KeyboardType.Password, password = true, error = if (mismatch) "They don't match" else null)
                if (error != null) Text(error, style = Type.captionBold, color = c.emberText)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LButton("Cancel", onCancel, modifier = Modifier.weight(1f), kind = ButtonKind.Outline, height = 48.dp, enabled = !busy)
                LButton(if (busy) "Working…" else confirmLabel, { onSubmit(first.toCharArray()) }, modifier = Modifier.weight(1f), height = 48.dp, enabled = ready && !busy)
            }
        }
    }
}
