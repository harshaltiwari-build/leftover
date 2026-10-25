package app.leftover.ui.split

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.leftover.data.db.GroupKind
import app.leftover.data.db.PersonEntity
import app.leftover.domain.Money
import app.leftover.domain.Paise
import app.leftover.ui.components.Avatar
import app.leftover.ui.theme.CategoryPalette
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.LeftoverIcons
import kotlin.math.abs

/** Colours handed to new friends in turn, so people are easy to tell apart without photos. */
val PersonColors = listOf("sky", "mint", "peach", "lavender", "rose", "butter", "pistachio")

/** A friend's coloured initial, or the lime "Y" when [person] is null (you). */
@Composable
fun PersonAvatar(person: PersonEntity?, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val c = Leftover.colors
    if (person == null) Avatar("Y", c.lime, modifier, size)
    else Avatar(person.name, CategoryPalette.of(person.color, c.isDark).bar, modifier, size)
}

fun GroupKind.label(): String = when (this) {
    GroupKind.FRIENDS -> "Friends"
    GroupKind.HOME -> "Home"
    GroupKind.TRIP -> "Trip"
    GroupKind.EVENT -> "Event"
}

fun GroupKind.icon(): ImageVector = when (this) {
    GroupKind.FRIENDS -> LeftoverIcons.People
    GroupKind.HOME -> LeftoverIcons.Home
    GroupKind.TRIP -> LeftoverIcons.Transport
    GroupKind.EVENT -> LeftoverIcons.Calendar
}

/** "owes you", "you owe" or "settled up", for the line under a friend's name. */
fun balanceCaption(balance: Paise): String = when {
    balance > 0 -> "owes you"
    balance < 0 -> "you owe"
    else -> "settled up"
}

fun balanceText(balance: Paise, hidden: Boolean): String = Money.display(abs(balance), hidden)
