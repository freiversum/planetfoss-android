// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.theme

import androidx.compose.ui.graphics.Color

/** Rubric colours of the website (site.css `.rbg-<slug>`); unknown rubrics get the outline colour. */
fun rubricColor(slug: String): Color? = when (slug) {
    "desktop" -> Color(0xFF5F60E0)
    "apps" -> Color(0xFF0071E0)
    "dev" -> Color(0xFF8B50CF)
    "ki" -> Color(0xFFA941B0)
    "distros" -> Color(0xFF008375)
    "selfhosting" -> Color(0xFF00818D)
    "how-to" -> Color(0xFF2A835B)
    "security-privacy" -> Color(0xFFC43F3E)
    "politik" -> Color(0xFFC04A00)
    "kernel" -> Color(0xFFC23D61)
    "hardware" -> Color(0xFF9A6A00)
    "gaming" -> Color(0xFF43813D)
    "community" -> Color(0xFFA56217)
    else -> null
}
