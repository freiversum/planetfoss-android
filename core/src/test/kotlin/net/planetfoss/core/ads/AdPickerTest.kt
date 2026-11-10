// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.core.ads

import net.planetfoss.core.api.Ad
import net.planetfoss.core.api.ListTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AdPickerTest {
    private fun ad(id: Long, weight: Int = 5, topics: List<String> = emptyList()) =
        Ad(id = id, slot = AdSlot.FEED, lang = "de", after = 4, label = "Anzeige", title = "T$id", clickUrl = "https://x/a/$id", weight = weight, topics = topics)

    @Test
    fun noCandidatesNoAd() {
        assertNull(AdPicker.pick(emptyList(), null))
    }

    @Test
    fun weightsSetTheShares() {
        val a = ad(1, weight = 9)
        val b = ad(2, weight = 1)
        val rnd = Random(42)
        val n = 4000
        val countA = (1..n).count { AdPicker.pick(listOf(a, b), null, rnd)!!.id == 1L }
        // 90 % expected; wide margin, the seed makes the run repeatable
        assertTrue("share of A was $countA of $n", countA in (n * 0.86).toInt()..(n * 0.94).toInt())
    }

    @Test
    fun sameSeedSameCard() {
        val list = listOf(ad(1), ad(2), ad(3))
        assertEquals(AdPicker.pick(list, null, Random(7))!!.id, AdPicker.pick(list, null, Random(7))!!.id)
    }

    @Test
    fun topicCardsOnlyOnTheirTopics() {
        val plain = ad(1)
        val kde = ad(2, topics = listOf("kde"))
        val rnd = Random(1)
        assertTrue((1..200).all { AdPicker.pick(listOf(plain, kde), null, rnd)!!.id == 1L })
        assertTrue((1..200).all { AdPicker.pick(listOf(plain, kde), "fedora", rnd)!!.id == 1L })
        // on its own topic the bound card goes first
        assertTrue((1..200).all { AdPicker.pick(listOf(plain, kde), "kde", rnd)!!.id == 2L })
        assertNull(AdPicker.pick(listOf(kde), "fedora", rnd))
    }

    @Test
    fun oddWeightsAreClamped() {
        assertEquals(1L, AdPicker.pick(listOf(ad(1, weight = 0)), null)!!.id)
        assertEquals(1L, AdPicker.pick(listOf(ad(1, weight = 99)), null)!!.id)
    }

    @Test
    fun slotsFollowTheScreen() {
        assertEquals(AdSlot.FEED, ListTarget.Home.adSlot)
        assertEquals(AdSlot.LIST, ListTarget.Rubric("selfhosting").adSlot)
        assertEquals(AdSlot.LIST, ListTarget.Topic("kde").adSlot)
        assertEquals(AdSlot.LIST, ListTarget.Source("heise").adSlot)
        assertEquals(AdSlot.LIST, ListTarget.Videos.adSlot)
        assertNull(ListTarget.Starred.adSlot)
        assertNull(ListTarget.Bundle(3).adSlot)
    }
}
