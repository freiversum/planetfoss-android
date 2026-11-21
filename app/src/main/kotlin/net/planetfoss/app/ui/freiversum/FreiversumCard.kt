// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.freiversum

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.planetfoss.app.R
import net.planetfoss.app.ui.components.openOutsideApp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 * "Sternwarte": the freiversum card at the end of Settings (concept: freiversum.de/docs/app-fuss/index.html).
 * Always dark, a window into the night sky of freiversum.de with PlanetFOSS as the marked star (FV 2).
 *
 * - Intro once, when the card is fully visible for the first time after opening the screen, and from the play
 *   icon: the line draws itself, the stars light up in turn, the radar beam sweeps once, two pulses go out from the
 *   own star, a comet runs along the constellation. One timeline (an Animatable in seconds); afterwards the canvas
 *   shows a still picture and nothing runs.
 * - Tap a star: it flares, a sign shows name and role for 2.5 s, a haptic tick. The own star three times quickly
 *   calls the UFO ([UfoController]).
 * - Swipe sideways over the sky: a shooting star follows the finger and burns out after release. Vertical moves
 *   are left to the scrolling page.
 * - Long press on the sky (0.6 s): three radar pulses from the finger, then the UFO comes to it.
 * - "Remove animations" (animator duration scale 0): the end state at once, the sign without flare, no shooting
 *   star, no UFO.
 * - TalkBack: the card is one unit with the link as its action; the sky and the play icon are hidden.
 */

private const val URL = "https://freiversum.de/"

private val Oxanium = FontFamily(Font(R.font.oxanium_semibold, FontWeight.SemiBold))
private val AtkinsonMono = FontFamily(Font(R.font.atkinson_hyperlegible_mono_regular, FontWeight.Normal))

private val Ease = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)          // --ease of freiversum.de
private val SweepEase = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

// Timeline of the intro in seconds (concept, section "Auftritt")
private const val LINE_START = 0.15f
private const val LINE_S = 1f
private const val STAR_START = 0.25f
private const val STAR_STEP = 0.22f
private const val STAR_S = 0.6f
private const val SWEEP_START = 0.2f
private const val SWEEP_S = 2.6f
private const val PULSE_START = 1.1f
private const val PULSE_GAP = 1.2f
private const val PULSE_S = 2.4f
private const val COMET_START = 1.4f
private const val COMET_S = 2f
private const val BLINK_S = 1.4f
/** The last far star stops blinking at 1.5 + 2 × 1.4 s. */
internal const val INTRO_END = 4.35f

private const val LONG_PRESS_MS = 600L
private const val SIGN_MS = 2500L
private const val TRIPLE_TAP_MS = 700L

private fun phase(t: Float, start: Float, duration: Float) = ((t - start) / duration).coerceIn(0f, 1f)

@Composable
internal fun FreiversumCard(ufo: UfoController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val still = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val intro = remember { Animatable(INTRO_END) }
    var played by rememberSaveable { mutableStateOf(false) }
    val play: () -> Unit = {
        if (!still) scope.launch {
            intro.snapTo(0f)
            intro.animateTo(INTRO_END, tween((INTRO_END * 1000).toInt(), easing = LinearEasing))
        }
    }
    val title = stringResource(R.string.freiversum_title)
    val text = stringResource(R.string.freiversum_text)
    val visit = stringResource(R.string.freiversum_visit)
    val dark = MaterialTheme.colorScheme.background.luminanceIsDark()

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Ink, contentColor = Text1),
        border = BorderStroke(1.dp, Edge),
        elevation = CardDefaults.cardElevation(defaultElevation = if (dark) 0.dp else 4.dp),
        modifier = modifier
            .onVisibilityChanged(minFractionVisible = 1f) { visible ->
                if (visible && !played) { played = true; play() }
            }
            // one unit for TalkBack: title and text, the link as the action; the playful parts stay silent
            .clearAndSetSemantics {
                contentDescription = "$title. $text"
                onClick(label = visit) { openOutsideApp(context, URL); true }
            },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).background(OwnStar.color, CircleShape))
            Spacer(Modifier.width(7.dp))
            Text(
                "${OwnStar.catalogue} · planetfoss.net",
                style = TextStyle(fontFamily = AtkinsonMono, fontSize = 11.5.sp, letterSpacing = 0.02.em, color = Text3),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = play, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.freiversum_play), tint = Text3, modifier = Modifier.size(20.dp))
            }
        }
        Sky(intro.value, still, ufo, haptics::performHapticFeedback)
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 18.dp)) {
            Text(title, style = TextStyle(fontFamily = Oxanium, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 25.sp, letterSpacing = (-0.01).em, color = Text1))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp, color = Text2),
                modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
            )
            OutlinedButton(
                onClick = { openOutsideApp(context, URL) },
                border = BorderStroke(1.dp, Line2),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Text1),
            ) {
                Text(visit)
                Spacer(Modifier.width(8.dp))
                Icon(painterResource(R.drawable.ic_open_in_new), null, Modifier.size(18.dp))
            }
        }
    }
}

