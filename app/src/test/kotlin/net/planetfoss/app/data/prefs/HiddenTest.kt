// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HiddenTest {
    @Test
    fun `a blocked fediverse account survives storing and reading`() {
        val account = Hidden(Hidden.ACCOUNT, Hidden.account("mastodon", "@anna@example.social"), "@anna@example.social")
        val back = Hidden.parse(account.raw())
        assertNotNull(back)
        assertEquals(account, back)
        assertEquals("mastodon:@anna@example.social", back!!.slug)
    }

    @Test
    fun `the same handle on two services is two accounts`() {
        assertEquals(false, Hidden.account("mastodon", "@a") == Hidden.account("bluesky", "@a"))
    }

    @Test
    fun `unknown kinds and empty slugs are dropped`() {
        assertNull(Hidden.parse("x\tslug\tname"))
        assertNull(Hidden.parse("a\t\tname"))
    }
}
