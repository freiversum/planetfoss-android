// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.freiversum

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/*
 * The UFO of freiversum.de (src/components/HimmelJs.astro), as a visitor over the whole Settings screen: the
 * same saucer with its five running rim lights, the dome and the faint beam below, the same steering (glide in
 * depth, swinging paths, brake thrusters, wobble and tilt), and the same escape: a short shake, then hyperdrive
 * towards the reader and out of the picture, with a light trail and speed lines. Obstacles, beaming and sprints
 * of the website are left out; the flight is short (about 6–8 s): it appears from the side or out of the depth,
 * visits two or three places, hovers, shakes and leaves. No sound.
 *
 * Everything is in map units like on the website; one unit is [UNIT_DP] dp. The layer draws above the screen,
 * lets every touch through and only exists while something flies. With "Remove animations" nothing comes.
 */

private const val UNIT_DP = 1.7f
private const val SHAKE_S = 0.5f      // website: 0.35 s; a little longer so the charging is seen on a phone
private const val TRAIL_S = 1.4f
private const val PULSE_S = 1.0f
private const val MAX_LIFE_S = 7f

private val OnLightInk = Color(0xFF5158C8)
private val OnLightEdge = Color(0xFF3B4152)

private fun rnd(a: Float, b: Float) = a + Random.nextFloat() * (b - a)

private class TrailPoint(val x: Float, val y: Float, val gr: Float, val t: Float)

private class Escape(
    val t0: Float, val x: Float, val y: Float, val dx: Float, val dy: Float, val gr: Float,
    val side: Float, val reach: Float, val grow: Float,
)

private class Saucer(var x: Float, var y: Float, var gr: Float, var vx: Float, var vy: Float, val t0: Float) {
    var targetX = 0f
    var targetY = 0f
    var targetGr = 1f
    var pause = true
    var wait = 0f
    var stopsLeft = 2
    var lured = false
    var thrustX = 0f
    var thrustY = 0f
    var bump = -9f
    val trail = ArrayDeque<TrailPoint>()
    var escape: Escape? = null
}

/** A radar pulse sent from the finger to call the UFO, in layer pixels. */
private class LurePulse(val at: Offset, val t0: Float, val index: Int)

/** What flies over the screen: the UFO and the lure pulses. Create with [rememberUfo], draw with [UfoLayer]. */
@Stable
internal class UfoController(private val still: Boolean) {
    private var frame by mutableLongStateOf(0L)
    var active by mutableStateOf(false)
        private set
    private var time = 0f
    private var saucer: Saucer? = null
    private val pulses = mutableListOf<LurePulse>()
    private var origin = Offset.Zero   // the layer's position in the root, px
    private var unit = 1f              // px per map unit
    private var w = 0f                 // layer size in units
    private var h = 0f
    private var pendingVisit = false

    /**
     * The screen below is light: the website's UFO flies in a dark sky, its pale blue and white light would vanish
     * on a light page. Then the light parts are drawn in a blue-violet that also shows on the dark card and the rim gets a fine dark edge.
     */
    var onLight = false

    private fun ink(c: Color): Color = if (onLight) lerp(c, OnLightInk, 0.62f).copy(alpha = c.alpha) else c
    private var pendingLure: Offset? = null   // root px

    /** The UFO comes by on its own (World UFO Day, the own star tapped three times). */
    fun visit() {
        if (still) return
        if (saucer == null) pendingVisit = true
        active = true
    }

    /** A radar pulse from [root] (root px), the [index]th of the lure call. */
    fun pulse(root: Offset, index: Int) {
        if (still) return
        pulses += LurePulse(root - origin, time, index)
        active = true
    }

    /** After the third pulse: the UFO comes to [root]; is it already there, it comes very close. */
    fun lure(root: Offset) {
        if (still) return
        pendingLure = root
        active = true
    }

    internal fun place(rootPos: Offset, size: Size, density: Float) {
        origin = rootPos
        unit = UNIT_DP * density
        w = size.width / unit
        h = size.height / unit
    }

