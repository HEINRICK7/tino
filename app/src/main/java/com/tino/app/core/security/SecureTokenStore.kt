package com.tino.app.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    data class AuthSession(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAtEpochMs: Long?,
    )

    private val preferences = context.getSharedPreferences("tino_secure_session", Context.MODE_PRIVATE)
    private val keyAlias = "tino-auth-token"

    fun save(token: String) {
        saveEncrypted(token)
    }

    fun saveSession(
        accessToken: String,
        refreshToken: String?,
        expiresAtEpochMs: Long?,
    ) {
        require(accessToken.isNotBlank()) { "O access token não pode ser vazio." }
        val payload = JSONObject().apply {
            put("access_token", accessToken)
            if (refreshToken == null) put("refresh_token", JSONObject.NULL) else put("refresh_token", refreshToken)
            if (expiresAtEpochMs == null) put("expires_at", JSONObject.NULL) else put("expires_at", expiresAtEpochMs)
        }.toString()
        saveEncrypted(payload)
    }

    fun readSession(): AuthSession? {
        val value = readRaw() ?: return null
        return runCatching {
            val json = JSONObject(value)
            AuthSession(
                accessToken = json.getString("access_token"),
                refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() && it != "null" },
                expiresAtEpochMs = if (json.isNull("expires_at")) null else json.getLong("expires_at"),
            )
        }.getOrElse {
            // Compatibility with sessions written before refresh-token support.
            AuthSession(value, null, null)
        }
    }

    fun read(): String? = readSession()?.accessToken

    private fun saveEncrypted(value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val encoded = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        preferences.edit().putString("token", encoded).putString("iv", iv).apply()
    }

    private fun readRaw(): String? {
        val encoded = preferences.getString("token", null) ?: return null
        val iv = preferences.getString("iv", null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), javax.crypto.spec.GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        return String(cipher.doFinal(Base64.decode(encoded, Base64.NO_WRAP)), StandardCharsets.UTF_8)
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
