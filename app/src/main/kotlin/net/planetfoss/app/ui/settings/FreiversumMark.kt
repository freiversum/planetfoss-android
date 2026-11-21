package net.planetfoss.app.ui.settings

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.planetfoss.app.R

/*
 * The freiversum mark (size S, 16 grid, same geometry and colours as res/drawable/ic_freiversum.xml) with the
 * constellation animation of the websites' footer, played once instead of on hover: when the row becomes fully
 * visible and when it is pressed. The line draws itself, the four stars light up one after the other and breathe
 * once, a comet runs along the line and back, and two radar pulses go out from the violet star, which is PlanetFOSS
 * in the freiversum (FV 2). Then everything rests: at rest the row shows the vector drawable, and the canvas only
 * exists and redraws while the one-shot animation runs.
 * With "Remove animations" (animator duration scale 0) the mark stays still.
 */

private val Tile = Color(0xFF10121C)
private val LineRest = Color(0xFF5B6080)
private val LineLit = Color(0xFF9A9FC8)
private val Comet = Color(0xFFFBFCFF)
private val PulseColor = Color(0xFF8370EE)

/** The four stars in the order of the line: Linux und Ich, PlanetFOSS, LinuxPULSE, tuxsucht. */
private val Stars = listOf(
    Offset(3.5f, 12.5f) to Color(0xFFFF6600),
    Offset(6.5f, 6.5f) to Color(0xFF8370EE),
    Offset(10.5f, 9.5f) to Color(0xFFFFBC00),
    Offset(13.5f, 3.5f) to Color(0xFFEEF2F8),
)
private val PulseCenter = Stars[1].first

/** Breathing starts staggered like on the web (0.9 / 1.3 / 1.1 / 1.5 s). */
private val BreathStart = floatArrayOf(0.9f, 1.3f, 1.1f, 1.5f)

private const val DRAW_S = 0.7f
private const val TWINKLE_S = 0.55f
private const val TWINKLE_STEP_S = 0.2f
private const val BREATH_S = 1.6f
private const val COMET_START_S = 0.8f
private const val COMET_S = 2.4f
private const val COMET_LEN = 0.07f
private const val PULSE_START_S = 0.7f
private const val PULSE_S = 2.4f
private const val PULSE_GAP_S = 1.2f
private const val PULSE_R = 7f

/** One pass: comet there and back ends last. */
internal const val FREIVERSUM_ANIM_S = COMET_START_S + 2 * COMET_S

private val DrawEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

@Composable
internal fun FreiversumMark(interactionSource: InteractionSource, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val still = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    // Seconds into the one-shot animation; at the end value the mark looks exactly like the static drawable.
    val time = remember { Animatable(FREIVERSUM_ANIM_S) }
    val scope = rememberCoroutineScope()
    val play: () -> Unit = {
        if (!still && !time.isRunning) scope.launch {
            time.snapTo(0f)
            time.animateTo(FREIVERSUM_ANIM_S, tween((FREIVERSUM_ANIM_S * 1000).toInt(), easing = LinearEasing))
        }
    }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { if (it is PressInteraction.Press) play() }
    }
    Box(modifier.size(24.dp).onVisibilityChanged(minFractionVisible = 1f) { visible -> if (visible) play() }) {
        // At rest the vector drawable, as before; the canvas only exists while the animation runs.
        if (time.isRunning) {
            Canvas(Modifier.matchParentSize()) { drawFreiversum(time.value) }
        } else {
            Image(painterResource(R.drawable.ic_freiversum), null, Modifier.matchParentSize())
        }
    }
}

private fun phase(t: Float, start: Float, duration: Float): Float = ((t - start) / duration).coerceIn(0f, 1f)

/** Draws the mark at [t] seconds into the animation, in the 16 grid scaled to the canvas. */
internal fun DrawScope.drawFreiversum(t: Float) {
    val u = size.minDimension / 16f
    val done = t >= FREIVERSUM_ANIM_S
    drawRoundRect(Tile, size = Size(16f * u, 16f * u), cornerRadius = CornerRadius(3f * u))
    val pts = listOf(Offset(3.5f, 12.5f), Offset(6.5f, 6.5f), Offset(10.5f, 9.5f), Offset(13.5f, 3.5f))
    val line = Path().apply {
        moveTo(pts[0].x * u, pts[0].y * u)
        pts.drop(1).forEach { lineTo(it.x * u, it.y * u) }
    }
    if (done) {
        drawPath(line, LineRest, style = Stroke(width = u))
    } else {
        val measure = PathMeasure().apply { setPath(line, false) }
        val len = measure.length
        // Radar pulses under the line, like on the web.
        for (k in 0..1) {
            val p = phase(t, PULSE_START_S + k * PULSE_GAP_S, PULSE_S)
            if (p <= 0f || p >= 1f) continue
            val e = EaseOut.transform(p)
            val scale = 0.1f + 0.9f * e
            drawCircle(
                PulseColor.copy(alpha = 0.85f * (1f - e)),
                radius = PULSE_R * scale * u,
                center = PulseCenter * u,
                style = Stroke(width = 0.7f * scale * u),
            )
        }
        // The line draws itself and glows while the animation runs.
        val glow = (minOf(t, FREIVERSUM_ANIM_S - t) / 0.3f).coerceIn(0f, 1f)
        val drawn = Path()
        measure.getSegment(0f, DrawEasing.transform(phase(t, 0f, DRAW_S)) * len, drawn, true)
        drawPath(drawn, lerp(LineRest, LineLit, glow), style = Stroke(width = u))
        // The comet runs to the end of the line and back.
        if (t > COMET_START_S) {
            val c = (t - COMET_START_S) / COMET_S
            val s = EaseInOut.transform(if (c <= 1f) c else 2f - c) * (1f + COMET_LEN)
            val seg = Path()
            measure.getSegment((s - COMET_LEN).coerceIn(0f, 1f) * len, s.coerceIn(0f, 1f) * len, seg, true)
            val fade = (minOf(t - COMET_START_S, FREIVERSUM_ANIM_S - t) / 0.2f).coerceIn(0f, 1f)
            drawPath(seg, Comet.copy(alpha = 0.9f * fade), style = Stroke(width = 1.4f * u, cap = StrokeCap.Round))
        }
    }
    Stars.forEachIndexed { i, (center, color) ->
        var scale = 1f
        var turn = 0f
        if (!done) {
            val tw = phase(t, i * TWINKLE_STEP_S, TWINKLE_S)
            if (tw > 0f && tw < 1f) {
                // Up to 1.7 and 45 degrees in the middle, back to rest (a plus turned by 90 degrees looks the same).
                val e = EaseOut.transform(tw)
                val peak = 1f - kotlin.math.abs(1f - 2f * e)
                scale = 1f + 0.7f * peak
                turn = 90f * e
            }
            val b = (t - BreathStart[i]) / BREATH_S
            if (b > 0f && b < 2f) scale *= 1f + 0.2f * FastOutSlowInEasing.transform(if (b <= 1f) b else 2f - b)
        }
        drawStar(center * u, u, color, scale, turn)
    }
}

private fun DrawScope.drawStar(center: Offset, u: Float, color: Color, scale: Float, turn: Float) {
    withTransform({
        rotate(turn, center)
        scale(scale, scale, center)
    }) {
        drawRect(color, topLeft = center - Offset(1.5f * u, 0.5f * u), size = Size(3f * u, u))
        drawRect(color, topLeft = center - Offset(0.5f * u, 1.5f * u), size = Size(u, 3f * u))
    }
}
