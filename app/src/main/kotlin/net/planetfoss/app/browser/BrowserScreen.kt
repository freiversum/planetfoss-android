// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.browser

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.foundation.background
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import net.planetfoss.app.ui.components.isVideoSite
import net.planetfoss.app.ui.components.openInHandlerApp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.planetfoss.app.R
import net.planetfoss.app.ui.components.CenterMessage
import net.planetfoss.app.ui.components.copyLink
import net.planetfoss.app.ui.components.openOutsideApp
import net.planetfoss.app.ui.components.shareLink
import net.planetfoss.app.ui.components.SupportMenuItem
import net.planetfoss.core.api.Support
import net.planetfoss.core.url.UrlPolicy

/**
 * The internal browser: one WebView with an address line, progress, history, reload,
 * share, copy, “open in browser” and an error page. Closing returns to where the article
 * was opened; the WebView's history survives navigation and process death.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BrowserScreen(startUrl: String, javaScript: Boolean, onClose: () -> Unit, support: Pair<Support, String>? = null, vm: BrowserViewModel = hiltViewModel()) {
    val adblock by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var url by rememberSaveable { mutableStateOf(startUrl) }
    var title by rememberSaveable { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<PageError?>(null) }
    var askOutside by remember { mutableStateOf<Pair<String, String>?>(null) }
    // a video in full screen: its view and how to leave
    var fullscreen by remember { mutableStateOf<Pair<android.view.View, () -> Unit>?>(null) }
    val saved = rememberSaveable { Bundle() }

    val events = remember {
        object : BrowserEvents {
            override fun onPage(pageUrl: String, isLoading: Boolean) {
                if (pageUrl != "about:blank") url = pageUrl
                if (isLoading) vm.newPage(UrlPolicy.displayHost(pageUrl).ifEmpty { null })
                loading = isLoading
            }
            override fun onProgress(percent: Int) { progress = percent }
            override fun onTitle(pageTitle: String) { title = pageTitle }
            override fun onHistory(back: Boolean, forward: Boolean) { canBack = back; canForward = forward }
            override fun onError(pageError: PageError?) { error = pageError }
            override fun onAskOutside(target: String, scheme: String) { askOutside = target to scheme }
            override fun onFullscreen(view: android.view.View?, exit: (() -> Unit)?) {
                fullscreen = if (view != null && exit != null) view to exit else null
            }
        }
    }

    val webView = remember {
        WebView(context).apply {
            // AndroidView's default is WRAP_CONTENT, which puts WebView into content-height mode:
            // CSS `vh` becomes 0 and pages that size media with it (golem.de) show no images
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            applySecureSettings(javaScript)
            webViewClient = BrowserClient(events, intercept = vm::intercept)
            webChromeClient = BrowserChrome(events)
            // downloads are no business of the internal browser: hand them to the user's browser
            setDownloadListener { dl, _, _, _, _ -> if (UrlPolicy.isWebUrl(dl)) openOutsideApp(context, dl) }
            if (saved.isEmpty || restoreState(saved) == null) {
                if (UrlPolicy.isWebUrl(startUrl)) {
                    (webViewClient as BrowserClient).upgraded = !UrlPolicy.isSecure(startUrl)
                    // the source sees PlanetFOSS as where its reader came from, as from the website
                    loadUrl(UrlPolicy.upgrade(startUrl), mapOf("Referer" to REFERER))
                } else {
                    events.onError(PageError.BLOCKED) // only http(s) addresses ever reach the WebView
                }
            }
        }
    }

    DisposableEffect(webView) {
        onDispose {
            saved.clear()
            webView.saveState(saved)
            webView.stopLoading()
            webView.destroy()
        }
    }

    BackHandler(enabled = canBack) { webView.goBack() }
    // back leaves full screen first (registered later, so it wins over going back in the history)
    BackHandler(enabled = fullscreen != null) { fullscreen?.second?.invoke(); fullscreen = null }


    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.browser_close)) } },
                    title = {
                        Column {
                            Text(title.ifBlank { UrlPolicy.displayHost(url) }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // no lock when the certificate failed or the page had to fall back to http
                                val secure = UrlPolicy.isSecure(url) && error != PageError.CERTIFICATE && error != PageError.INSECURE
                                Icon(
                                    if (secure) Icons.Filled.Lock else Icons.Filled.Warning,
                                    stringResource(if (secure) R.string.browser_secure else R.string.browser_insecure),
                                    modifier = Modifier.size(14.dp).padding(end = 2.dp),
                                    tint = if (secure) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                                )
                                Text(UrlPolicy.displayHost(url), style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { if (loading) webView.stopLoading() else webView.reload() }) {
                            Icon(if (loading) Icons.Filled.Close else Icons.Filled.Refresh, stringResource(if (loading) R.string.browser_stop else R.string.browser_reload))
                        }
                        BrowserMenu(
                            adblockOn = adblock.active,
                            siteException = adblock.isException(UrlPolicy.displayHost(url)),
                            host = UrlPolicy.displayHost(url),
                            blockedCount = { vm.blockedCount() },
                            onToggleSite = { vm.toggleSite(UrlPolicy.displayHost(url)); webView.reload() },
                            canForward = canForward,
                            onForward = { webView.goForward() },
                            onShare = { shareLink(context, title.ifBlank { url }, url) },
                            onCopy = { copyLink(context, url) },
                            onOutside = { openOutsideApp(context, url) },
                            // YouTube and co.: their own app plays better (background, quality, no consent page)
                            onVideoApp = if (isVideoSite(url)) ({ openInHandlerApp(context, url) }) else null,
                            // the support page (subscription, donation) opens in the user's browser, where they are signed in
                            support = support?.let { (link, name) -> Triple(name, link) { openOutsideApp(context, link.url) } },
                        )
                    },
                )
                if (loading && progress < 100) {
                    LinearWavyProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            error?.let { e ->
                Box(Modifier.fillMaxSize().padding(0.dp)) {
                    androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
                        CenterMessage(
                            stringResource(
                                when (e) {
                                    PageError.OFFLINE -> R.string.page_offline
                                    PageError.HOST -> R.string.page_host
                                    PageError.TIMEOUT -> R.string.page_timeout
                                    PageError.CERTIFICATE -> R.string.page_certificate
                                    PageError.BLOCKED -> R.string.page_blocked
                                    PageError.INSECURE -> R.string.page_insecure
                                    PageError.OTHER -> R.string.page_other
                                },
                                UrlPolicy.displayHost(url),
                            ),
                            onRetry = if (e == PageError.BLOCKED || e == PageError.CERTIFICATE || e == PageError.INSECURE) null else ({ error = null; webView.reload() }),
                            action = if (e == PageError.INSECURE) stringResource(R.string.browser_open_outside) to { openOutsideApp(context, startUrl) } else null,
                        )
                    }
                }
            }
        }
    }

    fullscreen?.let { (view, _) ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { fullscreen?.second?.invoke(); fullscreen = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            // the dialog has its own window: hide the status and navigation bar there (swipe brings them back)
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            DisposableEffect(window) {
                val bars = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
                bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                bars?.hide(WindowInsetsCompat.Type.systemBars())
                onDispose { }
            }
            AndroidView(factory = { view }, modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black))
        }
    }

    askOutside?.let { (target, scheme) ->
        AlertDialog(
            onDismissRequest = { askOutside = null },
            title = { Text(stringResource(R.string.outside_title)) },
            text = { Text(stringResource(R.string.outside_text, scheme)) },
            confirmButton = {
                TextButton(onClick = {
                    askOutside = null
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, target.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: ActivityNotFoundException) {
                        error = PageError.BLOCKED
                    }
                }) { Text(stringResource(R.string.outside_open)) }
            },
            dismissButton = { TextButton(onClick = { askOutside = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun BrowserMenu(
    adblockOn: Boolean,
    siteException: Boolean,
    host: String,
    blockedCount: () -> Int,
    onToggleSite: () -> Unit,
    canForward: Boolean,
    onForward: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onOutside: () -> Unit,
    /** Video pages only: open in the app Android has for the link (YouTube, NewPipe …). */
    onVideoApp: (() -> Unit)?,
    /** Source name, its support link and the action that opens the link. */
    support: Triple<String, Support, () -> Unit>?,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_actions)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.browser_forward)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) },
                enabled = canForward,
                onClick = { open = false; onForward() },
            )
            DropdownMenuItem(text = { Text(stringResource(R.string.share)) }, leadingIcon = { Icon(Icons.Filled.Share, null) }, onClick = { open = false; onShare() })
            DropdownMenuItem(
                text = { Text(stringResource(R.string.copy_link)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_link), null) },
                onClick = { open = false; onCopy() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.browser_open_outside)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_open_in_new), null) },
                onClick = { open = false; onOutside() },
            )
            onVideoApp?.let { go ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.open_in_video_app)) },
                    leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                    onClick = { open = false; go() },
                )
            }
            if (adblockOn && host.isNotEmpty()) {
                HorizontalDivider()
                // read when the menu opens: the count of this page, kept in memory only
                val count = remember(open) { blockedCount() }
                DropdownMenuItem(
                    text = {
                        Text(
                            if (siteException) stringResource(R.string.adblock_site_off, host)
                            else pluralStringResource(R.plurals.adblock_blocked, count, count),
                        )
                    },
                    enabled = false,
                    onClick = {},
                )
                DropdownMenuItem(
                    text = { Text(stringResource(if (siteException) R.string.adblock_site_enable else R.string.adblock_site_disable)) },
                    onClick = { open = false; onToggleSite() },
                )
            }
            // last, set apart: support the source (its page opens in the user's browser)
            support?.let { (name, link, go) ->
                HorizontalDivider()
                SupportMenuItem(name, link) { open = false; go() }
            }
        }
    }
}

private const val REFERER = "https://planetfoss.net/"
