// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.core.api

/** A list of items the API serves page by page. [key] identifies it in the local cache (without language). */
sealed interface ListTarget {
    val key: String
    val endpoint: String

    data object Home : ListTarget {
        override val key = "home"
        override val endpoint = "home"
    }

    data object Videos : ListTarget {
        override val key = "videos"
        override val endpoint = "videos"
    }

    /** The Sternstunden: items of the last 7 days that readers starred on planetfoss.net, most stars first. */
    data object Starred : ListTarget {
        override val key = "starred"
        override val endpoint = "starred"
    }

    /** All reports of one story (bundle), oldest first – the website's /buendel/<id>/. */
    data class Bundle(val id: Long) : ListTarget {
        override val key = "bundle:$id"
        override val endpoint = "bundle/$id"
    }

    data class Rubric(val slug: String) : ListTarget {
        override val key = "rubric:$slug"
        override val endpoint = "rubric/${safe(slug)}"
    }

    data class Topic(val topicKey: String) : ListTarget {
        override val key = "topic:$topicKey"
        override val endpoint = "topic/${safe(topicKey)}"
    }

    data class Source(val slug: String) : ListTarget {
        override val key = "source:$slug"
        override val endpoint = "source/${safe(slug)}"
    }

    private companion object {
        /** Slugs and keys are [a-z0-9-] on the server; anything else must not reach the path. */
        fun safe(value: String): String {
            require(value.isNotEmpty() && value.length <= 80 && value.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }) { "bad key" }
            return value
        }
    }
}
