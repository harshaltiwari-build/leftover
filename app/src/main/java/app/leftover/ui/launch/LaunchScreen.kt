package app.leftover.ui.launch

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.leftover.R
import app.leftover.ui.components.ButtonKind
import app.leftover.ui.components.LButton
import app.leftover.ui.theme.Leftover
import app.leftover.ui.theme.LeftoverIcons
import app.leftover.ui.theme.Radius
import app.leftover.ui.theme.Type
import kotlin.math.sin

/** Animation timeline in seconds. Mirrors the "00 Opening animation" board in the design file. */
private object T {
    const val TILE_IN = 0.15f
    const val TILE_DONE = 0.55f
    const val STEM_START = 0.5f
    const val STEM_END = 0.95f
    const val BASE_START = 0.85f
    const val BASE_END = 1.25f
    const val COIN_START = 1.0f
    const val COIN_LAND = 1.4f
    const val COIN_SETTLE = 1.6f
    const val SLIDE_START = 1.9f
    const val SLIDE_END = 2.5f
    const val TAGLINE_IN = 2.4f
    const val TAGLINE_OUT = 3.3f
    const val GLIDE_START = 3.5f
    const val GLIDE_END = 4.2f
    const val WELCOME_START = 4.0f
    const val WELCOME_END = 5.05f
    const val RETURNING_END = 3.4f
}

private val smooth = CubicBezierEasing(0.65f, 0f, 0.25f, 1f)
private val easeOut = CubicBezierEasing(0.3f, 0f, 0.2f, 1f)

private fun seg(t: Float, from: Float, to: Float, easing: Easing = LinearEasing): Float =
    easing.transform(((t - from) / (to - from)).coerceIn(0f, 1f))

private fun backOut(x: Float): Float {
    val c1 = 1.8f
    val c3 = c1 + 1f
    val p = x - 1f
    return 1f + c3 * p * p * p + c1 * p * p
}

/**
 * The app opening: the lime tile springs in, the L draws itself, the coin drops, the wordmark wipes out from behind the icon,
 * then the whole lockup glides into the header of the welcome screen.
 * Returning users ([showWelcome] = false) get the same logo build, then a fade straight into the app.
 */
@Composable
fun LaunchScreen(showWelcome: Boolean, restoreError: String?, onGetStarted: () -> Unit, onRestore: () -> Unit, onFinished: () -> Unit) {
    val reduceMotion = rememberReduceMotion()
    val end = when {
        reduceMotion -> 0f
        showWelcome -> T.WELCOME_END
        else -> T.RETURNING_END
    }
    val clock = remember { Animatable(if (reduceMotion) end else 0f) }
    var finishedCalled by remember { mutableStateOf(false) }

    LaunchedEffect(end) {
        if (end > 0f) clock.animateTo(end, tween(durationMillis = (end * 1000).toInt(), easing = LinearEasing))
        if (!showWelcome && !finishedCalled) {
            finishedCalled = true
            onFinished()
        }
    }

    val t = clock.value
    val c = Leftover.colors
    val interactive = showWelcome && t >= T.WELCOME_END - 0.2f

    BoxWithConstraints(Modifier.fillMaxSize().background(c.ground)) {
        if (showWelcome) {
            WelcomeContent(progress = { index -> seg(t, T.WELCOME_START + index * 0.12f, T.WELCOME_START + 0.5f + index * 0.12f, easeOut) }, enabled = interactive, restoreError = restoreError, onGetStarted = onGetStarted, onRestore = onRestore)
        }
        Lockup(t = t, showWelcome = showWelcome, maxWidth = maxWidth, maxHeight = maxHeight)
        if (!showWelcome) {
            val fade = seg(t, T.RETURNING_END - 0.35f, T.RETURNING_END)
            Box(Modifier.fillMaxSize().alpha(fade).background(c.ground))
        }
    }
}

@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
}

private const val LOCKUP_WIDTH = 150f
private const val LOCKUP_HEIGHT = 34.5f
private const val ICON_SIZE = 31.75f
private const val ICON_LEFT = 1.86f
private const val ICON_TOP = 1.64f
private const val WORD_LEFT = 36.7f
private const val WORD_WIDTH = 113.3f
private const val ICON_CENTER_OFFSET = 57.3f
/** While the L draws, the mark alone is shown this wide (as a share of the screen width). */
private const val MARK_SHARE = 0.34f

/** Once the wordmark joins, the whole lockup spans the screen less this margin. */
private const val LOCKUP_MARGIN = 56f

