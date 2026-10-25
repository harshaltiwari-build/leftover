package app.leftover.ui.split

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import app.leftover.ui.components.Avatar
import app.leftover.ui.theme.CategoryPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.leftover.domain.UpiLink
import app.leftover.ui.components.BackButton
import app.leftover.ui.components.LButton
import app.leftover.ui.components.LabeledField
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.Type

/** Adds a friend. The UPI id is optional and only used to open your UPI app when you settle up. */
@Composable
fun AddPersonScreen(onClose: () -> Unit, onSave: (name: String, upiId: String?) -> Unit) {
    val c = Leftover.colors
    var name by rememberSaveable { mutableStateOf("") }
    var upi by rememberSaveable { mutableStateOf("") }
    val upiError = if (upi.isNotBlank() && !UpiLink.isValidId(upi.trim())) "That doesn't look like a UPI id, for example name@bank" else null

    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onClose, "Close", close = true)
            Text("Add a friend", style = Type.h2, color = c.ink)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Avatar(name.trim().ifEmpty { "?" }, if (name.isBlank()) c.track else CategoryPalette.of(PersonColors.first(), c.isDark).bar, size = 84.dp)
            }
            LabeledField("Name", name, "Rohan", { name = it.take(30) }, KeyboardType.Text, capitalize = true)
            LabeledField("UPI id (optional)", upi, "rohan@okhdfc", { upi = it.trim().take(60) }, KeyboardType.Email, error = upiError)
            Text(
                "Saved only on this phone. With a UPI id, Settle up can open your UPI app with the amount filled in. Without one, you can still record payments by hand.",
                style = Type.small, color = c.muted,
            )
        }
        LButton("Add friend", enabled = name.isNotBlank() && upiError == null, onClick = {
            onSave(name.trim(), upi.trim().ifBlank { null })
            onClose()
        })
    }
}
