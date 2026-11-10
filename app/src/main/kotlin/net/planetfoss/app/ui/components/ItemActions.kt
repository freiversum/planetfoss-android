// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import net.planetfoss.core.api.Category
import net.planetfoss.core.api.Item
import net.planetfoss.core.api.Source
import net.planetfoss.core.api.TopicRef

/** What a card can do. Provided once at the navigation root, so cards need no callbacks threaded through. */
@Stable
class ItemActions(
    /** Open in the browser mode the user chose. */
    val open: (String) -> Unit,
    /**
     * Open an article: like [open], but in the user's browser if its source asks for that, with its support link.
     * [place] (liste, supernova) goes with the count for the sources' reach.
     */
    val openFrom: (Item, String) -> Unit,
    /** Open the other way: outside the app when the internal browser is chosen, else inside. */
    val openOther: (String) -> Unit,
    /** Label of [openOther] (“open in browser” / “open in the app”). */
    val openOtherLabel: Int,
    val toggleSave: (Item) -> Unit,
    /** Star the item for the Sternstunden on planetfoss.net, or take the star back. */
    val toggleStar: (Item) -> Unit,
    val rubric: (Category) -> Unit,
    val topic: (TopicRef) -> Unit,
    val source: (Source) -> Unit,
    /** All reports of a story as cards (the website's bundle page). */
    val bundle: (Long) -> Unit = {},
) {
    fun openItem(item: Item) = openFrom(item, "liste")
}

val LocalItemActions = staticCompositionLocalOf<ItemActions> { error("ItemActions not provided") }

/** Ids of saved items, to draw the bookmark filled or empty. */
val LocalSavedIds = compositionLocalOf { emptySet<Long>() }

/** End of the previous visit (epoch ms): posts published later carry the “new” marker. Null = nothing is new (switched off, first start). */
val LocalNewSince = compositionLocalOf<Long?> { null }

/** “All seen” (the bar above the new posts). */
val LocalAllSeen = staticCompositionLocalOf<() -> Unit> { {} }

/** Stars set from this phone, counts the server just reported, and stars on their way. */
data class StarUi(val mine: Set<Long> = emptySet(), val counts: Map<Long, Int> = emptyMap(), val busy: Set<Long> = emptySet()) {
    fun count(item: Item): Int = counts[item.id] ?: item.stars
}

val LocalStars = compositionLocalOf { StarUi() }

/** Android share sheet with title and link. */
fun shareLink(context: Context, title: String, url: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, title)
        .putExtra(Intent.EXTRA_TEXT, "$title\n$url")
    context.startActivity(Intent.createChooser(send, null))
}

/** Share sheet with the bare link (a post to boost: Mastodon apps open it from the link alone). */
fun shareUrl(context: Context, url: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
    context.startActivity(Intent.createChooser(send, null))
}

/** Copy a link; Android shows its own confirmation. */
fun copyLink(context: Context, url: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(url, url))
}
