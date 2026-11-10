// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

/**
 * Language of the app's own texts (menus, dialogs, notifications), independent of the
 * content language: the system's per-app language, also shown in Android's app settings.
 */
object AppLanguage {
    enum class Choice(val tag: String) { SYSTEM(""), DE("de"), EN("en") }

    fun current(context: Context): Choice {
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        val tag = if (locales.isEmpty) "" else locales[0].language
        return Choice.entries.firstOrNull { it.tag == tag } ?: Choice.SYSTEM
    }

    /** Android recreates the activity itself. */
    fun set(context: Context, choice: Choice) {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(choice.tag)
    }
}
