package br.unasp.boacao.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

data class StoredLoginCredentials(
    val email: String,
    val password: String
)

class BiometricCredentialStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasCredentials(): Boolean =
        preferences.contains(KEY_CIPHER_TEXT) && preferences.contains(KEY_IV)

    fun canUseBiometricLogin(): Boolean =
        hasCredentials() && canAuthenticateWithBiometrics()

    fun canAuthenticateWithBiometrics(): Boolean =
        BiometricManager.from(appContext)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun save(credentials: StoredLoginCredentials): Result<Unit> = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())

        val payload = JSONObject()
            .put("email", credentials.email)
            .put("password", credentials.password)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)

        val encrypted = cipher.doFinal(payload)
        preferences.edit()
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_CIPHER_TEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun read(): Result<StoredLoginCredentials> = runCatching {
        val iv = preferences.getString(KEY_IV, null) ?: error("Login biometrico nao configurado.")
        val cipherText = preferences.getString(KEY_CIPHER_TEXT, null) ?: error("Login biometrico nao configurado.")

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
        )

        val decrypted = cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP))
        val json = JSONObject(String(decrypted, StandardCharsets.UTF_8))

        StoredLoginCredentials(
            email = json.getString("email"),
            password = json.getString("password")
        )
    }.onFailure {
        clear()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val keySpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "boa_acao_biometric_login_key"
        const val PREFS_NAME = "boa_acao_biometric_login"
        const val KEY_IV = "iv"
        const val KEY_CIPHER_TEXT = "cipher_text"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
