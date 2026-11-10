// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.browser

import android.content.Context
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase

/**
 * “Delete browser data”: everything the internal browser keeps – cookies, web storage
 * (localStorage, IndexedDB, service workers' caches), HTTP cache, saved HTTP logins and
 * site permissions. The app's own data (cached lists, saved articles) is not touched.
 * Must run on the main thread (WebView).
 */
fun clearBrowserData(context: Context, done: () -> Unit) {
    WebStorage.getInstance().deleteAllData()
    GeolocationPermissions.getInstance().clearAll()
    WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword()
    WebView(context).apply {
        clearCache(true)
        clearHistory()
        destroy()
    }
    CookieManager.getInstance().removeAllCookies {
        CookieManager.getInstance().flush()
        done()
    }
}
