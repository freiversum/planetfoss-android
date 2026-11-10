// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.browser

import android.util.Log
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.planetfoss.app.BuildConfig
import net.planetfoss.app.adblock.AdblockRepository
import net.planetfoss.app.adblock.AdblockState
import net.planetfoss.app.adblock.RequestTypes
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * The tracker protection's side of the internal browser. [intercept] runs on WebView's network
 * threads, so it reads only volatile snapshots. The count of blocked requests is kept in
 * memory for the current page only – no statistics are stored or sent.
 */
@HiltViewModel
class BrowserViewModel @Inject constructor(private val adblock: AdblockRepository) : ViewModel() {
    val state: StateFlow<AdblockState> = adblock.state.stateIn(viewModelScope, SharingStarted.Eagerly, AdblockState())

    @Volatile private var snapshot = AdblockState()

    @Volatile var currentPageHost: String? = null
    private val blocked = AtomicInteger(0)

    init {
        viewModelScope.launch {
            state.collect { s ->
                snapshot = s
                if (s.active) adblock.ensureLoaded()
            }
        }
        // service workers fetch outside the page's WebViewClient; the page host is the open page's
        ServiceWorkerController.getInstance().setServiceWorkerClient(object : ServiceWorkerClient() {
            override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? = intercept(request, currentPageHost)
        })
    }

    fun intercept(request: WebResourceRequest, pageHost: String?): WebResourceResponse? {
        val s = snapshot
        if (!s.active || s.isException(pageHost)) return null
        val req = RequestTypes.request(request, pageHost) ?: return null
        val rule = adblock.decide(req) ?: return null
        blocked.incrementAndGet()
        // debug builds only, local logcat: which host was blocked by which rule (for the device test)
        if (BuildConfig.DEBUG) Log.d("PlanetFOSS", "adblock: blocked ${req.host} (${req.type}) by $rule")
        return RequestTypes.blockedResponse()
    }

    fun blockedCount(): Int = blocked.get()

    fun newPage(host: String?) {
        if (host != currentPageHost) blocked.set(0)
        currentPageHost = host
    }

    /** “Do not block on this site” / “block again” from the browser menu. */
    fun toggleSite(host: String) {
        viewModelScope.launch {
            val h = AdblockRepository.normalizeHost(host) ?: return@launch
            if (snapshot.isException(host)) {
                // remove the exception that covers this host (it may be stored for a parent domain)
                snapshot.exceptions.filter { host == it || host.endsWith(".$it") }.forEach { adblock.removeException(it) }
            } else {
                adblock.addException(h)
            }
        }
    }

    override fun onCleared() {
        ServiceWorkerController.getInstance().setServiceWorkerClient(null)
    }
}
