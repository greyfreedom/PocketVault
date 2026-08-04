
package com.turisla.hellopocket.model

import kotlinx.serialization.Serializable

/**
 * 保险库清单文件的数据模型，描述了整个保险库的结构。
 * 这个文件本身会被序列化为JSON，然后使用主密码加密存储。
 *
 * @property version 清单的版本号，用于未来的数据迁移。
 * @property salt 用于派生主密钥的盐值，以Base64字符串存储。
 * @property dataFiles 一个映射，键是数据类型（如 "passwords", "notes"），值是对应的加密数据文件名。
 * @property attachments 一个列表，包含所有附件的元数据。
 */
@Serializable
data class VaultManifest(
    val version: Int = 1,
    val salt: String,
    val dataFiles: Map<String, String>,
    val attachments: List<AttachmentManifestEntry> = emptyList()
)

/**
 * 附件清单条目的数据模型。
 *
 * @property id 附件的唯一标识符 (UUID)。
 * @property encryptedFileName 附件在 attachments/ 目录中加密后的文件名。
 * @property originalFileName 附件的原始文件名。
 * @property mimeType 附件的MIME类型，例如 "image/jpeg"。
 * @property size 附件的原始文件大小（以字节为单位）。
 * @property createdAt 附件的创建时间戳。
 */
@Serializable
data class AttachmentManifestEntry(
    val id: String,
    val encryptedFileName: String,
    val originalFileName: String,
    val mimeType: String,
    val size: Long,
    val createdAt: Long
)
