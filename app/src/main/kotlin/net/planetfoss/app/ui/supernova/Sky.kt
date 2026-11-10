// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.supernova

import net.planetfoss.core.api.Comet
import net.planetfoss.core.api.Supernova

/** One entry of the Supernovas tab: a supernova or a comet. */
sealed interface Sky {
    val key: String

    data class Nova(val nova: Supernova) : Sky {
        override val key get() = "nova:" + nova.id
    }

    data class Rising(val comet: Comet) : Sky {
        override val key get() = "comet:" + comet.key
    }
}

/**
 * The tab's order: burning supernovas first, then the afterglow (each newest first, API order);
 * a comet after each supernova, so heavy and light cards take turns, the rest at the end. Never a
 * comet before the first supernova; without supernovas only the comets.
 */
fun skyOf(novas: List<Supernova>, comets: List<Comet>): List<Sky> {
    val rest = ArrayDeque(comets)
    return buildList {
        novas.sortedByDescending { it.burning }.forEach { n ->
            add(Sky.Nova(n))
            rest.removeFirstOrNull()?.let { add(Sky.Rising(it)) }
        }
        rest.forEach { add(Sky.Rising(it)) }
    }
}
