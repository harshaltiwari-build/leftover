package app.leftover.ui.launch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/** Geometry of the Leftover mark (an L holding a coin) on the 18–90 safe area of the 108 launcher grid. */
internal object MarkGeometry {
    val lime = Color(0xFFD3F67A)
    val charcoal = Color(0xFF20251B)

    private const val L_PATH =
        "M37.2,29.5 L45.4,29.5 A5,5 0 0 1 50.4,34.5 L50.4,57.6 L71.1,57.6 A4.5,4.5 0 0 1 75.6,62.1 L75.6,64.5 " +
            "A14,14 0 0 1 61.6,78.5 L45.7,78.5 A14,14 0 0 1 31.7,64.5 L31.7,35 A5.5,5.5 0 0 1 37.2,29.5 Z"

    val glyph: Path = PathParser().parsePathString(L_PATH).toPath()
    val coinCenter = Offset(65.3f, 46.4f)
    const val COIN_RADIUS = 8.4f
    const val STEM_TOP = 29.5f
    const val STEM_BOTTOM = 57.6f
    const val BASE_LEFT = 31.7f
    const val BASE_RIGHT = 75.6f
    const val ORIGIN = 18f
    const val SIZE = 72f
    const val COIN_START_OFFSET = -90f
    const val CORNER_FRACTION = 0.26f
}

/**
 * The animated app mark.
 * [tile] scales the lime tile, [stem] and [base] (0..1) draw the L, [coinDrop] moves the coin from above (0) to landed (1),
 * and [squash] (−1..1) is the small landing bump: positive squashes the tile, negative stretches it.
 */
@Composable
fun LeftoverMark(
    size: Dp,
    modifier: Modifier = Modifier,
    tile: Float = 1f,
    stem: Float = 1f,
    base: Float = 1f,
    coinDrop: Float = 1f,
    squash: Float = 0f,
) {
    Canvas(modifier.size(size)) {
        val side = this.size.width
        val unit = side / MarkGeometry.SIZE
        val squashed = squash.coerceAtLeast(0f)
        val stretched = (-squash).coerceAtLeast(0f)
        scale(
            scaleX = tile * (1f - 0.06f * squashed + 0.05f * stretched),
            scaleY = tile * (1f - 0.03f * squashed + 0.05f * stretched),
            pivot = Offset(side / 2f, side),
        ) {
            val corner = side * MarkGeometry.CORNER_FRACTION
            drawRoundRect(MarkGeometry.lime, size = this.size, cornerRadius = CornerRadius(corner, corner))
            val tilePath = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, this@scale.size), CornerRadius(corner, corner))) }
            clipPath(tilePath) {
                scale(unit, unit, pivot = Offset.Zero) {
                    translate(-MarkGeometry.ORIGIN, -MarkGeometry.ORIGIN) {
                        val stemBottom = MarkGeometry.STEM_TOP + (MarkGeometry.STEM_BOTTOM - MarkGeometry.STEM_TOP) * stem
                        clipRect(0f, 0f, 200f, stemBottom) { drawPath(MarkGeometry.glyph, MarkGeometry.charcoal) }
                        if (base > 0f) {
                            val right = MarkGeometry.BASE_LEFT + (MarkGeometry.BASE_RIGHT - MarkGeometry.BASE_LEFT) * base
                            clipRect(0f, MarkGeometry.STEM_BOTTOM, right, 200f) { drawPath(MarkGeometry.glyph, MarkGeometry.charcoal) }
                        }
                        if (coinDrop > 0f) {
                            val fall = (1f - coinDrop) * MarkGeometry.COIN_START_OFFSET
                            drawCircle(MarkGeometry.charcoal, MarkGeometry.COIN_RADIUS, MarkGeometry.coinCenter + Offset(0f, fall))
                        }
                    }
                }
            }
        }
    }
}
