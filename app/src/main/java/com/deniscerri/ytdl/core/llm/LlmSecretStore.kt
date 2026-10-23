package com.deniscerri.ytdl.core.llm

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores API keys per provider id. */
interface LlmSecretStore {
    fun get(providerId: String): String?
    fun put(providerId: String, secret: String)
    fun remove(providerId: String)
    fun has(providerId: String): Boolean
}

/**
 * API keys encrypted with AES-256-GCM under a non-exportable Android Keystore key and kept in
 * their own private preferences file. They are never written to the default preferences, which
 * the app's settings backup exports. If the Keystore key is lost (for example after a restore to
 * another device) the stored keys cannot be decrypted and are dropped; the user enters them again.
 */
class KeystoreLlmSecretStore(context: Context) : LlmSecretStore {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun get(providerId: String): String? {
        val stored = prefs.getString(providerId, null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, IV_SIZE)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Dropping an API key that can no longer be decrypted (${e.javaClass.simpleName})")
            remove(providerId)
            null
        }
    }

    override fun put(providerId: String, secret: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        check(iv.size == IV_SIZE)
        val encrypted = iv + cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        prefs.edit { putString(providerId, Base64.encodeToString(encrypted, Base64.NO_WRAP)) }
    }

    override fun remove(providerId: String) {
        prefs.edit { remove(providerId) }
    }

    override fun has(providerId: String): Boolean = prefs.contains(providerId)

    @Synchronized
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val TAG = "LlmSecretStore"
        private const val PREFS_NAME = "llm_secrets"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "ytdlnis_llm_api_keys"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_BITS = 128
    }
}
