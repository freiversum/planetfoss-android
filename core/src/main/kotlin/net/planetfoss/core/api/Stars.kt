// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.core.api

import kotlinx.serialization.Serializable
import java.security.MessageDigest

/** A star task from planetfoss.net: bound to one item and ten minutes, valid once. */
@Serializable
data class StarChallenge(val nonce: String? = null, val bits: Int = 0, val error: String? = null)

/** Answer to starring or unstarring: the item's new count and, after starring, the token that takes the star back. */
@Serializable
data class StarAnswer(val ok: Boolean = false, val token: String? = null, val stars: Int? = null, val error: String? = null)

/**
 * The website's proof of work (`Stars::valid`, site.js `solve`): find n so that sha256("nonce:n")
 * starts with [bits] zero bits. About 130 000 tries at 17 bits – no account, no cookie, no identifier;
 * it only makes mass starring expensive.
 */
object ProofOfWork {
    fun solve(nonce: String, bits: Int): Long {
        require(bits in 1..32) { "bits" }
        val sha = MessageDigest.getInstance("SHA-256")
        val prefix = "$nonce:".toByteArray()
        var n = 0L
        while (true) {
            sha.update(prefix)
            if (zeroBits(sha.digest(n.toString().toByteArray()), bits)) return n
            n++
        }
    }

    fun valid(nonce: String, solution: Long, bits: Int): Boolean =
        zeroBits(MessageDigest.getInstance("SHA-256").digest("$nonce:$solution".toByteArray()), bits)

    private fun zeroBits(hash: ByteArray, bits: Int): Boolean {
        val full = bits / 8
        for (i in 0 until full) if (hash[i] != 0.toByte()) return false
        val rest = bits % 8
        return rest == 0 || (hash[full].toInt() and 0xFF) shr (8 - rest) == 0
    }
}