private fun Color.luminanceIsDark() = (0.2126f * red + 0.7152f * green + 0.0722f * blue) < 0.5f

/** The sky with its gestures. [t] is the time on the intro timeline (at rest: [INTRO_END]). */
@Composable
private fun Sky(t: Float, still: Boolean, ufo: UfoController, haptic: (HapticFeedbackType) -> Unit) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val slop = LocalViewConfiguration.current.touchSlop
    val measurer = rememberTextMeasurer()
    val bodyFont = MaterialTheme.typography.bodyLarge.fontFamily
    // tapped star: flare (0..1) and the sign
    val flare = remember { Animatable(1f) }
    var flareStar by remember { mutableIntStateOf(-1) }
    var sign by remember { mutableStateOf<FvStar?>(null) }
    var signNonce by remember { mutableLongStateOf(0L) }
    LaunchedEffect(signNonce) {
        if (sign != null) { delay(SIGN_MS); sign = null }
    }
    val roles = FvStars.map { stringResource(it.role) }
    // shooting star under the finger, in map units
    val meteor = remember { mutableStateListOf<Offset>() }
    val meteorFade = remember { Animatable(1f) }
    var ownTaps by remember { mutableIntStateOf(0) }
    var ownTapAt by remember { mutableLongStateOf(0L) }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val labelStyle = with(density) { TextStyle(fontFamily = AtkinsonMono, fontSize = 17f.toSp(), letterSpacing = 0.02.em) }
    val labels = remember(labelStyle) { FvStars.map { measurer.measure(it.catalogue, labelStyle) } }

    fun tapStar(i: Int) {
        val star = FvStars[i]
        haptic(HapticFeedbackType.SegmentTick)
        if (!still) {
            flareStar = i
            scope.launch { flare.snapTo(0f); flare.animateTo(1f, tween(600, easing = LinearEasing)) }
        }
        sign = star
        signNonce++
        if (star === OwnStar && !still) {
            val now = System.currentTimeMillis()
            ownTaps = if (now - ownTapAt < TRIPLE_TAP_MS) ownTaps + 1 else 1
            ownTapAt = now
            if (ownTaps >= 3) { ownTaps = 0; ufo.visit() }
        }
    }

    fun lure(local: Offset) {
        val root = coords?.takeIf { it.isAttached }?.localToRoot(local) ?: return
        scope.launch {
            repeat(3) { i ->
                ufo.pulse(root, i)
                haptic(HapticFeedbackType.SegmentTick)
                delay(420)
            }
            delay(280)
            ufo.lure(root)
        }
    }

    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(VB_W / VB_H)
            .onPlaced { coords = it }
            .clearAndSetSemantics { }
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .pointerInput(still) {
                val toMap = { p: Offset -> Offset(p.x / (size.width / VB_W) + VB_X, p.y / (size.width / VB_W) + VB_Y) }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    // 0 = tap, 1 = shooting star, 2 = page scroll, 3 = long press
                    var mode = 3
                    withTimeoutOrNull(LONG_PRESS_MS) {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) { mode = 0; break }
                            val d = change.position - start
                            if (abs(d.x) > slop && abs(d.x) > abs(d.y)) { mode = 1; break }
                            if (abs(d.y) > slop) { mode = 2; break }
                        }
                    }
                    when (mode) {
                        0 -> {
                            val p = toMap(start)
                            val hit = FvStars.indexOfFirst { (p - it.center).getDistance() < it.r + 30f }
                            if (hit >= 0) tapStar(hit)
                        }
                        1 -> if (!still) {
                            scope.launch { meteorFade.snapTo(1f) }
                            meteor.clear()
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                meteor.add(toMap(change.position))
                                if (meteor.size > 22) meteor.removeAt(0)
                            }
                            scope.launch {
                                meteorFade.animateTo(0f, tween(600, easing = EaseOut))
                                meteor.clear()
                            }
                        }
                        3 -> if (!still) {
                            lure(start)
                            // the finger stays down: keep the gesture so the page does not scroll under it
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                if (!change.pressed) break
                            }
                        }
                    }
                }
            },
    ) {
        val s = size.width / VB_W
        clipRect {
            withTransform({
                scale(s, s, Offset.Zero)
                translate(-VB_X, -VB_Y)
            }) {
                drawMaskedSky(t, s)
            }
            // the lower part of grid, background stars and beam fades out (mask m-aus of the concept)
            drawRect(
                Brush.verticalGradient(0.7f to Color.White, 1f to Color.White.copy(alpha = 0.25f)),
                blendMode = BlendMode.DstIn,
            )
            withTransform({
                scale(s, s, Offset.Zero)
                translate(-VB_X, -VB_Y)
            }) {
                drawConstellation(t, labels)
                drawFlareStars(t, if (flare.isRunning || flare.value < 1f) flareStar else -1, flare.value)
                sign?.let { st -> drawSign(st, roles[FvStars.indexOf(st)], measurer, density, bodyFont) }
                if (meteor.isNotEmpty()) drawMeteor(meteor, meteorFade.value)
            }
        }
    }
}