    internal fun step(dt: Float) {
        time += dt
        if (w > 0f) {
            if (pendingVisit) { pendingVisit = false; if (saucer == null) saucer = newSaucer(lured = false) }
            pendingLure?.let { root ->
                pendingLure = null
                val p = (root - origin) / unit
                val s = saucer?.takeIf { it.escape == null } ?: newSaucer(lured = true).also { saucer = it }
                s.lured = true
                s.stopsLeft = 1
                s.targetX = p.x; s.targetY = p.y; s.targetGr = 1.6f
                s.pause = true; s.wait = 0f
            }
        }
        saucer?.let { s ->
            move(s, dt)
            val e = s.escape
            if (e == null && time - s.t0 > MAX_LIFE_S) flee(s)
            if (e != null && time - e.t0 > SHAKE_S + TRAIL_S) saucer = null
        }
        pulses.removeAll { time - it.t0 > PULSE_S }
        active = saucer != null || pulses.isNotEmpty() || pendingVisit || pendingLure != null
        frame++
    }

    // ---------- flight ----------

    private fun newSaucer(lured: Boolean): Saucer {
        val fromSide = Random.nextBoolean()
        val right = Random.nextBoolean()
        val s = if (fromSide) {
            Saucer(if (right) w + 30f else -30f, rnd(h * 0.12f, h * 0.4f), 0.9f, if (right) -60f else 60f, 0f, time)
        } else {
            Saucer(rnd(60f, w - 60f), rnd(h * 0.1f, h * 0.35f), 0.06f, 0f, 0f, time)
        }
        s.lured = lured
        s.stopsLeft = if (lured) 1 else 2 + Random.nextInt(2)
        newTarget(s)
        if (!fromSide) s.targetGr = max(s.targetGr, 1f)   // out of the depth: first come properly close
        return s
    }

    private fun newTarget(s: Saucer) {
        var x = 0f
        var y = 0f
        for (i in 0 until 40) {
            x = rnd(40f, w - 40f)
            y = rnd(0.14f * h + 20f, 0.78f * h)
            if (hypot(x - s.x, y - s.y) > 0.35f * min(w, h)) break
        }
        s.targetX = x; s.targetY = y
        s.targetGr = 0.45f * (1.5f / 0.45f).pow(Random.nextFloat()) + 0.25f
        // the last stop always hovers; the others hover or fly through with swing
        s.pause = s.stopsLeft <= 1 || Random.nextFloat() < 0.55f
    }

    /** One step of the steering of ufoBewegen on freiversum.de, without obstacles. */
    private fun move(s: Saucer, dt: Float) {
        if (s.escape != null || dt <= 0f) return
        s.gr = exp(ln(s.gr) + (ln(s.targetGr) - ln(s.gr)) * min(1f, dt * 0.7f))
        val tempo = 80f * min(1.6f, max(0.5f, s.gr))
        val age = time - s.t0
        val dx = s.targetX - s.x
        val dy = s.targetY - s.y
        val d = max(0.01f, hypot(dx, dy))
        var ax = 0f
        var ay = 0f
        if (s.wait > 0f) {
            s.wait -= dt
            if (s.wait <= 0f) {
                if (s.stopsLeft <= 0) { flee(s); return }
                newTarget(s)
            }
        } else if (d < (if (s.pause) 22f else 35f)) {
            s.stopsLeft--
            if (s.pause) s.wait = if (s.lured) 1.4f else rnd(0.5f, 1.2f)
            else if (s.stopsLeft <= 0) { flee(s); return } else newTarget(s)
        } else {
            val want = tempo * (if (s.pause) min(1f, d / 70f) else 1f)
            ax += ((dx / d) * want - s.vx) * 1.6f
            ay += ((dy / d) * want - s.vy) * 1.6f
        }
        if (s.wait > 0f) { ax -= s.vx; ay -= s.vy }   // roll out while hovering
        val nearK = min(1.4f, max(0.5f, s.gr)) * (if (s.wait > 0f) 0.35f else if (s.pause) min(1f, 0.3f + d / 120f) else 1f)
        ax += (sin(age * 0.83f + 1.3f) * 34f + sin(age * 2.1f) * 12f) * nearK
        ay += (sin(age * 0.61f + 0.4f) * 26f + cos(age * 1.7f) * 10f) * nearK
        val edge = 30f
        if (s.x < edge && s.x > -10f) ax += (edge - s.x) * 4f
        if (s.x > w - edge && s.x < w + 10f) ax -= (s.x - w + edge) * 4f
        if (s.y < edge) ay += (edge - s.y) * 4f
        if (s.y > h - edge) ay -= (s.y - h + edge) * 4f
        val smooth = min(1f, dt * 9f)
        s.thrustX += (ax - s.thrustX) * smooth
        s.thrustY += (ay - s.thrustY) * smooth
        s.vx += ax * dt
        s.vy += ay * dt
        val v = hypot(s.vx, s.vy)
        val vmax = tempo * 1.4f
        if (v > vmax) { s.vx *= vmax / v; s.vy *= vmax / v }
        s.x += s.vx * dt
        s.y += s.vy * dt
    }

