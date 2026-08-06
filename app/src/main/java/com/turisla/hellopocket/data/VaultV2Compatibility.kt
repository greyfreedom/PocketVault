package com.turisla.hellopocket.data

import com.turisla.hellopocket.model.AttachmentManifestEntry
import com.turisla.hellopocket.model.VaultConfig
import com.turisla.hellopocket.security.TinkCryptoManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * 仅用于读取早期 V2 配置。当前 [VaultConfig] 继续保持严格字段要求，避免新代码意外写回旧默认值。
 */
@Serializable
internal data class CompatibleVaultConfig(
    val version: Int,
    val salt: String,
    val encryptedKeyset: String,
    val integrityHash: String? = null,
    val passwordHint: String? = null,
    val kdfAlgorithm: String = TinkCryptoManager.PBKDF2_ALGORITHM,
    val kdfIterations: Int = TinkCryptoManager.LEGACY_V2_KDF_ITERATIONS,
    val vaultId: String = "",
    val associatedDataVersion: Int = 0,
) {
    fun toVaultConfig(): VaultConfig {
        return VaultConfig(
            version = version,
            salt = salt,
            encryptedKeyset = encryptedKeyset,
            integrityHash = integrityHash,
            passwordHint = passwordHint,
            kdfAlgorithm = kdfAlgorithm,
            kdfIterations = kdfIterations,
            vaultId = vaultId,
            associatedDataVersion = associatedDataVersion,
        )
    }

    /**
     * 只有公开配置本身明确带有旧 V2 特征时，才允许进入一次性兼容迁移路径。
     * 这样当前格式不能通过删减加密清单字段被降级成“旧格式”。
     */
    fun requiresOneTimeUpgrade(): Boolean {
        return allowsLegacyEncryptedMetadata() ||
            integrityHash.isNullOrBlank()
    }

    fun allowsLegacyEncryptedMetadata(): Boolean {
        return kdfIterations < TinkCryptoManager.DEFAULT_KDF_ITERATIONS ||
            associatedDataVersion == 0 ||
            vaultId.isBlank()
    }
}

/**
 * 早期 V2 的加密清单缺少认证快照字段；只在已由旧配置触发的迁移路径中使用。
 */
@Serializable
internal data class CompatibleVaultManifestV2(
    val schemaVersion: Int = 1,
    val vaultId: String = "",
    val generation: Long = 0,
    val createdAt: Long = 0L,
    val attachments: List<AttachmentManifestEntry> = emptyList(),
    val fileDigests: Map<String, String> = emptyMap(),
    val fileSizes: Map<String, Long> = emptyMap(),
    val configBinding: String = "",
)

internal fun decodeCompatibleVaultConfig(json: String): CompatibleVaultConfig {
    return Json.decodeFromString(json)
}

internal fun decodeCompatibleVaultManifest(json: String): CompatibleVaultManifestV2 {
    return Json.decodeFromString(json)
}

/**
 * 2.4.x 及更早版本按带默认值的数据类编码配置绑定；100,000 次迭代会被省略。
 * 保留这一精确编码只为认证旧清单，迁移后的清单始终使用当前绑定格式。
 */
internal fun calculateCompatibleVaultConfigBindingBytes(config: CompatibleVaultConfig): ByteArray {
    val canonicalConfig = Json.encodeToString(config.copy(integrityHash = null))
    return MessageDigest.getInstance("SHA-256")
        .digest(canonicalConfig.toByteArray(Charsets.UTF_8))
}
