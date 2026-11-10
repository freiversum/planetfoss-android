// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProofOfWorkTest {
    @Test
    fun findsTheSameSolutionAsTheWebsite() {
        // computed with Python's hashlib for nonce "1.2.ab.cd" and the website's 17 bits
        assertEquals(118744L, ProofOfWork.solve("1.2.ab.cd", 17))
        assertTrue(ProofOfWork.valid("1.2.ab.cd", 118744, 17))
        assertFalse(ProofOfWork.valid("1.2.ab.cd", 118743, 17))
    }
}
