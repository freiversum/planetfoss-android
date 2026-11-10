// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Another app starting the exported activity gets nothing but the planetfoss:// link through. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MainActivityIntentTest {
    private fun sanitized(intent: Intent): Intent {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val m = MainActivity::class.java.getDeclaredMethod("sanitize", Intent::class.java).apply { isAccessible = true }
        return m.invoke(activity, intent) as Intent
    }

    @Test
    fun navigationExtrasAreDropped() {
        val evil = Intent(Intent.ACTION_VIEW, Uri.parse("planetfoss://supernova/gnome-52"))
            .putExtra("android-support-nav:controller:deepLinkIds", intArrayOf(1, 2))
            .putExtra("android-support-nav:controller:deepLinkArgs", arrayListOf(Bundle().apply { putString("url", "https://evil.example/") }))
        val out = sanitized(evil)
        assertEquals(Uri.parse("planetfoss://supernova/gnome-52"), out.data)
        assertNull(out.extras)
    }

    @Test
    fun otherSchemesBecomeAPlainStart() {
        val out = sanitized(Intent(Intent.ACTION_VIEW, Uri.parse("https://evil.example/")))
        assertNull(out.data)
        assertEquals(Intent.ACTION_MAIN, out.action)
        assertTrue(out.hasCategory(Intent.CATEGORY_LAUNCHER))
    }
}
