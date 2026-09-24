package com.yourapp.gemmatest.engine

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID

private const val KEYSTORE_ALIAS = "irachat_device_key"
private const val PREFS_NAME = "irachat_device_prefs"
private const val PREF_DEVICE_ID = "device_id"
private const val PREF_REGISTERED = "registered"

// Wraps a per-device EC key pair in AndroidKeyStore — the private key never
// leaves secure hardware (where supported). Used to sign every online
// request so the gateway can verify it came from a real registered device
// without any shared/embeddable secret in the app.
class DeviceAuth(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    val deviceId: String
        get() {
            prefs.getString(PREF_DEVICE_ID, null)?.let { return it }
            val newId = UUID.randomUUID().toString()
            prefs.edit().putString(PREF_DEVICE_ID, newId).apply()
            return newId
        }

    val isRegistered: Boolean
        get() = prefs.getBoolean(PREF_REGISTERED, false)

    fun markRegistered() {
        prefs.edit().putBoolean(PREF_REGISTERED, true).apply()
    }

    private fun ensureKeyPair() {
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) return
        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore"
        )
        generator.initialize(
            KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .build()
        )
        generator.generateKeyPair()
    }

    // base64 DER SubjectPublicKeyInfo — matches what the gateway's
    // cryptography.hazmat.load_der_public_key() expects on the other end
    fun publicKeyBase64(): String {
        ensureKeyPair()
        val cert = keyStore.getCertificate(KEYSTORE_ALIAS)
        return Base64.encodeToString(cert.publicKey.encoded, Base64.NO_WRAP)
    }

    fun sign(message: ByteArray): String {
        ensureKeyPair()
        val privateKey = keyStore.getKey(KEYSTORE_ALIAS, null) as PrivateKey
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(message)
        return Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
    }
}
