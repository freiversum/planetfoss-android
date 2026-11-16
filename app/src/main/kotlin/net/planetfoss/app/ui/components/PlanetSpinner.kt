// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TAU = (2 * PI).toFloat()

/**
 * The planet "o" of the logo as loading indicator (mockup variant A, 2026-11-07): while pulling, the yellow moon is
 * dragged around the planet by [progress]; while [refreshing] it keeps orbiting.
 */
@Composable
fun PlanetSpinner(progress: Float, refreshing: Boolean, modifier: Modifier = Modifier) {
    val p = progress.coerceIn(0f, 1f)
    // Armed (pulled past the threshold) or loading: the moon orbits on its own. It continues where the pull left it.
    if (refreshing || p >= 1f) {
        val spin by rememberInfiniteTransition(label = "planet").animateFloat(
            initialValue = 0f,
            targetValue = TAU,
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
            label = "moon",
        )
        PlanetCanvas(MoonStart + spin, 1f, modifier)
    } else {
        PlanetCanvas(MoonStart + p * TAU, p, modifier)
    }
}

/** Where the moon starts: in front of the planet, so it is seen from the first pixel of the pull. */
private const val MoonStart = 0.9f

private val RingColors = listOf(Color(0xFF6E5BEB), Color(0xFF5EE4DF), Color(0xFF7FE9DA))
private val PlanetColors = listOf(Color(0xFFC4B8FF), Color(0xFF5B3DF5))
private val MoonColor = Color(0xFFFCC954)
private val Center = Offset(32f, 34f)
private const val Tilt = -22f
private const val StrokeW = 4.2f
private const val Ry = 8.5f

private fun ringPath(front: Boolean) = Path().apply {
    val oval = Rect(6f, 34f - Ry, 58f, 34f + Ry)
    if (front) {
        moveTo(58f, 34f); arcTo(oval, 0f, 180f, false)
    } else {
        moveTo(6f, 34f); arcTo(oval, 180f, 180f, false)
    }
}

@Composable
private fun PlanetCanvas(moonAngle: Float, pull: Float, modifier: Modifier) {
    Canvas(modifier.size(56.dp, 38.dp).graphicsLayer { alpha = 0.35f + 0.65f * pull }) {
        val grow = 0.7f + 0.3f * pull
        val u = size.width / 56f
        withTransform({
            scale(u, u, Offset.Zero)
            translate(-4f, -15f)
            scale(grow, grow, Center)
        }) { drawPlanet(moonAngle) }
    }
}

private fun DrawScope.drawPlanet(moonAngle: Float) {
    val ringBrush = Brush.linearGradient(RingColors, start = Offset(6f, 34f), end = Offset(58f, 34f))
    val planetBrush = Brush.linearGradient(PlanetColors, start = Offset(17f, 19f), end = Offset(47f, 49f))
    val ringStyle = Stroke(width = StrokeW, cap = StrokeCap.Round)
    val disc = Path().apply { addOval(Rect(Center, 19.5f)) }
    val moonX = 32f + 26f * cos(moonAngle)
    val moonY = 34f + Ry * sin(moonAngle)
    val moonBehind = moonY < 34f
    val moon = Offset(moonX, moonY)

    // Back half of the ring and a moon behind the planet.
    rotate(Tilt, Center) {
        clipPath(disc, ClipOp.Difference) {
            drawPath(ringPath(false), ringBrush, style = ringStyle)
            if (moonBehind) drawCircle(MoonColor, 3.6f, moon)
        }
    }
    // Planet, with a gap cut where the front of the ring passes.
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(-100f, -100f, 200f, 200f), Paint())
        drawCircle(planetBrush, 15f, Center, style = ringStyle)
        rotate(Tilt, Center) {
            drawPath(ringPath(true), Color.Black, style = Stroke(width = 9f, cap = StrokeCap.Round), blendMode = BlendMode.Clear)
        }
        canvas.restore()
    }
    // Front half of the ring and a moon in front of the planet.
    rotate(Tilt, Center) {
        drawPath(ringPath(true), ringBrush, style = ringStyle)
        if (!moonBehind) drawCircle(MoonColor, 3.6f, moon)
    }
}
