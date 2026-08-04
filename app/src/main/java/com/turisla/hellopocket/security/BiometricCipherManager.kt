package com.turisla.hellopocket.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import com.turisla.hellopocket.utils.loggerI
import java.security.KeyStore
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class BiometricCipherManager {

    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private fun getOrCreateBiometricKey(): SecretKey {
        val existingKey = keyStore.getKey(BIOMETRIC_KEY_ALIAS, null)
        if (existingKey != null) return existingKey as SecretKey

        val params = KeyGenParameterSpec.Builder(
            BIOMETRIC_KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            setKeySize(KEY_LENGTH)
            setUserAuthenticationRequired(true)
            // Invalidate the key if the user adds or removes fingerprints
            setInvalidatedByBiometricEnrollment(true)
        }.build()

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGenerator.init(params)
        return keyGenerator.generateKey()
    }

    private fun getExistingBiometricKey(): SecretKey {
        return keyStore.getKey(BIOMETRIC_KEY_ALIAS, null) as? SecretKey
            ?: throw GeneralSecurityException("Biometric key is unavailable")
    }

    fun getCipherForEncryption(): Cipher {
        val biometricKey = getOrCreateBiometricKey()
        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, biometricKey)
        } catch (e: KeyPermanentlyInvalidatedException) {
            loggerI("Biometric key permanently invalidated. Deleting and recreating.")
            keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
            val newKey = getOrCreateBiometricKey()
            cipher.init(Cipher.ENCRYPT_MODE, newKey)
        }
        return cipher
    }

    fun getCipherForDecryption(iv: ByteArray): Cipher {
        // 已有密文只能由原密钥解开；解密路径绝不能静默创建一把新密钥。
        val biometricKey = getExistingBiometricKey()
        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        try {
            cipher.init(Cipher.DECRYPT_MODE, biometricKey, spec)
        } catch (error: KeyPermanentlyInvalidatedException) {
            keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
            throw error
        }
        return cipher
    }

    fun deleteBiometricKey() {
        if (keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
            keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val BIOMETRIC_KEY_ALIAS = "hello_pocket_biometric_key_alias"
        private const val AES_GCM_ALGORITHM = "AES/GCM/NoPadding"
        private const val KEY_LENGTH = 256
        private const val GCM_TAG_LENGTH = 128
    }
}
