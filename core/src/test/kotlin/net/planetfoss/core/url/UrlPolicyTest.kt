// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.core.url

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolicyTest {
    @Test
    fun webLinksLoadInside() {
        assertEquals(UrlDecision.LoadInside("https://linuxiac.com/a"), UrlPolicy.decide("https://linuxiac.com/a"))
        assertEquals(UrlDecision.LoadInside("http://example.org/"), UrlPolicy.decide("http://example.org/"))
        assertEquals(UrlDecision.LoadInside("about:blank"), UrlPolicy.decide("about:blank"))
        assertEquals(UrlDecision.LoadInside("HTTPS://Example.org/"), UrlPolicy.decide("HTTPS://Example.org/"))
    }

    @Test
    fun dangerousSchemesAreBlocked() {
        for (url in listOf(
            "javascript:alert(1)", "JavaScript:alert(1)", "file:///data/data/net.planetfoss.app/databases/planetfoss.db",
            "content://com.android.contacts/contacts", "data:text/html,<script>x</script>", "blob:https://x/1",
            "about:config", "view-source:https://x/", "chrome://settings", "https:///nohost", "http://", "::::", "",
            "wss://example.org/socket",
        )) {
            assertEquals(url, UrlDecision.Block, UrlPolicy.decide(url))
        }
    }

    @Test
    fun otherAppsOnlyAfterAsking() {
        assertEquals(UrlDecision.AskToOpenOutside("mailto:a@example.org", "mailto"), UrlPolicy.decide("mailto:a@example.org"))
        assertEquals(UrlDecision.AskToOpenOutside("tel:+491234", "tel"), UrlPolicy.decide("tel:+491234"))
        assertEquals("market", (UrlPolicy.decide("market://details?id=org.fdroid") as UrlDecision.AskToOpenOutside).scheme)
        assertEquals("zoommtg", (UrlPolicy.decide("zoommtg://zoom.us/join") as UrlDecision.AskToOpenOutside).scheme)
    }

    @Test
    fun intentLinksOnlyUseTheirWebFallback() {
        val withFallback = "intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;" +
            "S.browser_fallback_url=https%3A%2F%2Fexample.org%2Fscan;end"
        assertEquals(UrlDecision.LoadInside("https://example.org/scan"), UrlPolicy.decide(withFallback))
        assertEquals(UrlDecision.Block, UrlPolicy.decide("intent://x/#Intent;scheme=evil;package=com.evil;end"))
        assertEquals(UrlDecision.Block, UrlPolicy.decide("intent://x/#Intent;S.browser_fallback_url=javascript%3Aalert(1);end"))
        assertEquals(UrlDecision.Block, UrlPolicy.decide("intent://x/#Intent;S.browser_fallback_url=file%3A%2F%2F%2Fetc%2Fhosts;end"))
    }

    @Test
    fun articleLinksMustBeWebUrls() {
        assertTrue(UrlPolicy.isWebUrl("https://9to5linux.com/x"))
        assertFalse(UrlPolicy.isWebUrl("javascript:alert(1)"))
        assertFalse(UrlPolicy.isWebUrl("intent://x/#Intent;end"))
        assertFalse(UrlPolicy.isWebUrl("https://"))
    }

    @Test
    fun plainHttpIsUpgraded() {
        assertEquals("https://example.org/a?b=1#c", UrlPolicy.upgrade("http://example.org/a?b=1#c"))
        assertEquals("https://Example.org/", UrlPolicy.upgrade("HTTP://Example.org/"))
        assertEquals("https://x.org/", UrlPolicy.upgrade("https://x.org/"))
        assertEquals("mailto:a@b", UrlPolicy.upgrade("mailto:a@b"))
    }

    @Test
    fun addressLine() {
        assertEquals("linuxiac.com", UrlPolicy.displayHost("https://www.Linuxiac.com/a?b=c"))
        assertEquals("", UrlPolicy.displayHost("javascript:x"))
        assertTrue(UrlPolicy.isSecure("https://a.org/"))
        assertFalse(UrlPolicy.isSecure("http://a.org/"))
    }
}
