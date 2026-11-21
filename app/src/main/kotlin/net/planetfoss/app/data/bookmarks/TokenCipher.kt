// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.data.bookmarks

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Encrypts and decrypts a short secret (the bookmark service's API token). */
interface TokenCipher {
    fun encrypt(plain: String): String

    /** Null when the text cannot be read any more (key gone, e.g. after a restore onto another phone). */
    fun decrypt(stored: String): String?
}

/**
 * AES-GCM with a key that lives in the Android Keystore and never leaves it: the token in the settings file is useless
 * without this phone. No extra library (Tink) needed for one string.
 */
@Singleton
class KeystoreTokenCipher @Inject constructor() : TokenCipher {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val data = cipher.doFinal(plain.toByteArray())
        return Base64.getEncoder().encodeToString(cipher.iv + data)
    }

    override fun decrypt(stored: String): String? = runCatching {
        val raw = Base64.getDecoder().decode(stored)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, IV))
        String(cipher.doFinal(raw, IV, raw.size - IV))
    }.getOrNull()

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "bookmark-token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV = 12
    }
}
