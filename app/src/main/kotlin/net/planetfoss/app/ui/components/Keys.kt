// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.components

import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridItemInfo
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import net.planetfoss.app.R
import net.planetfoss.app.ui.theme.MetaStyle
import net.planetfoss.app.ui.theme.Space
import net.planetfoss.core.api.Item

/**
 * Keyboard shortcuts (docs/11-tablet-mode.md, T3, D-T5). The activity turns key presses that no view used into these
 * (so typing in a search field never triggers one); navigation handles the global ones and hands the rest to the list.
 */
enum class Shortcut {
    Latest, Radar, Discover, Saved, Search, Refresh, Settings, Help, Close, Next, Prev, Open, Outside, Save, Star;

    companion object {
        /** The shortcut of a key press, or null. `/` and `?` by character, so they work with any keyboard layout. */
        fun of(e: KeyEvent): Shortcut? {
            if (e.isAltPressed || e.isMetaPressed) return null
            if (e.isCtrlPressed) {
                return when (e.keyCode) {
                    KeyEvent.KEYCODE_1 -> Latest
                    KeyEvent.KEYCODE_2 -> Radar
                    KeyEvent.KEYCODE_3 -> Discover
                    KeyEvent.KEYCODE_4 -> Saved
                    KeyEvent.KEYCODE_F -> Search
                    KeyEvent.KEYCODE_R -> Refresh
                    KeyEvent.KEYCODE_COMMA -> Settings
                    else -> null
                }
            }
            when (e.getUnicodeChar(e.metaState).toChar()) {
                '/' -> return Search
                '?' -> return Help
            }
            if (e.isShiftPressed) return null
            return when (e.keyCode) {
                KeyEvent.KEYCODE_F5 -> Refresh
                KeyEvent.KEYCODE_ESCAPE -> Close
                KeyEvent.KEYCODE_J -> Next
                KeyEvent.KEYCODE_K -> Prev
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> Open
                KeyEvent.KEYCODE_O -> Outside
                KeyEvent.KEYCODE_B -> Save
                KeyEvent.KEYCODE_S -> Star
                else -> null
            }
        }
    }
}

/** The shortcuts for the list on screen (the left one beside a detail pane); empty elsewhere. */
val LocalListKeys = staticCompositionLocalOf<Flow<Shortcut>> { emptyFlow() }

/** Id of the post the keyboard cursor (J/K) stands on; its card gets a ring. */
val LocalKeyCursor = compositionLocalOf<Long?> { null }

/**
 * The keyboard in a list of posts: J/K move a cursor from card to card (scrolling it to the top), Enter opens the post,
 * O opens it in the browser outside the app, B saves it, S stars it; Ctrl+R / F5 refreshes. Listens only while the
 * list is on screen and in front (resumed).
 */
@Composable
fun KeyCursor(list: LazyStaggeredGridState, items: () -> List<Item>, onRefresh: () -> Unit, content: @Composable () -> Unit) {
    val keys = LocalListKeys.current
    val actions by rememberUpdatedState(LocalItemActions.current)
    val owner = LocalLifecycleOwner.current
    val itemsNow by rememberUpdatedState(items)
    val refreshNow by rememberUpdatedState(onRefresh)
    var cursor by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(keys, owner) {
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            keys.collect { k ->
                when (k) {
                    Shortcut.Refresh -> refreshNow()
                    Shortcut.Next, Shortcut.Prev -> move(list, cursor, forward = k == Shortcut.Next)?.let { cursor = it }
                    Shortcut.Open, Shortcut.Outside, Shortcut.Save, Shortcut.Star -> {
                        val item = cursor?.let { id -> itemsNow().firstOrNull { it.id == id } } ?: return@collect
                        when (k) {
                            Shortcut.Open -> actions.openItem(item)
                            Shortcut.Outside -> actions.openOutside(item)
                            Shortcut.Save -> actions.toggleSave(item)
                            else -> actions.toggleStar(item)
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
    CompositionLocalProvider(LocalKeyCursor provides cursor, content = content)
}

/** Post id of a card in the grid: the stream's cards are keyed by id, the front page's by "front-<id>". */
private fun idOf(key: Any): Long? = when (key) {
    is Long -> key
    is String -> key.removePrefix("front-").toLongOrNull()
    else -> null
}

/** The next or previous post card from [cursor] (the first one in view without a cursor), scrolled to the top. */
private suspend fun move(list: LazyStaggeredGridState, cursor: Long?, forward: Boolean): Long? {
    fun cards(): List<LazyStaggeredGridItemInfo> =
        list.layoutInfo.visibleItemsInfo.filter { it.contentType == "item" || it.contentType == "lead" }.sortedBy { it.index }
    val cards = cards()
    val cur = cards.firstOrNull { idOf(it.key) == cursor }
    val target = when {
        cur == null -> if (forward) cards.firstOrNull { it.offset.y >= 0 } ?: cards.firstOrNull() else cards.lastOrNull()
        forward -> cards.firstOrNull { it.index > cur.index } ?: run {
            // the cursor's card is the last one in view: bring it up, the next one follows below it
            list.scrollToItem(cur.index)
            cards().firstOrNull { it.index > cur.index }
        }
        else -> cards.lastOrNull { it.index < cur.index } ?: run {
            if (cur.index == 0) return null
            list.scrollToItem(maxOf(0, cur.index - 4))
            cards().lastOrNull { it.index < cur.index }
        }
    } ?: return null
    list.animateScrollToItem(target.index)
    return idOf(target.key)
}

/** One line of the shortcut overview: what it does and the keys. */
private data class KeyLine(@StringRes val label: Int, val keys: String)

/** The shortcut overview (`?`): the same list Android shows with Meta + /. */
@Composable
fun KeysDialog(onDismiss: () -> Unit) {
    val ctrl = stringResource(R.string.key_ctrl)
    val groups = listOf(
        R.string.keys_group_navigate to listOf(
            KeyLine(R.string.keys_tabs, "$ctrl 1–4"),
            KeyLine(R.string.keys_search, "/  ·  $ctrl F"),
            KeyLine(R.string.keys_refresh, "$ctrl R  ·  F5"),
            KeyLine(R.string.keys_close, "Esc"),
            KeyLine(R.string.keys_settings, "$ctrl ,"),
            KeyLine(R.string.keys_help, "?"),
        ),
        R.string.keys_group_post to listOf(
            KeyLine(R.string.keys_next_prev, "J  ·  K"),
            KeyLine(R.string.keys_open, "Enter"),
            KeyLine(R.string.keys_outside, "O"),
            KeyLine(R.string.keys_save, "B"),
            KeyLine(R.string.keys_star, "S"),
            KeyLine(R.string.keys_menu, stringResource(R.string.keys_right_click)),
        ),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = { Text(stringResource(R.string.keys_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                groups.forEach { (title, lines) ->
                    Text(stringResource(title).uppercase(), style = MetaStyle, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = Space.s).semantics { heading() })
                    lines.forEachIndexed { i, l ->
                        if (i > 0) HorizontalDivider()
                        Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                            Text(stringResource(l.label), modifier = Modifier.weight(1f))
                            Text(l.keys, style = MetaStyle, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
    )
}
