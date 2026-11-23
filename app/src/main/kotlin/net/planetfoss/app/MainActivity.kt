// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import net.planetfoss.app.data.SeenRepository
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import net.planetfoss.app.ui.PlanetFossNavigation
import net.planetfoss.app.ui.theme.PlanetFossTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** Deep links that arrive while the activity runs (singleTop), e.g. a tapped notification. */
    private val newIntents = Channel<Intent>(Channel.BUFFERED)

    /** Keyboard shortcuts no view used (docs/11-tablet-mode.md, T3). */
    private val shortcuts = kotlinx.coroutines.flow.MutableSharedFlow<net.planetfoss.app.ui.components.Shortcut>(extraBufferCapacity = 8)

    @Inject lateinit var seen: SeenRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        debugExtras(intent)
        intent = sanitize(intent)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PlanetFossTheme { PlanetFossNavigation(newIntents.receiveAsFlow(), shortcuts) }
        }
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.started()
        lifecycleScope.launch { seen.started() }
    }

    override fun onStop() {
        super.onStop()
        AppVisibility.stopped()
        // the app's own scope: the activity may be gone before the write finishes
        (application as PlanetFossApp).scope.launch { seen.stopped() }
    }

    /** Called only for keys no view consumed – letters typed into a text field never get here. */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        val shortcut = net.planetfoss.app.ui.components.Shortcut.of(event)
        if (shortcut != null && shortcuts.tryEmit(shortcut)) return true
        return super.onKeyDown(keyCode, event)
    }

    /** The app's shortcuts in Android's list (Meta + /). */
    override fun onProvideKeyboardShortcuts(data: MutableList<android.view.KeyboardShortcutGroup>, menu: android.view.Menu?, deviceId: Int) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        fun info(label: Int, key: Int, meta: Int = 0) = android.view.KeyboardShortcutInfo(getString(label), key, meta)
        val ctrl = android.view.KeyEvent.META_CTRL_ON
        data += android.view.KeyboardShortcutGroup(
            getString(R.string.keys_group_navigate),
            listOf(
                info(R.string.tab_latest, android.view.KeyEvent.KEYCODE_1, ctrl),
                info(R.string.tab_radar, android.view.KeyEvent.KEYCODE_2, ctrl),
                info(R.string.tab_discover, android.view.KeyEvent.KEYCODE_3, ctrl),
                info(R.string.tab_saved, android.view.KeyEvent.KEYCODE_4, ctrl),
                info(R.string.keys_search, android.view.KeyEvent.KEYCODE_F, ctrl),
                info(R.string.keys_refresh, android.view.KeyEvent.KEYCODE_R, ctrl),
                info(R.string.keys_close, android.view.KeyEvent.KEYCODE_ESCAPE),
                info(R.string.keys_settings, android.view.KeyEvent.KEYCODE_COMMA, ctrl),
            ),
        )
        data += android.view.KeyboardShortcutGroup(
            getString(R.string.keys_group_post),
            listOf(
                info(R.string.keys_next_prev, android.view.KeyEvent.KEYCODE_J),
                info(R.string.keys_next_prev, android.view.KeyEvent.KEYCODE_K),
                info(R.string.keys_open, android.view.KeyEvent.KEYCODE_ENTER),
                info(R.string.keys_outside, android.view.KeyEvent.KEYCODE_O),
                info(R.string.keys_save, android.view.KeyEvent.KEYCODE_B),
                info(R.string.keys_star, android.view.KeyEvent.KEYCODE_S),
            ),
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        debugExtras(intent)
        if (intent.data?.scheme == "planetfoss") newIntents.trySend(sanitize(intent))
    }

    /**
     * Debug builds only: `--ez ufo_day true` simulates World UFO Day (2 July) for Settings, `false` returns to
     * the real date. Release builds never read the extra (BuildConfig.DEBUG is a constant, R8 drops the code).
     */
    private fun debugExtras(incoming: Intent) {
        if (!BuildConfig.DEBUG || !incoming.hasExtra(EXTRA_UFO_DAY)) return
        net.planetfoss.app.ui.freiversum.UfoDay.clock =
            if (incoming.getBooleanExtra(EXTRA_UFO_DAY, false)) net.planetfoss.app.ui.freiversum.UfoDay.simulatedClock()
            else java.time.Clock.systemDefaultZone()
    }

    private companion object {
        const val EXTRA_UFO_DAY = "ufo_day"
    }

    /**
     * The activity is exported (launcher, planetfoss:// links), so any app can start it. Only the
     * link itself is passed on: Navigation's own extras (deep link ids and arguments) would let
     * another app open any screen – e.g. the internal browser on an address of its choice.
     */
    private fun sanitize(incoming: Intent): Intent {
        val data = incoming.data?.takeIf { it.scheme == "planetfoss" && incoming.action == Intent.ACTION_VIEW }
        return if (data != null) Intent(Intent.ACTION_VIEW, data, this, MainActivity::class.java)
        else Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClass(this, MainActivity::class.java)
    }
}