// ---------- drawing, all in map units ----------

/** Grid, background stars and the radar beam: the part under the fade-out mask. */
private fun DrawScope.drawMaskedSky(t: Float, s: Float) {
    val hair = 1f
    for (r in listOf(90f, 170f, 250f, 330f, 410f, 490f, 570f)) {
        drawCircle(if (r == 410f) GridMain else Grid, r, Pole, style = Stroke(hair))
    }
    for (i in 0 until 36) {
        val a = Math.toRadians((i * 10 - 170).toDouble())
        drawLine(
            if (i % 3 == 0) GridMain else Grid, Pole,
            Pole + Offset((cos(a) * 700).toFloat(), (sin(a) * 700).toFloat()), hair,
        )
    }
    val running = t < INTRO_END
    for (p in FarPoints) {
        var alpha = 1f
        if (running && p.blinks) {
            // 1.4 s ease-in-out to 0.2 and back (two alternate iterations)
            val k = (t - p.delay) / BLINK_S
            if (k > 0f && k < 2f) alpha = 1f - 0.8f * EaseInOut.transform(if (k <= 1f) k else 2f - k)
        }
        drawCircle((if (p.bright) FarStarBright else FarStar).copy(alpha = alpha), p.r, Offset(p.x, p.y))
    }
    // radar beam: a wedge from the pole that sweeps once from -75° to 75° and goes out
    val k = phase(t, SWEEP_START, SWEEP_S)
    if (running && k > 0f && k < 1f) {
        val opacity = when {
            k < 0.12f -> k / 0.12f
            k < 0.85f -> 1f - 0.2f * (k - 0.12f) / 0.73f
            else -> 0.8f * (1f - (k - 0.85f) / 0.15f)
        }
        val angle = -75f + 150f * SweepEase.transform(k)
        val wedge = Path().apply {
            moveTo(Pole.x, Pole.y)
            lineTo(Pole.x - 120f, Pole.y - 620f)
            quadraticTo(Pole.x - 40f, Pole.y - 632f, Pole.x + 40f, Pole.y - 620f)
            close()
        }
        withTransform({ rotate(angle, Pole) }) {
            drawPath(
                wedge,
                Brush.radialGradient(
                    0f to Text1.copy(alpha = 0f), 0.35f to Text1.copy(alpha = 0.10f), 1f to Text1.copy(alpha = 0f),
                    center = Pole, radius = 560f,
                ),
                alpha = opacity.coerceIn(0f, 1f),
            )
        }
    }
}