    private class Pose(val age: Float, val x: Float, val y: Float, val gr: Float, val tilt: Float)

    /** Place and attitude with the wobble (ufoOrt): up and down, sideways, tilted into the flight. */
    private fun pose(s: Saucer): Pose {
        val age = time - s.t0
        val near = min(1.6f, s.gr)
        val bob = (sin(age * 2.1f) * 9f + sin(age * 5.3f) * 2.5f) * near
        val side = sin(age * 3.7f) * 3f * near
        val afterBump = max(0f, 1f - (time - s.bump) / 1.2f)
        val tilt = sin(age * 3.1f) * 0.22f + sin(age * 9.5f) * 0.05f +
            max(-0.3f, min(0.3f, s.vx / 220f)) + sin((time - s.bump) * 18f) * 0.35f * afterBump
        return Pose(age, s.x + side, s.y + bob, s.gr, tilt)
    }

    /** ufoFlucht: towards the farthest corner (±20°), or nearly head-on when it is about in the middle. */
    private fun flee(s: Saucer) {
        if (s.escape != null) return
        val o = pose(s)
        val bottom = 0.92f * h
        val mx = w / 2
        val my = bottom / 2
        val middle = hypot((o.x - mx) / (w / 2), (o.y - my) / (bottom / 2)) < rnd(0.15f, 0.3f)
        val angle = if (middle) Random.nextFloat() * 2f * PI.toFloat() else {
            var ex = 0f
            var ey = 0f
            var far = -1f
            for ((cx, cy) in listOf(0f to 0f, w to 0f, 0f to bottom, w to bottom)) {
                val e = hypot(cx - o.x, cy - o.y)
                if (e > far) { far = e; ex = cx; ey = cy }
            }
            atan2(ey - o.y, ex - o.x) + (Random.nextFloat() - 0.5f) * 0.7f
        }
        val rx = cos(angle)
        val ry = sin(angle)
        var out = Float.POSITIVE_INFINITY
        if (rx > 0.01f) out = min(out, (w - o.x) / rx) else if (rx < -0.01f) out = min(out, -o.x / rx)
        if (ry > 0.01f) out = min(out, (h - o.y) / ry) else if (ry < -0.01f) out = min(out, -o.y / ry)
        val reach = max(0f, out) + 120f * max(1f, o.gr)
        s.escape = Escape(time, o.x, o.y, rx, ry, o.gr, if (middle) rnd(0.06f, 0.2f) else rnd(0.85f, 1.15f), reach, rnd(0.7f, 1.4f))
    }

    // ---------- drawing ----------

    internal fun DrawScope.drawAll() {
        @Suppress("UNUSED_VARIABLE") val f = frame   // read: redraw on every step
        for (p in pulses) drawPulse(p)
        val s = saucer ?: return
        withTransform({ scale(unit, unit, Offset.Zero) }) { drawSaucerFlight(s) }
    }

    private fun DrawScope.drawPulse(p: LurePulse) {
        val k = ((time - p.t0) / PULSE_S).coerceIn(0f, 1f)
        val e = 1f - (1f - k).pow(3)
        val color = if (p.index % 2 == 1) Color(0xFF8370EE) else ink(Color(0xFFEEF0F5))
        val r = (6f + 74f * e) * unit
        drawCircle(color.copy(alpha = 0.9f * (1f - k)), r, p.at, style = Stroke(width = 2.2f * unit * (1f - 0.5f * k)))
        drawCircle(color.copy(alpha = 0.25f * (1f - k)), r * 0.62f, p.at, style = Stroke(width = 1.2f * unit))
        if (k < 0.3f) drawCircle(color.copy(alpha = 0.5f * (1f - k / 0.3f)), 5f * unit, p.at)
    }

