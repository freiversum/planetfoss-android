// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Message
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import net.planetfoss.core.url.UrlDecision
import net.planetfoss.core.url.UrlPolicy

/** Why the main page could not be shown. */
enum class PageError { OFFLINE, HOST, TIMEOUT, CERTIFICATE, BLOCKED, INSECURE, OTHER }

/** Everything the browser screen draws; updated by [BrowserClient] and [BrowserChrome]. */
interface BrowserEvents {
    fun onPage(pageUrl: String, isLoading: Boolean)
    fun onProgress(percent: Int)
    fun onTitle(pageTitle: String)
    fun onHistory(back: Boolean, forward: Boolean)
    fun onError(pageError: PageError?)
    /** Another app's link the user tapped – the screen asks before anything opens. */
    fun onAskOutside(target: String, scheme: String)
    /** A video asks for full screen ([view]), or leaves it (null). */
    fun onFullscreen(view: android.view.View?, exit: (() -> Unit)?) = Unit
}

/**
 * Security settings of the internal browser (docs/05-browser.md). Pages get no file or
 * content access, no mixed content, no JavaScript bridge, no Safe Browsing (it sends URL
 * hashes to Google), no third-party cookies and no form data storage.
 */
@SuppressLint("SetJavaScriptEnabled")
fun WebView.applySecureSettings(javaScript: Boolean) {
    settings.apply {
        javaScriptEnabled = javaScript
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        allowFileAccess = false
        allowContentAccess = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        safeBrowsingEnabled = false
        domStorageEnabled = true // many sources break without localStorage; cleared with the browser data
        @Suppress("DEPRECATION")
        saveFormData = false
        setGeolocationEnabled(false)
        mediaPlaybackRequiresUserGesture = true
        builtInZoomControls = true
        displayZoomControls = false
        isAlgorithmicDarkeningAllowed = true
    }
    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(this@applySecureSettings, false)
    }
}

/**
 * Navigation and loading decisions. [intercept] is the hook for the tracker protection (phase 6):
 * it gets each sub-resource request and the page's host, and returns a replacement
 * response to block it or null to let it through.
 */
class BrowserClient(
    private val events: BrowserEvents,
    private val intercept: ((WebResourceRequest, String?) -> WebResourceResponse?)? = null,
) : WebViewClient() {
    @Volatile private var pageHost: String? = null

    /** The current page was asked for as http:// and upgraded: if https fails, say why. */
    @Volatile var upgraded: Boolean = false

    /**
     * Every navigation of the page goes through [UrlPolicy]; only http(s) stays inside, and
     * plain http is upgraded to https (the app permits no cleartext traffic at all).
     */
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        if (handle(url, request.isForMainFrame, request.hasGesture())) return true
        if (request.isForMainFrame && !UrlPolicy.isSecure(url) && url != "about:blank") {
            // https redirecting back to http (http.badssl.com does that): no loop, the page has no encryption
            if (upgraded && request.isRedirect) {
                view.stopLoading()
                events.onError(PageError.INSECURE)
                return true
            }
            upgraded = true
            view.loadUrl(UrlPolicy.upgrade(url))
            return true
        }
        return false
    }

    /** Returns true when the WebView must not load [url] itself. */
    fun handle(url: String, mainFrame: Boolean, userGesture: Boolean): Boolean = when (val d = UrlPolicy.decide(url)) {
        is UrlDecision.LoadInside -> false
        is UrlDecision.AskToOpenOutside -> {
            // without a tap a page may not even ask to open another app
            if (mainFrame && userGesture) events.onAskOutside(d.url, d.scheme)
            true
        }
        UrlDecision.Block -> {
            if (mainFrame && userGesture) events.onError(PageError.BLOCKED)
            true
        }
    }

    override fun onPageFinished(view: WebView, url: String) {
        events.onPage(url, isLoading = false)
        events.onHistory(view.canGoBack(), view.canGoForward())
        if (view.progress >= 100) upgraded = false
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        pageHost = UrlPolicy.displayHost(url).ifEmpty { null }
        events.onTitle("") // the previous page's title must not stay over an error of this one
        events.onError(null)
        events.onPage(url, isLoading = true)
        events.onHistory(view.canGoBack(), view.canGoForward())
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        events.onPage(url, isLoading = view.progress < 100)
        events.onHistory(view.canGoBack(), view.canGoForward())
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (!request.isForMainFrame) return
        events.onError(
            if (upgraded) PageError.INSECURE else when (error.errorCode) {
                ERROR_HOST_LOOKUP -> PageError.HOST
                ERROR_CONNECT, ERROR_IO -> PageError.OFFLINE
                ERROR_TIMEOUT -> PageError.TIMEOUT
                ERROR_FAILED_SSL_HANDSHAKE -> PageError.CERTIFICATE
                ERROR_UNSAFE_RESOURCE -> PageError.BLOCKED
                else -> PageError.OTHER
            },
        )
    }

    /** Certificate errors are never accepted, not even on request. */
    @SuppressLint("WebViewClientOnReceivedSslError")
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
        if (error.url == view.url || view.progress < 100) events.onError(PageError.CERTIFICATE)
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        if (request.isForMainFrame) return null // the page itself is never blocked
        return intercept?.invoke(request, pageHost)
    }

    /** A WebView renderer crash must not take the app down. */
    override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
        events.onError(PageError.OTHER)
        return true
    }
}

/** Progress, title and the requests a page makes of the device – all of which are refused. */
class BrowserChrome(private val events: BrowserEvents) : WebChromeClient() {
    override fun onProgressChanged(view: WebView, newProgress: Int) = events.onProgress(newProgress)

    override fun onReceivedTitle(view: WebView, title: String?) {
        if (!title.isNullOrBlank()) events.onTitle(title)
    }

    /** Nothing of the page's console reaches logcat. */
    override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean = true

    /** Full screen for videos (YouTube's button, double tap): the screen lays [view] over everything. */
    override fun onShowCustomView(view: android.view.View, callback: CustomViewCallback) =
        events.onFullscreen(view) { callback.onCustomViewHidden() }

    override fun onHideCustomView() = events.onFullscreen(null, null)

    /** Camera, microphone, MIDI, protected media: denied. */
    override fun onPermissionRequest(request: PermissionRequest) = request.deny()

    override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback) =
        callback.invoke(origin, false, false)

    /** No uploads from the internal browser. */
    override fun onShowFileChooser(view: WebView, filePathCallback: ValueCallback<Array<android.net.Uri>>, fileChooserParams: FileChooserParams): Boolean {
        filePathCallback.onReceiveValue(null)
        return true
    }

    /** Pop-ups: multiple windows are off, so this should not happen; refuse if it does. */
    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean = false
}
