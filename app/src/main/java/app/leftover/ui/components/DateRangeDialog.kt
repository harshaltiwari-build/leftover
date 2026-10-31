package app.leftover.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private const val DAY_MS = 86_400_000L

/** Pick a start and end day. A single tapped day counts as both. The picker works in UTC days, so no time zone shifts creep in. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeDialog(title: String, confirmLabel: String, initialStart: LocalDate?, initialEnd: LocalDate?, onPicked: (LocalDate, LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStart?.toEpochDay()?.times(DAY_MS),
        initialSelectedEndDateMillis = initialEnd?.toEpochDay()?.times(DAY_MS),
    )
    fun day(ms: Long) = Instant.ofEpochMilli(ms).atOffset(ZoneOffset.UTC).toLocalDate()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val s = state.selectedStartDateMillis
                    if (s != null) onPicked(day(s), day(state.selectedEndDateMillis ?: s))
                    onDismiss()
                },
                enabled = state.selectedStartDateMillis != null,
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DateRangePicker(state, modifier = Modifier.weight(1f), title = { Text(title, modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}
