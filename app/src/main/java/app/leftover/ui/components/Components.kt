package app.leftover.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.leftover.domain.Money
import app.leftover.domain.Paise
import app.leftover.ui.theme.CategoryPalette
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.LeftoverIcons
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type

enum class ButtonKind { Primary, Lime, Outline, Quiet }

@Composable
fun LButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = 56.dp,
) {
    val c = Leftover.colors
    val container = when {
        !enabled -> c.track
        kind == ButtonKind.Primary -> c.chipSelected
        kind == ButtonKind.Lime -> c.lime
        else -> Color.Transparent
    }
    val content = when {
        !enabled -> c.muted
        kind == ButtonKind.Primary -> c.onChipSelected
        kind == ButtonKind.Lime -> c.onLime
        else -> c.ink
    }
    val border = if (kind == ButtonKind.Outline && enabled) BorderStroke(1.5.dp, c.ink) else null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .clip(Radius.md)
            .background(container)
            .let { if (border != null) it.border(border, Radius.md) else it }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = Type.bodyStrong, color = content, textAlign = TextAlign.Center)
        if (icon != null) {
            Spacer(Modifier.width(8.dp))
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun IconTile(icon: ImageVector, onClick: () -> Unit, label: String, modifier: Modifier = Modifier, filled: Boolean = false, size: Dp = 44.dp) {
    val c = Leftover.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(Radius.md)
            .background(if (filled) c.hero else c.surface)
            .border(1.dp, if (filled) Color.Transparent else c.hairline, Radius.md)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (filled) c.heroNumber else c.ink, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun BackButton(onClick: () -> Unit, label: String = "Back", close: Boolean = false) =
    IconTile(if (close) LeftoverIcons.Close else LeftoverIcons.Back, onClick, label)

@Composable
fun Card(
    modifier: Modifier = Modifier,
    shape: Shape = Radius.lg,
    color: Color = Leftover.colors.surface,
    bordered: Boolean = true,
    padding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color)
            .let { if (bordered) it.border(1.dp, Leftover.colors.hairline, shape) else it }
            .padding(padding),
        content = content,
    )
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Leftover.colors.hairline))
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = Type.label, color = Leftover.colors.muted, modifier = modifier.padding(horizontal = 4.dp).semantics { heading() })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    dashed: Boolean = false,
    /** Centre the label in the chip, for chips that share a row at equal widths. */
    center: Boolean = false,
    /** Optional press-and-hold action, for chips that can be removed. */
    onLongClick: (() -> Unit)? = null,
    /** RadioButton when the chips are a pick-one group; Checkbox when several can be on. */
    role: Role = Role.RadioButton,
) {
    val c = Leftover.colors
    val bg by animateColorAsState(if (selected) c.chipSelected else c.surface, label = "chip-bg")
    val fg = if (selected) c.onChipSelected else c.ink
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(Radius.pill)
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else c.border, Radius.pill)
            .let { if (onLongClick != null) it.combinedClickable(role = role, onClick = onClick, onLongClick = onLongClick).semantics { this.selected = selected } else it.selectable(selected, role, onClick) }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, if (center) Alignment.CenterHorizontally else Alignment.Start),
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        Text(text, style = Type.captionBold, color = fg, maxLines = 2, textAlign = if (center) TextAlign.Center else TextAlign.Start)
    }
}

private fun Modifier.selectable(selected: Boolean, role: Role, onClick: () -> Unit): Modifier =
    this.clickable(role = role, onClick = onClick).semantics { this.selected = selected }

@Composable
fun Segmented(options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Leftover.colors
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 44.dp).clip(Radius.md).background(c.track).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val on = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(Radius.sm)
                    .background(if (on) c.surface else Color.Transparent)
                    .selectable(on, Role.RadioButton) { onSelect(option) }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(option, style = Type.captionBold, color = c.ink, maxLines = 2, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun LSwitch(checked: Boolean, onChange: (Boolean) -> Unit, label: String, decorative: Boolean = false) {
    val c = Leftover.colors
    val track by animateColorAsState(if (checked) (if (c.isDark) c.lime else c.hero) else c.muted, label = "switch-track")
    val knobX by animateDpAsState(if (checked) 18.dp else 0.dp, label = "switch-knob")
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 44.dp)
            .let {
                if (decorative) it.clearAndSetSemantics {}
                else it.clickable(role = Role.Switch) { onChange(!checked) }.semantics {
                    contentDescription = label
                    toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = 46.dp, height = 28.dp).clip(CircleShape).background(track).padding(3.dp)) {
            Box(Modifier.padding(start = knobX).size(22.dp).clip(CircleShape).background(Color.White))
        }
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 58.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.bodyStrong, color = Leftover.colors.ink)
            if (subtitle != null) Text(subtitle, style = Type.small, color = Leftover.colors.muted)
        }
        LSwitch(checked, onChange, title, decorative = true)
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Leftover.colors.ink, track: Color = Leftover.colors.track, height: Dp = 8.dp) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "progress")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(animated).clip(CircleShape).background(color))
    }
}

