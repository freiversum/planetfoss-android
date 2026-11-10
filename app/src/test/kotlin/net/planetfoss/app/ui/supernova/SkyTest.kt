// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.supernova

import net.planetfoss.core.api.Comet
import net.planetfoss.core.api.Supernova
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SkyTest {
    private val t = Instant.parse("2026-10-30T08:00:00Z")
    private fun nova(key: String, burning: Boolean = false) = Supernova("id-$key", key, key, t, t, "u", burning = burning)
    private fun comet(key: String) = Comet(key, key, "u", t, t)
    private fun keys(sky: List<Sky>) = sky.map { if (it is Sky.Nova) it.nova.key else "c:" + (it as Sky.Rising).comet.key }

    @Test
    fun burningFirstThenOneCometAfterEachSupernova() {
        val sky = skyOf(listOf(nova("a"), nova("b", burning = true), nova("c")), listOf(comet("x"), comet("y")))
        assertEquals(listOf("b", "c:x", "a", "c:y", "c"), keys(sky))
    }

    @Test
    fun moreCometsThanSupernovasGoToTheEnd() {
        assertEquals(listOf("a", "c:x", "c:y", "c:z"), keys(skyOf(listOf(nova("a")), listOf(comet("x"), comet("y"), comet("z")))))
    }

    @Test
    fun withoutSupernovasOnlyComets() {
        assertEquals(listOf("c:x"), keys(skyOf(emptyList(), listOf(comet("x")))))
        assertEquals(emptyList<String>(), keys(skyOf(emptyList(), emptyList())))
    }
}
