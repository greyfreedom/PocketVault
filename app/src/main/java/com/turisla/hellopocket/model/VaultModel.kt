
package com.turisla.hellopocket.model

import kotlinx.serialization.Serializable

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
