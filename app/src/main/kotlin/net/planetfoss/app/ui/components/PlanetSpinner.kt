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
import androidx.compose.ui.graphics.PathFillType
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
 * The PlanetFOSS mark (brand 2026-11: filled planet, tilted ring, yellow spark) as loading indicator: while pulling,
 * the spark is dragged along the ring by [progress]; while [refreshing] it keeps orbiting the planet.
 */
@Composable
fun PlanetSpinner(progress: Float, refreshing: Boolean, modifier: Modifier = Modifier) {
    val p = progress.coerceIn(0f, 1f)
    // Armed (pulled past the threshold) or loading: the spark orbits on its own. It continues where the pull left it.
    if (refreshing || p >= 1f) {
        val spin by rememberInfiniteTransition(label = "planet").animateFloat(
            initialValue = 0f,
            targetValue = TAU,
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
            label = "spark",
        )
        PlanetCanvas(SparkStart + spin, 1f, modifier)
    } else {
        PlanetCanvas(SparkStart + p * TAU, p, modifier)
    }
}

/** Where the spark starts: on the front of the ring, so it is seen from the first pixel of the pull. */
private const val SparkStart = 0.9f

// Geometry and colours in planet units (radius 1), taken from ci/system/out/bildmarke.svg of planetfoss.net.
private val PlanetBrush = Brush.linearGradient(
    0f to Color(0xFFDAD0FA), 0.42f to Color(0xFF8370EE), 0.86f to Color(0xFF3A30CE),
    start = Offset(-0.75f, -0.85f), end = Offset(0.7f, 0.95f),
)
private val RingBrush = Brush.linearGradient(
    0f to Color(0xFF6A55E8), 0.42f to Color(0xFF4FC9E6), 0.62f to Color(0xFF45E0D2),
    start = Offset(-RX, 0f), end = Offset(RX, 0f),
)
private val SparkColor = Color(0xFFFCC954)
private const val RX = 1.66f
private const val RY = 0.42f
private const val Tilt = -18f
private const val RingW = 0.15f
private const val GapW = 0.27f
private const val BackGap = 1.06f
private const val SparkR = 0.3f

/** The visible part of the ring in front of the planet (lower half) or behind it (upper half). */
private fun ringPath(front: Boolean) = Path().apply {
    val oval = Rect(-RX, -RY, RX, RY)
    if (front) {
        moveTo(RX, 0f); arcTo(oval, 0f, 180f, false)
    } else {
        moveTo(-RX, 0f); arcTo(oval, 180f, 180f, false)
    }
}

/** Soft light rim on the upper left of the planet. */
private val Rim = Path().apply {
    fillType = PathFillType.EvenOdd
    addOval(Rect(Offset.Zero, 1f))
    addOval(Rect(Offset(-0.06f, 0.07f), 0.95f))
}

/** Four-pointed spark, centred on the origin. */
private fun sparkPath(r: Float) = Path().apply {
    val c = r * 0.09f
    moveTo(0f, -r)
    quadraticTo(c, -c, r, 0f)
    quadraticTo(c, c, 0f, r)
    quadraticTo(-c, c, -r, 0f)
    quadraticTo(-c, -c, 0f, -r)
    close()
}

@Composable
private fun PlanetCanvas(sparkAngle: Float, pull: Float, modifier: Modifier) {
    Canvas(modifier.size(56.dp, 38.dp).graphicsLayer { alpha = 0.35f + 0.65f * pull }) {
        val grow = 0.7f + 0.3f * pull
        // 4.0 x 2.7 planet units fit the canvas: the ring plus the spark on its far ends.
        val u = size.width / 4.0f * grow
        withTransform({
            translate(size.width / 2, size.height / 2)
            scale(u, u, Offset.Zero)
        }) { drawPlanet(sparkAngle) }
    }
}

private fun DrawScope.drawPlanet(sparkAngle: Float) {
    val ringStyle = Stroke(width = RingW)
    // Spark position on the ring, in planet coordinates (ring frame: shifted down 0.05, tilted by Tilt degrees).
    val ex = RX * cos(sparkAngle)
    val ey = RY * sin(sparkAngle)
    val t = Tilt * PI.toFloat() / 180f
    val spark = Offset(ex * cos(t) - ey * sin(t), ex * sin(t) + ey * cos(t) + 0.05f)
    val sparkInFront = ey > 0f
    val sparkShape = sparkPath(SparkR)

    fun ring(block: DrawScope.() -> Unit) = translate(0f, 0.05f) { rotate(Tilt, Offset.Zero) { block() } }
    fun drawSpark() = translate(spark.x, spark.y) {
        rotate(sparkAngle * 90f / PI.toFloat(), Offset.Zero) { drawPath(sparkShape, SparkColor) }
    }

    // Back half of the ring, kept clear of the planet, and a spark behind it (the planet covers it).
    clipPath(Path().apply { addOval(Rect(Offset.Zero, BackGap)) }, ClipOp.Difference) {
        ring { drawPath(ringPath(false), RingBrush, style = ringStyle) }
    }
    if (!sparkInFront) drawSpark()
    // Planet with its rim light, with a gap cut where the front of the ring passes.
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(-3f, -3f, 3f, 3f), Paint())
        drawCircle(PlanetBrush, 1f, Offset.Zero)
        clipPath(Path().apply { addOval(Rect(Offset.Zero, 1f)) }) { drawPath(Rim, Color.White.copy(alpha = 0.14f)) }
        ring { drawPath(ringPath(true), Color.Black, style = Stroke(width = GapW, cap = StrokeCap.Butt), blendMode = BlendMode.Clear) }
        canvas.restore()
    }
    // Front half of the ring and a spark in front of the planet.
    ring { drawPath(ringPath(true), RingBrush, style = ringStyle) }
    if (sparkInFront) drawSpark()
}
