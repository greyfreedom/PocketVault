package com.turisla.hellopocket.security

import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class TinkCryptoManager {

    companion object {
        const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        const val DEFAULT_KDF_ITERATIONS = 600_000
        private const val KEY_LENGTH = 256
        private const val GCM_TAG_LENGTH = 128
        private const val AES_GCM_ALGORITHM = "AES/GCM/NoPadding"
        private val EMPTY_ASSOCIATED_DATA = ByteArray(0)
        
        init {
            try {
                AeadConfig.register()
                StreamingAeadConfig.register()
            } catch (e: GeneralSecurityException) {
                throw RuntimeException("Failed to register Tink", e)
            }
        }
    }

    /**
     * 生成唯一的 StreamingAead 密钥集
     * 这是vault的唯一加密密钥
     */
    fun generateVaultKeyset(): KeysetHandle {
        return KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM_HKDF_1MB"))
    }

    /**
     * 使用密码派生密钥加密密钥集
     * 返回Base64编码的加密数据
     */
    fun encryptKeyset(
        keysetHandle: KeysetHandle,
        password: String,
        salt: ByteArray,
        iterations: Int = DEFAULT_KDF_ITERATIONS
    ): String {
        val masterKey = deriveKey(password, salt, iterations)
        val aead = AesGcmJceAead(masterKey)

        val encryptedKeyset = TinkProtoKeysetFormat.serializeEncryptedKeyset(
            keysetHandle,
            aead,
            EMPTY_ASSOCIATED_DATA,
            RegistryConfiguration.get(),
        )
        return Base64.encodeToString(encryptedKeyset, Base64.NO_WRAP)
    }

    /**
     * 使用密码派生密钥解密密钥集
     */
    fun decryptKeyset(
        encryptedKeysetBase64: String,
        password: String,
        salt: ByteArray,
        iterations: Int = DEFAULT_KDF_ITERATIONS
    ): KeysetHandle {
        val masterKey = deriveKey(password, salt, iterations)
        val aead = AesGcmJceAead(masterKey)
        
        val encryptedKeyset = Base64.decode(encryptedKeysetBase64, Base64.NO_WRAP)
        return TinkProtoKeysetFormat.parseEncryptedKeyset(
            encryptedKeyset,
            aead,
            EMPTY_ASSOCIATED_DATA,
            RegistryConfiguration.get(),
        )
    }

    /**
     * 从密钥集获取StreamingAead原语
     * 用于所有数据的流式加密/解密
     */
    fun getStreamingAead(keysetHandle: KeysetHandle): StreamingAead {
        return keysetHandle.getPrimitive(
            RegistryConfiguration.get(),
            StreamingAead::class.java,
        )
    }

    /**
     * 将密钥集序列化为字节（用于生物识别）
     * 注意：返回的是明文密钥集，必须立即用生物识别密钥加密
     */
    fun writeKeysetToBytes(keysetHandle: KeysetHandle): ByteArray {
        return TinkProtoKeysetFormat.serializeKeyset(
            keysetHandle,
            InsecureSecretKeyAccess.get(),
            RegistryConfiguration.get(),
        )
    }

    /**
     * 从字节反序列化密钥集（用于生物识别）
     */
    fun readKeysetFromBytes(bytes: ByteArray): KeysetHandle {
        return TinkProtoKeysetFormat.parseKeyset(
            bytes,
            InsecureSecretKeyAccess.get(),
            RegistryConfiguration.get(),
        )
    }

    fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return salt
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): javax.crypto.SecretKey {
        require(iterations in DEFAULT_KDF_ITERATIONS..5_000_000) { "Invalid KDF iteration count" }
        val passwordChars = password.toCharArray()
        val spec = PBEKeySpec(passwordChars, salt, iterations, KEY_LENGTH)
        return try {
            val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
            val encoded = factory.generateSecret(spec).encoded
            try {
                SecretKeySpec(encoded, "AES")
            } finally {
                encoded.fill(0)
            }
        } finally {
            spec.clearPassword()
            passwordChars.fill('\u0000')
        }
    }

    /**
     * 辅助类：使用JCE AES-GCM实现Tink的Aead接口
     * 仅用于包装/解包装密钥集本身
     * 
     * 增强版本：
     * - 验证 IV 长度
     * - 支持 associatedData（虽然当前未使用，但保持接口完整性）
     * - 更严格的错误处理
     */
    private class AesGcmJceAead(private val key: javax.crypto.SecretKey) : Aead {
        override fun encrypt(plaintext: ByteArray, associatedData: ByteArray?): ByteArray {
            require(plaintext.isNotEmpty()) { "Plaintext cannot be empty" }
            
            val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            
            val iv = cipher.iv
            require(iv.size == 12) { "Invalid IV size: ${iv.size}, expected 12" }
            
            // 虽然当前不使用 AD，但保持接口一致性
            val ciphertext = if (associatedData != null && associatedData.isNotEmpty()) {
                cipher.updateAAD(associatedData)
                cipher.doFinal(plaintext)
            } else {
                cipher.doFinal(plaintext)
            }
            
            return iv + ciphertext
        }

        override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray?): ByteArray {
            if (ciphertext.size < 12) {
                throw GeneralSecurityException("Ciphertext too short: ${ciphertext.size} bytes")
            }
            
            val iv = ciphertext.copyOfRange(0, 12)
            val actualCiphertext = ciphertext.copyOfRange(12, ciphertext.size)
            
            val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            
            // 必须与加密时的 AD 一致
            if (associatedData != null && associatedData.isNotEmpty()) {
                cipher.updateAAD(associatedData)
            }
            
            return cipher.doFinal(actualCiphertext)
        }
    }

}
