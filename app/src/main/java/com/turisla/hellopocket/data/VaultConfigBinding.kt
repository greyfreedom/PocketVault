package com.turisla.hellopocket.data

import com.turisla.hellopocket.model.VaultConfig
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * 生成不包含可重算 integrityHash 的稳定配置绑定。
 */
internal fun calculateVaultConfigBindingBytes(config: VaultConfig): ByteArray {
    val canonicalConfig = Json.encodeToString(config.copy(integrityHash = null))
    return MessageDigest.getInstance("SHA-256")
        .digest(canonicalConfig.toByteArray(Charsets.UTF_8))
}
