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
import app.leftover.data.Accent
import app.leftover.data.AppFont
import app.leftover.data.ThemeMode
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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
private fun variable(res: Int, weight: Int) = Font(
    res,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun family(res: Int) = FontFamily(variable(res, 400), variable(res, 500), variable(res, 600), variable(res, 700), variable(res, 800))

val Manrope = family(R.font.manrope)
private val Bricolage = family(R.font.bricolage)
private val Jakarta = family(R.font.jakarta)

/** The two families a font choice uses: one for big numbers and titles, one for everything you read. */
private fun families(font: AppFont): Pair<FontFamily, FontFamily> = when (font) {
    AppFont.MANROPE -> Manrope to Manrope
    AppFont.BRICOLAGE -> Bricolage to Manrope
    AppFont.JAKARTA -> Jakarta to Jakarta
}

/** The display family of a choice, for previews in Settings. */
fun previewFamily(font: AppFont): FontFamily = families(font).first

/** One complete type scale for a font choice. */
class TypeScale(font: AppFont) {
    private val display = families(font).first
    private val text = families(font).second
    private fun style(family: FontFamily, size: Int, weight: Int, tracking: Double = 0.0, line: Double? = null) = TextStyle(
        fontFamily = family,
        fontSize = size.sp,
        fontWeight = FontWeight(weight),
        letterSpacing = tracking.em,
        lineHeight = line?.let { (size * it).sp } ?: TextStyle.Default.lineHeight,
        fontFeatureSettings = "tnum",
    )

    val display64 = style(display, 64, 800, -0.045, 1.0)
    val display48 = style(display, 48, 800, -0.04, 1.05)
    val display36 = style(display, 36, 800, -0.035, 1.08)
    val h1 = style(display, 28, 800, -0.02, 1.15)
    val h2 = style(display, 22, 800, -0.01, 1.2)
    val title = style(display, 18, 800, line = 1.3)
    val body = style(text, 15, 500, line = 1.45)
    val bodyStrong = style(text, 15, 700, line = 1.4)
    val caption = style(text, 13, 600, line = 1.4)
    val captionBold = style(text, 13, 700, line = 1.4)
    val bodyExtra = style(text, 15, 800, line = 1.4)
    val small = style(text, 12, 600, line = 1.35)
    val smallBold = style(text, 12, 700, line = 1.35)
    val label = style(text, 12, 700, 0.08, 1.3)
    val keypad = style(text, 22, 700)
}

/**
 * Type scale: 12 · 13 · 15 · 18 · 22 · 28 · 36 · 48 · 64, tabular figures throughout. The font follows the choice in
 * Settings → Appearance; reading a style here inside a composable re-reads it when that choice changes.
 */
object Type {
    private val scales = HashMap<AppFont, TypeScale>()
    private var font by mutableStateOf(AppFont.MANROPE)

    fun use(choice: AppFont) { font = choice }
    private val s: TypeScale get() = scales.getOrPut(font) { TypeScale(font) }

    val display64 get() = s.display64
    val display48 get() = s.display48
    val display36 get() = s.display36
    val h1 get() = s.h1
    val h2 get() = s.h2
    val title get() = s.title
    val body get() = s.body
    val bodyStrong get() = s.bodyStrong
    val caption get() = s.caption
    val captionBold get() = s.captionBold
    val bodyExtra get() = s.bodyExtra
    val small get() = s.small
    val smallBold get() = s.smallBold
    val label get() = s.label
    val keypad get() = s.keypad
}

/** The accent swaps the brand highlight only: hero number, primary buttons, rings, selected tabs. Meaning colours never change. */
data class AccentColor(val fill: Color, val deep: Color)

fun accentOf(accent: Accent): AccentColor = when (accent) {
    Accent.LIME -> AccentColor(Color(0xFFD6F478), Color(0xFFB7DB3E))
    Accent.LAVENDER -> AccentColor(Color(0xFFC9BEFA), Color(0xFF9F8DF2))
    Accent.PEACH -> AccentColor(Color(0xFFF6C2A0), Color(0xFFEB9A66))
    Accent.SKY -> AccentColor(Color(0xFFAFCFEA), Color(0xFF7FAEDB))
}

private fun LeftoverColors.withAccent(accent: Accent): LeftoverColors {
    if (accent == Accent.LIME) return this
    val a = accentOf(accent)
    return if (isDark) copy(lime = a.fill, limeDeep = a.deep, hero = a.fill, chipSelected = a.fill)
    else copy(lime = a.fill, limeDeep = a.deep, heroNumber = a.fill)
}

private val LocalColors = staticCompositionLocalOf { LightColors }

object Leftover {
    val colors: LeftoverColors @Composable get() = LocalColors.current
}

@Composable
fun LeftoverTheme(mode: ThemeMode = ThemeMode.SYSTEM, pureBlack: Boolean = false, accent: Accent = Accent.LIME, font: AppFont = AppFont.MANROPE, content: @Composable () -> Unit) {
    // Set before any child reads a style, so the first frame already uses the chosen font.
    remember(font) { Type.use(font); font }
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = (if (dark) (if (pureBlack) BlackColors else DarkColors) else LightColors).withAccent(accent)
    val scheme = if (dark) {
        darkColorScheme(primary = colors.lime, onPrimary = colors.onLime, background = colors.ground, surface = colors.surface, onSurface = colors.ink, onBackground = colors.ink)
    } else {
        lightColorScheme(primary = colors.ink, onPrimary = colors.ground, background = colors.ground, surface = colors.surface, onSurface = colors.ink, onBackground = colors.ink)
    }
    CompositionLocalProvider(LocalColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
