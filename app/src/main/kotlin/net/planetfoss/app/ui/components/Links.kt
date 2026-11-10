// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import net.planetfoss.app.R
import net.planetfoss.core.url.UrlPolicy

/**
 * Open a web page outside the app: a Custom Tab of the default browser if one supports
 * them, else the system chooser. The external browser keeps its own settings and
 * protection; the app's blocking does not reach it. Only http(s) leaves the app.
 */
fun openOutsideApp(context: Context, url: String) {
    if (!UrlPolicy.isWebUrl(url)) return
    try {
        CustomTabsIntent.Builder().setShowTitle(true)
            // slides in and out like the app's own pages; the app holds still underneath
            .setStartAnimations(context, R.anim.page_enter, R.anim.page_hold)
            .setExitAnimations(context, R.anim.page_hold, R.anim.page_exit)
            .build().apply {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // the browser passes the app on as referrer (android-app://…), so the source sees where its reader came from
            intent.putExtra(Intent.EXTRA_REFERRER, "android-app://${context.packageName}".toUri())
        }.launchUrl(context, url.toUri())
    } catch (_: ActivityNotFoundException) {
        openWithChooser(context, url)
    }
}

/**
 * Hand the link to the app Android has for it – for a YouTube link the YouTube app (or NewPipe,
 * Tubular …) where one is installed, else the default browser in a normal tab. Used for videos,
 * which play better in their own app than in a Custom Tab.
 */
fun openInHandlerApp(context: Context, url: String) {
    if (!UrlPolicy.isWebUrl(url)) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        openWithChooser(context, url)
    }
}

/** Video sites whose links have their own apps (YouTube, NewPipe, Tubular, FreeTube's Android forks …). */
fun isVideoSite(url: String): Boolean = UrlPolicy.displayHost(url).let { h ->
    h == "youtu.be" || h == "youtube.com" || h.endsWith(".youtube.com") || h == "youtube-nocookie.com" || h.endsWith(".youtube-nocookie.com")
}

/** Android's app chooser for the link, every time (no default is remembered). */
fun openWithChooser(context: Context, url: String) {
    if (!UrlPolicy.isWebUrl(url)) return
    val view = Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
    try {
        context.startActivity(Intent.createChooser(view, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // no app can show a web page – nothing sensible left to do
    }
}
