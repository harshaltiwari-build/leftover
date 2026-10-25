package app.leftover.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.leftover.domain.KeypadInput
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.LeftoverIcons
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type

private val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(KeypadInput.DOUBLE_ZERO, "0", KeypadInput.DELETE))

/** Number pad shared by Quick add, onboarding and the widget sheet. Calls [onKey] with a digit, "00" or "del". */
@Composable
fun Keypad(onKey: (String) -> Unit, modifier: Modifier = Modifier, keyHeight: Dp = 52.dp) {
    val c = Leftover.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    val quiet = key == KeypadInput.DELETE || key == KeypadInput.DOUBLE_ZERO
                    Box(
                        Modifier
                            .weight(1f)
                            .height(keyHeight)
                            .clip(Radius.md)
                            .background(if (quiet) Color.Transparent else c.surface)
                            .clickable(role = Role.Button) { onKey(key) }
                            .semantics { contentDescription = if (key == KeypadInput.DELETE) "Delete digit" else key },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (key == KeypadInput.DELETE) {
                            Icon(LeftoverIcons.Delete, null, tint = c.ink, modifier = Modifier.size(24.dp))
                        } else {
                            Text(key, style = Type.keypad, color = c.ink)
                        }
                    }
                }
            }
        }
    }
}

/** The big rupee amount shown above the keypad. */
@Composable
fun AmountDisplay(digits: String, modifier: Modifier = Modifier, sign: String = "", signColor: Color = Leftover.colors.muted) {
    val c = Leftover.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Top) {
        Text(sign + "₹", style = Type.h1, color = signColor, modifier = Modifier.padding(top = 10.dp, end = 4.dp))
        Text(KeypadInput.display(digits), style = Type.display64, color = if (digits.isEmpty()) c.subtle else c.ink, maxLines = 1)
    }
}
