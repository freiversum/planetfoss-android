// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import net.planetfoss.app.R

/*
 * The website's three fonts (public/assets/site.css, the same Latin subsets, SIL OFL 1.1 – res/raw/fonts_ofl.txt):
 * Space Grotesk for headings and titles (--display), Inter for text and controls (--sans),
 * JetBrains Mono for meta lines – source, time, numbers (--mono). Characters outside the subset
 * fall back to the system font.
 */
private val Display = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_regular, FontWeight.Medium),
    Font(R.font.space_grotesk_bold, FontWeight.SemiBold),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

private fun inter(weight: Int) = Font(
    R.font.inter_variable,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private val Sans = FontFamily(inter(400), inter(500), inter(600), inter(700))

val MonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/** Meta lines as on the website (`.meta`: 12 px mono, muted). */
val MetaStyle = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp, lineHeight = 16.sp)

private val base = Typography()

private fun TextStyle.display(weight: FontWeight = FontWeight.Bold, tracking: Float = -0.01f) =
    copy(fontFamily = Display, fontWeight = weight, letterSpacing = tracking.em)

private fun TextStyle.sans(weight: FontWeight? = null) = copy(fontFamily = Sans, fontWeight = weight ?: fontWeight)

/**
 * Material's type scale with the PlanetFOSS fonts. Headlines and titles in bold Space Grotesk
 * (the website's `h2, h3`: 700, −0.015 em); card titles (titleMedium) a little larger than
 * Material's 16 sp so they stand clearly above teaser and meta line.
 */
val PlanetTypography = Typography(
    displayLarge = base.displayLarge.display(tracking = -0.02f),
    displayMedium = base.displayMedium.display(tracking = -0.02f),
    displaySmall = base.displaySmall.display(tracking = -0.015f),
    headlineLarge = base.headlineLarge.display(tracking = -0.015f),
    headlineMedium = base.headlineMedium.display(tracking = -0.015f),
    headlineSmall = base.headlineSmall.display(),
    titleLarge = base.titleLarge.display(),
    titleMedium = base.titleMedium.display().copy(fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = base.titleSmall.display().copy(fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = base.bodyLarge.sans(),
    bodyMedium = base.bodyMedium.sans(),
    bodySmall = base.bodySmall.sans(),
    labelLarge = base.labelLarge.sans(FontWeight.SemiBold),
    labelMedium = base.labelMedium.sans(FontWeight.Medium),
    labelSmall = base.labelSmall.sans(FontWeight.Medium),
)