private val linePath = Path().apply {
    moveTo(FvStars[0].x, FvStars[0].y)
    FvStars.drop(1).forEach { lineTo(it.x, it.y) }
}
private val lineLength = PathMeasure().apply { setPath(linePath, false) }.length

/** Line, pulses at the own star, comet and the catalogue numbers. */
private fun DrawScope.drawConstellation(t: Float, labels: List<TextLayoutResult>) {
    val running = t < INTRO_END
    val drawn = if (running) Ease.transform(phase(t, LINE_START, LINE_S)) else 1f
    if (drawn > 0f) {
        val stroke = Stroke(2.4f, cap = StrokeCap.Round)
        if (drawn >= 1f) drawPath(linePath, ConstellationLine, style = stroke)
        else {
            val part = Path()
            PathMeasure().apply { setPath(linePath, false) }.getSegment(0f, drawn * lineLength, part, true)
            drawPath(part, ConstellationLine, style = stroke)
        }
    }
    if (running) {
        // two radar pulses: from 0.15 to full size while fading out within 75 % of 2.4 s
        for (i in 0..1) {
            val k = (t - PULSE_START - i * PULSE_GAP) / PULSE_S
            if (k <= 0f || k >= 0.75f) continue
            val e = EaseOut.transform(k / 0.75f)
            drawCircle(OwnStar.color.copy(alpha = 0.9f * (1f - e)), OwnStar.r * 3.4f * (0.15f + 0.85f * e), OwnStar.center, style = Stroke(2f))
        }
        drawComet(t)
    }
    FvStars.forEachIndexed { i, st ->
        val own = st === OwnStar
        if (own) {
            drawCircle(st.color.copy(alpha = 0.55f), st.r + 10f, st.center, style = Stroke(1.6f))
            drawCircle(st.color.copy(alpha = 0.3f), st.r + 17f, st.center, style = Stroke(1.6f))
        }
        val lbl = labels[i]
        val labelAlpha = if (running) phase(t, STAR_START + i * STAR_STEP, STAR_S * 0.6f) else 1f
        drawText(
            lbl, color = if (own) Text1 else Text3, alpha = labelAlpha,
            topLeft = Offset(st.x + st.r + 10f, st.y + 5f - lbl.firstBaseline),
        )
    }
}

private fun DrawScope.drawComet(t: Float) {
    val u = (t - COMET_START) / COMET_S
    if (u <= 0f || u >= 1f) return
    fun at(v: Float): Offset {
        val c = v.coerceIn(0f, 1f)
        val e = if (c < 0.5f) 2f * c * c else 1f - (-2f * c + 2f).pow(2) / 2f
        return alongLine(e)
    }
    val head = at(u)
    val opacity = when {
        u < 0.1f -> u * 10f
        u > 0.85f -> max(0f, (1f - u) / 0.15f)
        else -> 1f
    }
    val n = 14
    for (i in 0 until n) {
        // the positions of the last frames (oldest first), like the concept's tail of 14 dots
        val p = at(u - (n - i) * 0.008f)
        val k = i.toFloat() / n
        drawCircle(Color(0xFFCFD6FF).copy(alpha = 0.5f * k * opacity), 3f * k, p)
    }
    drawCircle(
        Brush.radialGradient(
            0f to Color(0xFFFBFCFF), 0.35f to Color(0xFFCFD6FF).copy(alpha = 0.55f), 1f to Color(0xFFCFD6FF).copy(alpha = 0f),
            center = head, radius = 16f,
        ),
        16f, head, alpha = opacity,
    )
    drawCircle(Color(0xFFFBFCFF), 3f, head, alpha = opacity)
}