    private fun DrawScope.glow(x: Float, y: Float, r: Float, a: Float) {
        if (r <= 0f || a <= 0f) return
        drawCircle(
            Brush.radialGradient(listOf(ink(Text1).copy(alpha = a.coerceIn(0f, 1f)), ink(Text1).copy(alpha = 0f)), Offset(x, y), r),
            r, Offset(x, y),
        )
    }

    /** zeichneSaucer: beam glow below, rim, dome and the five rim lights that run round, one flashing at a time. */
    private fun DrawScope.saucer(s: Float, shine: Float, lights: Float, alpha: Float) {
        val beamA = ((0.18f + 0.14f * shine) * lights * alpha).coerceIn(0f, 1f)
        if (beamA > 0f) {
            drawPath(
                Path().apply { moveTo(-4f, 2f); lineTo(4f, 2f); lineTo(9f, 22f); lineTo(-9f, 22f); close() },
                Brush.verticalGradient(listOf(ink(Color(160, 210, 255)).copy(alpha = beamA), ink(Color(160, 210, 255)).copy(alpha = 0f)), 2f, 22f),
            )
        }
        drawOval(Color(196, 204, 220).copy(alpha = 0.9f * alpha), topLeft = Offset(-11f, -3.2f), size = Size(22f, 6.4f))
        if (onLight) drawOval(OnLightEdge.copy(alpha = 0.8f * alpha), topLeft = Offset(-11f, -3.2f), size = Size(22f, 6.4f), style = Stroke(0.7f))
        drawArc(ink(Color(160, 210, 255)).copy(alpha = 0.6f * alpha), 180f, 180f, true, Offset(-4.6f, -6f), Size(9.2f, 7.2f))
        val row = listOf(Color(255, 122, 31), ink(Color(238, 240, 245)), Color(131, 112, 238), Color(242, 183, 5), ink(Color(238, 240, 245)))
        val lit = floor(s * 6f).toInt().mod(row.size)
        for (i in row.indices) {
            val p = (((s * 0.8f + i.toFloat() / row.size) % 1f + 1f) % 1f) * 2f * PI.toFloat()
            if (sin(p) < -0.2f) continue
            val a = if (i == lit) 1f else 0.35f
            drawCircle(row[i].copy(alpha = (a * alpha).coerceIn(0f, 1f)), if (i == lit) 1.3f else 0.9f, Offset(cos(p) * 9.5f, 0.7f))
        }
    }

    /** zeichneDuesen: small jets for braking and turning, against the part of the thrust that does not push forward. */
    private fun DrawScope.thrusters(s: Saucer, tilt: Float, age: Float) {
        var bx = s.thrustX
        var by = s.thrustY
        val v = hypot(s.vx, s.vy)
        if (v > 4f) {
            val ex = s.vx / v
            val ey = s.vy / v
            val fwd = bx * ex + by * ey
            if (fwd > 0f) { bx -= fwd * ex; by -= fwd * ey }
        }
        val c = cos(tilt)
        val sn = sin(tilt)
        val lx = bx * c + by * sn
        val ly = -bx * sn + by * c
        fun strength(a: Float) = min(1f, max(0f, (abs(a) - 22f) / 150f)).pow(0.7f)
        val flicker = 0.85f + 0.15f * sin(age * 47f) * sin(age * 31f)
        fun jet(x: Float, y: Float, dx: Float, dy: Float, k: Float) {
            if (k < 0.04f) return
            val len = (3f + 14f * k) * flicker
            val b = 0.8f + 1.1f * k
            val ex = x + dx * len
            val ey = y + dy * len
            val nx = -dy * b
            val ny = dx * b
            drawPath(
                Path().apply { moveTo(x + nx, y + ny); lineTo(ex, ey); lineTo(x - nx, y - ny); close() },
                Brush.linearGradient(
                    0f to ink(Color(240, 248, 255)).copy(alpha = min(1f, 0.5f + 0.6f * k)),
                    0.35f to ink(Color(150, 205, 255)).copy(alpha = 0.25f + 0.55f * k),
                    1f to ink(Color(120, 180, 255)).copy(alpha = 0f),
                    start = Offset(x, y), end = Offset(ex, ey),
                ),
            )
            glow(x, y, 3f + 7f * k, 0.25f + 0.5f * k)
        }
        val kx = strength(lx)
        val ky = strength(ly)
        if (lx > 0f) jet(-11f, 0.4f, -1f, 0.08f, kx) else jet(11f, 0.4f, 1f, 0.08f, kx)
        if (ly < 0f) { jet(-5.5f, 2.6f, -0.15f, 1f, ky); jet(5.5f, 2.6f, 0.15f, 1f, ky) }
        else { jet(-7f, -1.6f, -0.2f, -1f, ky); jet(7f, -1.6f, 0.2f, -1f, ky) }
    }

