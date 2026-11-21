// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.freiversum

import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import net.planetfoss.app.R

/*
 * The sky of the freiversum card ("Sternwarte"): a cut-out of the star map on freiversum.de, in the map's own
 * units. Star positions, radii, colours and catalogue numbers come from freiversum.de/src/data/projekte.json
 * (field "karte", freiversum 0.48.0); when a project is added or moved there, update [FvStars] by hand.
 * Geometry of the grid, the background stars and the twinkle shape as in docs/app-fuss/index.html (the concept).
 */

/** One project star of the freiversum. */
internal class FvStar(
    val id: String,
    val catalogue: String,
    val name: String,
    @StringRes val role: Int,
    val color: Color,
    val x: Float,
    val y: Float,
    val r: Float,
) {
    val center get() = Offset(x, y)
}

/** From projekte.json, in the order of the constellation line: FV 1 → FV 4. */
internal val FvStars = listOf(
    FvStar("lui", "FV 1", "Linux und Ich", R.string.freiversum_role_lui, Color(0xFFFF6600), 150f, 380f, 30f),
    FvStar("pf", "FV 2", "PlanetFOSS", R.string.freiversum_role_pf, Color(0xFF8370EE), 260f, 210f, 26f),
    FvStar("pulse", "FV 3", "LinuxPULSE", R.string.freiversum_role_pulse, Color(0xFFFFBC00), 380f, 305f, 15f),
    FvStar("ts", "FV 4", "tuxsucht", R.string.freiversum_role_ts, Color(0xFFEEF2F8), 480f, 120f, 17f),
)

/** This app's own star: PlanetFOSS, FV 2. */
internal val OwnStar = FvStars[1]

/** The visible cut-out of the map (viewBox 40 70 560 370) and the pole of the radar grid. */
internal const val VB_X = 40f
internal const val VB_Y = 70f
internal const val VB_W = 560f
internal const val VB_H = 370f
internal val Pole = Offset(300f, 520f)

internal val Ink = Color(0xFF0B0D14)
internal val Raised = Color(0xFF1A1E28)
internal val Text1 = Color(0xFFEEF0F5)
internal val Text2 = Color(0xFFA3A9B8)
internal val Text3 = Color(0xFF7A8296)
internal val Edge = Color(0x12EEF0F5)     // rgba(238,240,245,.07)
internal val Line2 = Color(0x29EEF0F5)    // rgba(238,240,245,.16)
internal val Grid = Color(0x12EEF0F5)
internal val GridMain = Color(0x1CEEF0F5) // .11
internal val FarStar = Color(0xFF5F6779)
internal val FarStarBright = Color(0xFFA3A9B8)
internal val ConstellationLine = Color(0xFF6C7194)

/** A background star: position and radius in map units, [blinks] during the intro after [delay] seconds. */
internal class FarPoint(val x: Float, val y: Float, val r: Float, val bright: Boolean, val blinks: Boolean, val delay: Float)

/**
 * The 46 background stars, from the same Park–Miller generator with seed 11 as the concept, so they sit where
 * they sit there (positions rounded to tenths like the SVG).
 */
internal val FarPoints: List<FarPoint> = run {
    var seed = 11L
    fun next(): Double { seed = (seed * 16807L) % 2147483647L; return seed / 2147483647.0 }
    fun tenth(v: Double) = (Math.round(v * 10) / 10.0).toFloat()
    List(46) {
        val x = tenth(VB_X + next() * VB_W)
        val y = tenth(VB_Y + next() * VB_H)
        val r = tenth(next() * 1.6 + 0.6)
        val bright = next() > 0.8
        val blinks = next() > 0.6
        val delay = (Math.round(next() * 1.5 * 100) / 100.0).toFloat()
        FarPoint(x, y, r, bright, blinks, delay)
    }
}

/** The four-pointed twinkle of the freiversum star map, as a path around [c] with radius [r]. */
internal fun twinkle(c: Offset, r: Float, k: Float = 0.18f): Path = Path().apply {
    moveTo(c.x, c.y - r)
    quadraticTo(c.x + r * k, c.y - r * k, c.x + r, c.y)
    quadraticTo(c.x + r * k, c.y + r * k, c.x, c.y + r)
    quadraticTo(c.x - r * k, c.y + r * k, c.x - r, c.y)
    quadraticTo(c.x - r * k, c.y - r * k, c.x, c.y - r)
    close()
}

/** Point at [u] (0..1 of the length) along the constellation line. */
internal fun alongLine(u: Float): Offset {
    val seg = FvStars.zipWithNext { a, b -> (b.center - a.center).getDistance() }
    var d = u.coerceIn(0f, 1f) * seg.sum()
    for (i in seg.indices) {
        if (d <= seg[i]) return FvStars[i].center + (FvStars[i + 1].center - FvStars[i].center) * (d / seg[i])
        d -= seg[i]
    }
    return FvStars.last().center
}
