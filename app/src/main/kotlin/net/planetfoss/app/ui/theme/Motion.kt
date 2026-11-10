// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.theme

import androidx.activity.BackEventCompat
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

/**
 * Page motion after Moshidon (and the official Mastodon app it comes from): a new page fades in
 * while it travels only a short way (100 dp, not the whole width) and covers the one below, which
 * stays where it is; going back, the page fades and slides off in 200 ms and the page below is
 * simply there. Short distance and no movement underneath make it feel quick. The back gesture
 * moves the page the same way under the finger.
 */
object Motion {
    /** CSS `ease`, Moshidon's `cubic_bezier_default`: fast start, long soft landing. */
    val Ease = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    const val ENTER_MS = 300
    const val EXIT_MS = 200

    /** Tabs swap in place (Material's fade-through): out quickly, in a little later. */
    const val TAB_OUT_MS = 90
    const val TAB_IN_MS = 210
}

/**
 * NavHost transitions. [shiftPx] is 100 dp in pixels, negative in right-to-left layouts; [isTab]
 * tells top-level tabs apart from pushed pages, so switching tabs fades instead of sliding.
 */
class PageTransitions(private val shiftPx: Int, private val isTab: (NavBackStackEntry) -> Boolean) {
    private fun AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs() = isTab(initialState) && isTab(targetState)

    private val tabIn = fadeIn(tween(Motion.TAB_IN_MS, delayMillis = Motion.TAB_OUT_MS, easing = Motion.Ease))
    private val tabOut = fadeOut(tween(Motion.TAB_OUT_MS, easing = Motion.Ease))

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (betweenTabs()) tabIn
        else fadeIn(tween(Motion.ENTER_MS, easing = Motion.Ease)) + slideInHorizontally(tween(Motion.ENTER_MS, easing = Motion.Ease)) { shiftPx }
    }

    /** The page below stays still and is covered. */
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (betweenTabs()) tabOut else ExitTransition.KeepUntilTransitionsFinished
    }

    /** Back: the page below is just there … */
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (betweenTabs()) tabIn else EnterTransition.None
    }

    /** … and the top page fades and slides off the short way. */
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (betweenTabs()) tabOut else slideOff(shiftPx)
    }

    /**
     * The back gesture. Navigation's own default shrinks the page to 70 % (the same in every app); here
     * the page follows the finger the short way and fades, away from the edge the swipe started at.
     */
    val predictivePopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> EnterTransition = { popEnter() }
    val predictivePopExit: AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> ExitTransition = { edge ->
        if (betweenTabs()) tabOut else slideOff(if (edge == BackEventCompat.EDGE_RIGHT) -shiftPx else shiftPx)
    }

    private fun slideOff(px: Int) =
        fadeOut(tween(Motion.EXIT_MS, easing = Motion.Ease)) + slideOutHorizontally(tween(Motion.EXIT_MS, easing = Motion.Ease)) { px }
}