/** Rounded tile with a category's icon in its one fixed colour. */
@Composable
fun CategoryTile(iconKey: String, colorKey: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val c = Leftover.colors
    val palette = CategoryPalette.of(colorKey, c.isDark)
    Box(modifier.size(size).clip(Radius.sm).background(palette.tile), contentAlignment = Alignment.Center) {
        Icon(LeftoverIcons.forKey(iconKey), null, tint = c.ink, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun Avatar(initial: String, color: Color, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(initial.take(1).uppercase(), style = Type.bodyExtra, color = Color(0xFF20251B))
    }
}

/** Shows an amount or the hidden placeholder, so "Hide balances" works everywhere with one call. */
@Composable
fun Amount(value: Paise, hidden: Boolean, style: TextStyle, modifier: Modifier = Modifier, color: Color = Leftover.colors.ink, signed: Boolean = false, showPaise: Boolean = false) {
    Text(
        text = if (hidden) Money.HIDDEN else Money.format(value, showPaise = showPaise, signed = signed),
        style = style,
        color = color,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Clip,
    )
}

@Composable
fun Banner(text: String, background: Color, foreground: Color, modifier: Modifier = Modifier, trailing: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(Radius.md)
            .background(background)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text, style = Type.bodyStrong, color = foreground, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = Type.captionBold, color = foreground)
    }
}

@Composable
fun ListRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    title: String,
    subtitle: String? = null,
    titleColor: Color = Leftover.colors.ink,
) {
    // At very large text sizes the amount drops under the name instead of squeezing it.
    val stacked = LocalDensity.current.fontScale >= 1.4f && trailing != null
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            leading?.invoke()
            Column(Modifier.weight(1f)) {
                Text(title, style = Type.bodyStrong, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = Type.caption, color = Leftover.colors.muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (!stacked) trailing?.invoke(this)
        }
        if (stacked) Row(Modifier.fillMaxWidth().padding(start = if (leading != null) 56.dp else 0.dp, top = 4.dp), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) { trailing?.invoke(this) }
    }
}

@Composable
fun CenteredHint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Type.caption, color = Leftover.colors.muted, textAlign = TextAlign.Center, modifier = modifier.fillMaxWidth().padding(24.dp))
}

/** A labelled single-line input in the app's field style. [error] replaces the hint line when set. */
@Composable
fun LabeledField(
    label: String,
    value: String,
    hint: String,
    onChange: (String) -> Unit,
    keyboard: KeyboardType,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    error: String? = null,
    capitalize: Boolean = false,
    password: Boolean = false,
) {
    val c = Leftover.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(label)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(Radius.md).background(c.surface).border(1.dp, if (error != null) c.emberText else c.border, Radius.md).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (prefix != null) {
                Text(prefix, style = Type.title, color = c.muted)
                Spacer(Modifier.width(6.dp))
            }
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(hint, style = Type.bodyStrong, color = c.subtle)
                BasicTextField(
                    value, onChange, textStyle = Type.bodyStrong.copy(color = c.ink), cursorBrush = SolidColor(c.ink), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard, capitalization = if (capitalize) KeyboardCapitalization.Words else KeyboardCapitalization.None),
                    visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (error != null) Text(error, style = Type.small, color = c.emberText, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

/** A settings line: title and helper on the left, an optional value and a chevron on the right. A warning tints the value. */
@Composable
fun SettingsRow(title: String, helper: String?, onClick: () -> Unit, modifier: Modifier = Modifier, value: String? = null, warning: Boolean = false) {
    val c = Leftover.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.bodyStrong, color = c.ink)
            if (helper != null) Text(helper, style = Type.small, color = c.muted)
        }
        if (value != null) Text(value, style = Type.captionBold, color = if (warning) c.emberText else c.muted)
        Icon(LeftoverIcons.Chevron, null, tint = c.muted, modifier = Modifier.size(18.dp))
    }
}