@Composable
private fun Lockup(t: Float, showWelcome: Boolean, maxWidth: Dp, maxHeight: Dp) {
    val c = Leftover.colors
    val density = LocalDensity.current

    val tile = backOut(seg(t, T.TILE_IN, T.TILE_DONE))
    val stem = seg(t, T.STEM_START, T.STEM_END, smooth)
    val base = seg(t, T.BASE_START, T.BASE_END, smooth)
    val coinFall = seg(t, T.COIN_START, T.COIN_LAND, CubicBezierEasing(0.55f, 0f, 1f, 0.45f))
    val bounce = seg(t, T.COIN_LAND, T.COIN_SETTLE)
    val coinDrop = if (t < T.COIN_START) 0f else coinFall - 0.12f * sin(bounce * Math.PI.toFloat()).coerceAtLeast(0f)
    val squash = when {
        t < T.COIN_LAND -> 0f
        t < T.COIN_SETTLE -> sin(bounce * Math.PI.toFloat() * 1.0f)
        else -> 0f
    }
    val slide = seg(t, T.SLIDE_START, T.SLIDE_END, smooth)
    val glide = if (showWelcome) seg(t, T.GLIDE_START, T.GLIDE_END, smooth) else 0f

    // Big while the mark is alone, then it settles into a lockup that spans the screen, then glides into the header.
    val markScale = maxWidth.value * MARK_SHARE / ICON_SIZE
    val lockScale = (maxWidth.value - LOCKUP_MARGIN) / LOCKUP_WIDTH
    val heroScale = markScale + (lockScale - markScale) * slide
    val scale = heroScale + (1f - heroScale) * glide

    // The lockup's centre is the mark's centre until the wordmark joins, so centring the box keeps both states centred.
    val statusBarTop = with(density) { WindowInsets.statusBars.getTop(density).toDp().value }
    val centerY = maxHeight.value * 0.40f
    val heroX = (maxWidth.value - LOCKUP_WIDTH * heroScale) / 2f
    val heroY = centerY - LOCKUP_HEIGHT * heroScale / 2f
    val x = heroX + (24f - heroX) * glide
    val y = heroY + (statusBarTop + 20f - heroY) * glide

    // A soft ring spreads out from the mark when the coin lands.
    val ripple = seg(t, T.COIN_LAND, T.COIN_LAND + 0.8f, easeOut)
    if (ripple > 0f && ripple < 1f && glide == 0f) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = (size.width * 0.12f) + size.width * 0.5f * ripple
            drawCircle(MarkGeometry.lime.copy(alpha = 0.55f * (1f - ripple)), radius, Offset(size.width / 2f, centerY.dp.toPx()), style = Stroke(width = 3.dp.toPx()))
        }
    }

    Box(
        Modifier
            .offset(x = x.dp, y = y.dp)
            .size(LOCKUP_WIDTH.dp, LOCKUP_HEIGHT.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            },
    ) {
        val iconShift = ICON_CENTER_OFFSET * (1f - slide)
        LeftoverMark(
            size = ICON_SIZE.dp,
            tile = tile,
            stem = stem,
            base = base,
            coinDrop = coinDrop,
            squash = squash,
            modifier = Modifier.offset(x = (ICON_LEFT + iconShift).dp, y = ICON_TOP.dp),
        )
        val wordmark = if (c.isDark) R.drawable.wordmark_ivory else R.drawable.wordmark_ink
        Image(
            painter = painterResource(wordmark),
            contentDescription = "Leftover",
            modifier = Modifier
                .offset(x = WORD_LEFT.dp + (12f * (1f - slide)).dp, y = 0.dp)
                .width(WORD_WIDTH.dp)
                .alpha(slide)
                .drawWithContent {
                    clipRect(right = size.width * slide) { this@drawWithContent.drawContent() }
                },
        )
    }

    if (!showWelcome || t < T.GLIDE_START) {
        val tagAlpha = seg(t, T.TAGLINE_IN, T.TAGLINE_IN + 0.5f) * (1f - seg(t, T.TAGLINE_OUT, T.TAGLINE_OUT + 0.25f))
        Text(
            "Know what you can spend.",
            style = Type.title,
            color = c.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (centerY + LOCKUP_HEIGHT * lockScale / 2f + 28f).dp)
                .alpha(if (showWelcome) tagAlpha else seg(t, T.TAGLINE_IN, T.TAGLINE_IN + 0.5f))
                .graphicsLayer { translationY = (1f - seg(t, T.TAGLINE_IN, T.TAGLINE_IN + 0.5f)) * 12.dp.toPx() },
        )
    }
}

