// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

const val PACKAGE = "net.planetfoss.app.benchmark"

private const val WAIT = 15_000L

/** Latest has loaded: its feed is on screen and scrolls. */
fun MacrobenchmarkScope.waitForFeed() {
    device.wait(Until.hasObject(By.scrollable(true)), WAIT)
}

/** Flings whatever list is on screen; Compose replaces the node while posts arrive, so it is looked up each time. */
private fun MacrobenchmarkScope.fling(direction: Direction) {
    runCatching {
        device.findObject(By.scrollable(true))?.run {
            setGestureMargin(device.displayWidth / 5)
            fling(direction)
        }
    }
    device.waitForIdle()
}

/** What a reader does first: scroll through Latest, look at the supernovas and the rubrics, back to Latest. */
fun MacrobenchmarkScope.browse() {
    waitForFeed()
    repeat(3) { fling(Direction.DOWN) }
    fling(Direction.UP)
    for (tab in listOf("Supernovas", "Rubri(cs|ken)", "Latest|Neueste")) {
        runCatching { device.findObject(By.text(Pattern.compile(tab)))?.click() }.getOrNull() ?: continue
        device.waitForIdle()
        waitForFeed()
        fling(Direction.DOWN)
    }
}
