// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LogoDotTest {
    private fun tile(edge: Int, inner: Int = Color.rgb(255, 102, 0)): Bitmap =
        Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(edge)
            for (x in 8 until 56) for (y in 8 until 56) setPixel(x, y, inner)
        }

    @Test fun darkTileFillsTheDot() = assertTrue(fillsSquare(tile(Color.rgb(43, 42, 42))))

    @Test fun transparentMarginKeepsTheWhitePlate() = assertFalse(fillsSquare(tile(Color.TRANSPARENT)))

    @Test fun whiteMarginKeepsTheWhitePlate() = assertFalse(fillsSquare(tile(Color.WHITE)))
}