    /** zeichneSpur: exhaust of the last 1.4 s, thin and bright at the saucer, wider and paler behind. */
    private fun DrawScope.trail(s: Saucer) {
        val sp = s.trail
        while (sp.isNotEmpty() && time - sp.first().t > TRAIL_S) sp.removeFirst()
        for (i in 1 until sp.size) {
            val a = sp[i - 1]
            val b = sp[i]
            val age = (time - b.t) / TRAIL_S
            val speed = hypot(b.x - a.x, b.y - a.y) / max(0.001f, b.t - a.t) / max(0.2f, b.gr)
            val k = min(1f, speed / 40f) * (1f - age) * (1f - age)
            if (k < 0.01f) continue
            val width = b.gr * (1.4f + age * 5f)
            drawLine(ink(Color(150, 200, 255)).copy(alpha = 0.1f * k), Offset(a.x, a.y), Offset(b.x, b.y), width * 3.2f)
            drawLine(ink(Color(215, 235, 255)).copy(alpha = 0.42f * k), Offset(a.x, a.y), Offset(b.x, b.y), width)
        }
    }

    private fun DrawScope.drawSaucerFlight(s: Saucer) {
        trail(s)
        val f = s.escape
        if (f != null) { drawEscape(f); return }
        val o = pose(s)
        val last = s.trail.lastOrNull()
        if (last == null || time - last.t > 0.03f) s.trail.addLast(TrailPoint(o.x, o.y + 1.5f * o.gr, o.gr, time))
        val fadeIn = min(1f, o.age * 3f)
        val cover = min(fadeIn, o.gr / 0.2f)
        // far away only a point of light that turns into the saucer when it comes closer
        if (o.gr < 0.4f) glow(o.x, o.y, 4f, 0.35f * fadeIn * (1f - o.gr / 0.4f))
        withTransform({
            translate(o.x, o.y)
            rotate(o.tilt * 180f / PI.toFloat(), Offset.Zero)
            scale(o.gr, o.gr, Offset.Zero)
        }) {
            saucer(o.age, 0.5f + 0.5f * sin(o.age * 4.2f), 1f, cover)
            thrusters(s, o.tilt, o.age)
            // the dome flashes now and then; while it hovers at the finger it blinks more often
            if (sin(o.age * 1.7f) > 0.96f || (s.lured && s.wait > 0f && sin(o.age * 9f) > 0.5f)) glow(0f, -3f, 7f, 0.5f * cover)
        }
    }

