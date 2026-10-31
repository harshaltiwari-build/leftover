package app.leftover.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.leftover.R
import app.leftover.data.ThemeMode

@Immutable
data class LeftoverColors(
    val ground: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val hairline: Color,
    val border: Color,
    val ink: Color,
    val muted: Color,
    val subtle: Color,
    val lime: Color,
    val limeDeep: Color,
    val onLime: Color,
    val lavender: Color,
    val lavenderInk: Color,
    val lavenderMuted: Color,
    val ember: Color,
    val emberText: Color,
    val emberBg: Color,
    val positive: Color,
    val track: Color,
    val hero: Color,
    val onHero: Color,
    val onHeroMuted: Color,
    val heroNumber: Color,
    val chipSelected: Color,
    val onChipSelected: Color,
    val isDark: Boolean,
)

val LightColors = LeftoverColors(
    ground = Color(0xFFF8F8F2), surface = Color(0xFFFFFFFF), surfaceAlt = Color(0xFFF4F4EE),
    hairline = Color(0xFFECECE3), border = Color(0xFFE3E3D8), ink = Color(0xFF20251B), muted = Color(0xFF5E6357),
    subtle = Color(0xFFB9BDB0), lime = Color(0xFFD6F478), limeDeep = Color(0xFFB7DB3E), onLime = Color(0xFF20251B),
    lavender = Color(0xFFE4DDFB), lavenderInk = Color(0xFF2E2360), lavenderMuted = Color(0xFF4B3A8C),
    ember = Color(0xFFE8692E), emberText = Color(0xFFA8360A), emberBg = Color(0xFFFDE7D9), positive = Color(0xFF3F5A0B),
    track = Color(0xFFECECE3), hero = Color(0xFF20251B), onHero = Color(0xFFF8F8F2), onHeroMuted = Color(0xFFB9BDB0),
    heroNumber = Color(0xFFD6F478), chipSelected = Color(0xFF20251B), onChipSelected = Color(0xFFF8F8F2), isDark = false,
)

val DarkColors = LeftoverColors(
    ground = Color(0xFF12150F), surface = Color(0xFF1D2219), surfaceAlt = Color(0xFF252B20),
    hairline = Color(0xFF2C3326), border = Color(0xFF343C2D), ink = Color(0xFFF2F2EA), muted = Color(0xFFA9AE9F),
    subtle = Color(0xFF6B7064), lime = Color(0xFFD6F478), limeDeep = Color(0xFFB7DB3E), onLime = Color(0xFF20251B),
    lavender = Color(0xFF2A2540), lavenderInk = Color(0xFFE9E3FF), lavenderMuted = Color(0xFFC9BEFA),
    ember = Color(0xFFE8692E), emberText = Color(0xFFF6B08A), emberBg = Color(0xFF3A2418), positive = Color(0xFFC5E86B),
    track = Color(0xFF2C3326), hero = Color(0xFFD6F478), onHero = Color(0xFF20251B), onHeroMuted = Color(0xFF3A4033),
    heroNumber = Color(0xFF20251B), chipSelected = Color(0xFFD6F478), onChipSelected = Color(0xFF20251B), isDark = true,
)

/** Dark mode on true black, for OLED screens: the ground goes to #000 and surfaces step up from it. */
val BlackColors = DarkColors.copy(
    ground = Color(0xFF000000), surface = Color(0xFF0E100C), surfaceAlt = Color(0xFF161A13),
    hairline = Color(0xFF1E231A), border = Color(0xFF262C21), track = Color(0xFF1E231A),
)

/** One tile colour and one chart colour per category, used everywhere the category appears. */
@Immutable
data class CategoryColor(val tile: Color, val bar: Color)

object CategoryPalette {
    private val light = mapOf(
        "peach" to CategoryColor(Color(0xFFFCE3D3), Color(0xFFF2A97E)),
        "sand" to CategoryColor(Color(0xFFF1EAD8), Color(0xFFC9B48A)),
        "mint" to CategoryColor(Color(0xFFDDF1E6), Color(0xFF8CC9A6)),
        "sky" to CategoryColor(Color(0xFFDCEBF7), Color(0xFF8DB8DE)),
        "lavender" to CategoryColor(Color(0xFFE4DDFB), Color(0xFFB9A8F5)),
        "pistachio" to CategoryColor(Color(0xFFE3F0C9), Color(0xFFA9CC5E)),
        "rose" to CategoryColor(Color(0xFFF8DDE6), Color(0xFFE79AB3)),
        "butter" to CategoryColor(Color(0xFFFBE9B7), Color(0xFFE5C04F)),
        "grey" to CategoryColor(Color(0xFFECECE3), Color(0xFFB9BDB0)),
        "lime" to CategoryColor(Color(0xFFD6F478), Color(0xFFB7DB3E)),
    )
    val keys: List<String> = light.keys.toList()

    fun of(key: String, dark: Boolean): CategoryColor {
        val base = light[key] ?: light.getValue("grey")
        return if (dark) CategoryColor(tile = base.bar.copy(alpha = 0.22f), bar = base.bar) else base
    }
}

object Radius {
    val xs = RoundedCornerShape(8.dp)
    val sm = RoundedCornerShape(12.dp)
    val md = RoundedCornerShape(16.dp)
    val lg = RoundedCornerShape(22.dp)
    val xl = RoundedCornerShape(28.dp)
    val pill = RoundedCornerShape(percent = 50)
}

@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: Int) = Font(
    R.font.manrope,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Manrope = FontFamily(manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))

/** Type scale: 12 · 13 · 15 · 18 · 22 · 28 · 36 · 48 · 64, tabular figures throughout. */
object Type {
    private fun style(size: Int, weight: Int, tracking: Double = 0.0, line: Double? = null) = TextStyle(
        fontFamily = Manrope,
        fontSize = size.sp,
        fontWeight = FontWeight(weight),
        letterSpacing = tracking.em,
        lineHeight = line?.let { (size * it).sp } ?: TextStyle.Default.lineHeight,
        fontFeatureSettings = "tnum",
    )

    val display64 = style(64, 800, -0.045, 1.0)
    val display48 = style(48, 800, -0.04, 1.05)
    val display36 = style(36, 800, -0.035, 1.08)
    val h1 = style(28, 800, -0.02, 1.15)
    val h2 = style(22, 800, -0.01, 1.2)
    val title = style(18, 800, line = 1.3)
    val body = style(15, 500, line = 1.45)
    val bodyStrong = style(15, 700, line = 1.4)
    val caption = style(13, 600, line = 1.4)
    val captionBold = style(13, 700, line = 1.4)
    val bodyExtra = style(15, 800, line = 1.4)
    val small = style(12, 600, line = 1.35)
    val smallBold = style(12, 700, line = 1.35)
    val label = style(12, 700, 0.08, 1.3)
    val keypad = style(22, 700)
}

private val LocalColors = staticCompositionLocalOf { LightColors }

object Leftover {
    val colors: LeftoverColors @Composable get() = LocalColors.current
}

@Composable
fun LeftoverTheme(mode: ThemeMode = ThemeMode.SYSTEM, pureBlack: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) (if (pureBlack) BlackColors else DarkColors) else LightColors
    val scheme = if (dark) {
        darkColorScheme(primary = colors.lime, onPrimary = colors.onLime, background = colors.ground, surface = colors.surface, onSurface = colors.ink, onBackground = colors.ink)
    } else {
        lightColorScheme(primary = colors.ink, onPrimary = colors.ground, background = colors.ground, surface = colors.surface, onSurface = colors.ink, onBackground = colors.ink)
    }
    CompositionLocalProvider(LocalColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