/**
 * The welcome screen. [progress] gives each block's entrance (0..1) so the opening animation can stagger them;
 * pass `{ 1f }` to show it fully built.
 */
@Composable
fun WelcomeContent(progress: (Int) -> Float, enabled: Boolean, restoreError: String?, onGetStarted: () -> Unit, onRestore: () -> Unit) {
    val c = Leftover.colors
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
        Spacer(Modifier.height(20.dp + LOCKUP_HEIGHT.dp))

        Box(
            Modifier
                .padding(top = 28.dp)
                .fillMaxWidth()
                .height(330.dp)
                .graphicsLayer {
                    alpha = progress(0)
                    translationY = (1f - progress(0)) * 28.dp.toPx()
                },
        ) {
            WelcomeHeroCard(Modifier.offset(x = 18.dp, y = 26.dp))
            WelcomeChip(
                title = "₹180 · Coffee", subtitle = "Saved in 4 seconds", rotation = 3f,
                background = c.surface, titleColor = c.ink, subtitleColor = c.muted, iconBackground = c.lime, iconTint = c.onLime,
                icon = LeftoverIcons.Check,
                modifier = Modifier.align(Alignment.TopEnd).offset(y = 196.dp),
            )
            WelcomeChip(
                title = "Phone goal", subtitle = "Halfway there", rotation = -2f,
                background = c.lavender, titleColor = c.lavenderInk, subtitleColor = c.lavenderMuted, iconBackground = c.surface, iconTint = c.lavenderMuted,
                icon = LeftoverIcons.Phone,
                modifier = Modifier.offset(y = 262.dp),
            )
        }

        Column(
            Modifier.padding(top = 18.dp).graphicsLayer {
                alpha = progress(1)
                translationY = (1f - progress(1)) * 18.dp.toPx()
            },
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Know what you can spend.", style = Type.display36, color = c.ink)
            Text("Track it in seconds. Save for the things that matter. No bank login needed to start.", style = Type.body, color = c.muted)
        }

        Spacer(Modifier.weight(1f))

        Column(
            Modifier.graphicsLayer {
                alpha = progress(3)
                translationY = (1f - progress(3)) * 26.dp.toPx()
            },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LButton("Get started", onGetStarted, icon = LeftoverIcons.Arrow, enabled = enabled)
            LButton("I've used Leftover before — restore", onRestore, kind = ButtonKind.Quiet, enabled = enabled, height = 48.dp)
            if (restoreError != null) {
                Text(restoreError, style = Type.caption, color = c.emberText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clip(Radius.md).background(c.emberBg).padding(12.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(LeftoverIcons.Lock, null, tint = c.muted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Your data stays on this phone.", style = Type.small, color = c.muted)
            }
        }
    }
}

@Composable
private fun WelcomeHeroCard(modifier: Modifier = Modifier) {
    Column(
        modifier
            .width(300.dp)
            .graphicsLayer { rotationZ = -4f }
            .clip(Radius.xl)
            .background(Color(0xFF20251B))
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Left to spend · 3 days to payday", style = Type.caption, color = Color(0xFFB9BDB0))
        Text("₹4,280", style = Type.display48, color = Color(0xFFD6F478))
        Row(Modifier.fillMaxWidth().height(10.dp).clip(Radius.pill), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf(0.46f to 0xFF6B7064, 0.14f to 0xFFE4DDFB, 0.14f to 0xFFB9BDB0, 0.26f to 0xFFD6F478).forEach { (weight, color) ->
                Box(Modifier.weight(weight).height(10.dp).background(Color(color)))
            }
        }
        Text("About ₹1,426 a day", style = Type.caption, color = Color(0xFFB9BDB0))
    }
}

@Composable
private fun WelcomeChip(
    title: String, subtitle: String, rotation: Float, background: Color, titleColor: Color, subtitleColor: Color,
    iconBackground: Color, iconTint: Color, icon: ImageVector, modifier: Modifier = Modifier,
) {
    Row(
        modifier.graphicsLayer { rotationZ = rotation }.clip(Radius.md).background(background).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(34.dp).clip(Radius.sm).background(iconBackground), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Column {
            Text(title, style = Type.bodyStrong, color = titleColor)
            Text(subtitle, style = Type.small, color = subtitleColor)
        }
    }
}
