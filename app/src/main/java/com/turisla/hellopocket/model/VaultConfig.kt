package com.turisla.hellopocket.model

import kotlinx.serialization.Serializable

/**
 * 公开的配置文件 (vault_v2.json)
 * 存储vault版本、盐值和加密的密钥集
 */
@Serializable
data class VaultConfig(
    val version: Int,
    val salt: String, // Base64 编码的盐，用于派生主密码密钥
    val encryptedKeyset: String, // Base64 编码的加密 Tink Keyset (唯一的 StreamingAead Key)
    val integrityHash: String? = null, // Base64 编码的 SHA-256 哈希，校验 passwords.dat + categories.dat + manifest.dat
    val passwordHint: String? = null, // 主密码提示
    val kdfAlgorithm: String = "PBKDF2WithHmacSHA256",
    val kdfIterations: Int,
    val vaultId: String,
    // 当前格式固定为 1：密文绑定到保险库和逻辑文件名。
    val associatedDataVersion: Int,
)


/**
 * 加密的清单文件内容 (存入 manifest.dat)
 * 使用流式加密保护，包含附件列表等元数据
 */
@Serializable
data class VaultManifestV2(
    val schemaVersion: Int = 1,
    val vaultId: String,
    val generation: Long = 0,
    val createdAt: Long = 0L,
    val attachments: List<AttachmentManifestEntry> = emptyList(),
    // 核心密文摘要位于加密清单内，防止攻击者重算公开 hash 后拼接历史文件。
    val fileDigests: Map<String, String>,
    val fileSizes: Map<String, Long>,
    // 绑定盐、KDF、加密 Keyset 和密码提示；阻止只回滚明文配置来撤销改密。
    val configBinding: String,
)
