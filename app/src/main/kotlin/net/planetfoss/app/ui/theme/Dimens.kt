// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * One spacing scale on a 4 dp grid for the whole app – screen margins, card padding, gaps.
 * Use these names instead of loose numbers so equal things stay equally spaced.
 */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
}

/** Corner radii as on the website (`--rad-s`, `--rad-m`, `--rad-l`). */
object Radius {
    /** Thumbnails, small badges, touch highlights of rows inside a card. */
    val s = RoundedCornerShape(8.dp)
    /** Images inside a card. */
    val m = RoundedCornerShape(12.dp)
    /** Every card and box: posts, supernovas, comets, sponsor, notices. */
    val card = RoundedCornerShape(16.dp)
}

/** Lists and settings never get wider than this; on tablets and in landscape they stay centred. */
val ContentMaxWidth = 720.dp