/** The four twinkles: lighting up in turn during the intro, the tapped one flares. */
private fun DrawScope.drawFlareStars(t: Float, flareStar: Int, flare: Float) {
    val running = t < INTRO_END
    FvStars.forEachIndexed { i, st ->
        var alpha = 1f
        var scale = 1f
        var turn = 0f
        if (running) {
            val k = (t - STAR_START - i * STAR_STEP) / STAR_S
            when {
                k <= 0f -> alpha = 0f
                k < 0.6f -> {
                    val e = Ease.transform(k / 0.6f)
                    alpha = e; scale = 0.2f + 1.15f * e; turn = -45f + 53f * e
                }
                k < 1f -> {
                    val e = Ease.transform((k - 0.6f) / 0.4f)
                    scale = 1.35f - 0.35f * e; turn = 8f - 8f * e
                }
            }
        }
        if (i == flareStar && flare < 1f) {
            if (flare < 0.4f) {
                val e = Ease.transform(flare / 0.4f)
                scale *= 1f + 0.6f * e; turn += 45f * e
            } else {
                val e = Ease.transform((flare - 0.4f) / 0.6f)
                scale *= 1.6f - 0.6f * e; turn += 45f - 45f * e
            }
        }
        if (alpha <= 0f) return@forEachIndexed
        withTransform({
            rotate(turn, st.center)
            scale(scale, scale, st.center)
        }) {
            drawPath(twinkle(st.center, st.r), st.color, alpha = alpha.coerceIn(0f, 1f))
        }
    }
}

/** The sign of a tapped star: name, and catalogue number with role, on a raised chip. */
private fun DrawScope.drawSign(
    st: FvStar,
    role: String,
    measurer: androidx.compose.ui.text.TextMeasurer,
    density: androidx.compose.ui.unit.Density,
    bodyFont: FontFamily?,
) {
    val nameStyle = with(density) { TextStyle(fontFamily = bodyFont, fontWeight = FontWeight.SemiBold, fontSize = 24f.toSp(), color = Text1) }
    val roleStyle = with(density) { TextStyle(fontFamily = AtkinsonMono, fontSize = 18f.toSp(), color = Text2) }
    val name = measurer.measure(st.name, nameStyle)
    val line2 = measurer.measure("${st.catalogue} · $role", roleStyle)
    val w = max(name.size.width, line2.size.width) + 30f
    val x = min(st.x - w / 2f, VB_X + VB_W - w - 8f).coerceAtLeast(VB_X + 8f)
    val y = if (st.y > 250f) st.y - st.r - 84f else st.y + st.r + 16f
    drawRoundRect(Raised, Offset(x, y), Size(w, 68f), androidx.compose.ui.geometry.CornerRadius(14f))
    drawRoundRect(Line2, Offset(x, y), Size(w, 68f), androidx.compose.ui.geometry.CornerRadius(14f), style = Stroke(1f))
    drawText(name, topLeft = Offset(x + 15f, y + 30f - name.firstBaseline))
    drawText(line2, topLeft = Offset(x + 15f, y + 55f - line2.firstBaseline))
}

/** Shooting star: dots growing towards the head, a small twinkle at the finger. */
private fun DrawScope.drawMeteor(points: List<Offset>, fade: Float) {
    val n = points.size
    points.forEachIndexed { i, p ->
        val k = i.toFloat() / n
        drawCircle(Color(0xFFEEF3FF), 1f + k * 4f, p, alpha = (k * 0.8f * fade).coerceIn(0f, 1f))
    }
    drawPath(twinkle(points.last(), 9f, 0.12f), Color(0xFFFBFCFF), alpha = fade.coerceIn(0f, 1f))
}