    /** The escape of zeichneUfo: shake on the spot, then hyperdrive towards the reader and out of the picture. */
    private fun DrawScope.drawEscape(f: Escape) {
        val ft = time - f.t0
        if (ft < SHAKE_S) {
            val z = (1f - ft / SHAKE_S) * 1.6f + 0.6f
            withTransform({
                translate(f.x + sin(ft * 90f) * z, f.y + cos(ft * 77f) * z)
                rotate(sin(ft * 60f) * 0.18f * 180f / PI.toFloat(), Offset.Zero)
                scale(f.gr, f.gr, Offset.Zero)
            }) {
                saucer(time, 1f, 1.6f, 1f)
                glow(0f, -2f, 10f, 0.35f + 0.4f * (ft / SHAKE_S))
            }
            return
        }
        val w = ft - SHAKE_S
        val sg = max(0.8f, f.gr)
        val se = f.side
        val front = max(0f, 1f - se)
        val reach = if (se < 0.5f) se else max(1f, se)
        val way = f.reach * reach * min(1f, w / 0.45f).pow(1.7f) + max(0f, w - 0.45f) * 900f * se + 40f * w * sg
        val x = f.x + f.dx * way
        val y = f.y + f.dy * way
        val gr = f.gr * (1f + 2f * w + (20f + 110f * front) * f.grow * w * w)
        val cover = max(0f, 1f - w / 0.48f)
        val after = max(0f, 1f - w / 1.35f)
        val nx = -f.dy
        val ny = f.dx
        if (after > 0f && way > 2f) {
            val b0 = 0.9f * sg
            val b1 = min(40f, 2.2f * gr)
            drawPath(
                Path().apply {
                    moveTo(f.x + nx * b0, f.y + ny * b0); lineTo(x + nx * b1, y + ny * b1)
                    lineTo(x - nx * b1, y - ny * b1); lineTo(f.x - nx * b0, f.y - ny * b0); close()
                },
                Brush.linearGradient(
                    listOf(ink(Color(200, 228, 255)).copy(alpha = 0.45f * after), ink(Color(200, 228, 255)).copy(alpha = 0.12f * after)),
                    Offset(f.x, f.y), Offset(x, y),
                ),
            )
        }
        if (cover > 0f) {
            for (i in 0 until 18) {
                val hh = abs((sin(i * 91.7f) * 4375.85f) % 1f)
                val wk = (i / 18f) * 2f * PI.toFloat() + hh * 0.3f
                val r0 = 12f * gr * (0.8f + 0.4f * hh)
                val r1 = r0 * (1.5f + 3f * w) + 30f * w
                val a = (0.15f + 0.4f * front) * cover * (0.5f + 0.5f * hh)
                val p0 = Offset(x + cos(wk) * r0, y + sin(wk) * r0)
                val p1 = Offset(x + cos(wk) * r1, y + sin(wk) * r1)
                drawLine(
                    Brush.linearGradient(listOf(ink(Color(200, 228, 255)).copy(alpha = 0f), ink(Color(225, 240, 255)).copy(alpha = a.coerceIn(0f, 1f))), p0, p1),
                    p0, p1, 0.8f + 1.4f * hh, StrokeCap.Round,
                )
            }
        }
        if (w < 0.25f) glow(f.x, f.y, (14f + 40f * w) * sg, 0.5f * (1f - w / 0.25f))
        if (front > 0.5f && w > 0.25f && w < 0.7f) {
            val q = sin(((w - 0.25f) / 0.45f) * PI.toFloat())
            glow(x, y, 40f + 260f * (w - 0.25f), 0.3f * q)
        }
        if (cover <= 0f) return
        val wr = atan2(f.dy, f.dx) * 180f / PI.toFloat()
        withTransform({
            translate(x, y)
            rotate(wr, Offset.Zero)
            scale(
                gr * (1f + min(1.5f, w * 4f * min(1f, se))),
                gr * (1f + 0.6f * w * front) * max(0.6f, 1f - w * 0.6f * min(1f, se)),
                Offset.Zero,
            )
            rotate(-wr, Offset.Zero)
        }) { saucer(time, 1f, 0f, cover) }
    }
}

/** The UFO of a screen; with "Remove animations" it never comes. */
@Composable
internal fun rememberUfo(): UfoController {
    val context = LocalContext.current
    return remember {
        UfoController(still = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f)
    }
}

/**
 * The layer above the whole screen. It has no pointer input, so touches go through to the screen below, and its
 * canvas only exists while the UFO or a lure pulse is on screen; the frame clock runs only then.
 */
@Composable
internal fun UfoLayer(ufo: UfoController, modifier: Modifier = Modifier) {
    LaunchedEffect(ufo.active) {
        if (!ufo.active) return@LaunchedEffect
        var last = 0L
        while (ufo.active) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else min((now - last) / 1e9f, 0.1f)
                last = now
                ufo.step(dt)
            }
        }
    }
    val density = LocalDensity.current.density
    val placed = Modifier.fillMaxSize().clearAndSetSemantics { }.onPlaced { c ->
        ufo.place(c.positionInRoot(), Size(c.size.width.toFloat(), c.size.height.toFloat()), density)
    }
    androidx.compose.foundation.layout.Box(modifier.then(placed)) {
        if (ufo.active) {
            Canvas(Modifier.fillMaxSize()) {
                with(ufo) { drawAll() }
            }
        }
    }
}
