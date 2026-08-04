package com.turisla.hellopocket.data

import com.google.protobuf.ByteString
import com.turisla.hellopocket.model.AttachmentManifestEntry
import com.turisla.hellopocket.model.Category
import com.turisla.hellopocket.model.PasswordEntry
import com.turisla.hellopocket.model.TotpEntry
import org.junit.Assert.assertThrows
import org.junit.Test

class VaultSemanticValidatorTest {

    @Test
    fun acceptsConsistentVaultData() {
        VaultSemanticValidator.validateCore(
            passwords = listOf(passwordEntry()),
            categories = listOf(category()),
            attachments = listOf(attachment()),
        )
        VaultSemanticValidator.validateTotpEntries(listOf(totpEntry()))
    }

    @Test
    fun rejectsDuplicatePasswordIds() {
        assertThrows(IllegalArgumentException::class.java) {
            VaultSemanticValidator.validateCore(
                passwords = listOf(passwordEntry(), passwordEntry()),
                categories = listOf(category()),
                attachments = listOf(attachment()),
            )
        }
    }

    @Test
    fun rejectsMissingCategoryReference() {
        assertThrows(IllegalArgumentException::class.java) {
            VaultSemanticValidator.validateCore(
                passwords = listOf(passwordEntry(categoryId = "missing-category")),
                categories = listOf(category()),
                attachments = listOf(attachment()),
            )
        }
    }

    @Test
    fun rejectsMissingAttachmentReference() {
        assertThrows(IllegalArgumentException::class.java) {
            VaultSemanticValidator.validateCore(
                passwords = listOf(passwordEntry(attachmentId = "missing-attachment")),
                categories = listOf(category()),
                attachments = listOf(attachment()),
            )
        }
    }

    @Test
    fun rejectsInvalidCategoryColor() {
        assertThrows(IllegalArgumentException::class.java) {
            VaultSemanticValidator.validateCore(
                passwords = listOf(passwordEntry()),
                categories = listOf(category(color = "not-a-color")),
                attachments = listOf(attachment()),
            )
        }
    }

    @Test
    fun rejectsDuplicateTotpIds() {
        assertThrows(IllegalArgumentException::class.java) {
            VaultSemanticValidator.validateTotpEntries(listOf(totpEntry(), totpEntry()))
        }
    }

    private fun passwordEntry(
        categoryId: String = "category-1",
        attachmentId: String = "attachment-1",
    ): PasswordEntry = PasswordEntry.newBuilder()
        .setId("password-1")
        .setTitle("Example")
        .addCategoryIds(categoryId)
        .addAttachmentIds(attachmentId)
        .build()

    private fun category(color: String = "#2196F3"): Category = Category.newBuilder()
        .setId("category-1")
        .setName("Personal")
        .setColor(color)
        .build()

    private fun attachment() = AttachmentManifestEntry(
        id = "attachment-1",
        encryptedFileName = "attachment-1.dat",
        originalFileName = "example.txt",
        mimeType = "text/plain",
        size = 10,
        createdAt = 1,
    )

    private fun totpEntry(): TotpEntry = TotpEntry.newBuilder()
        .setId("totp-1")
        .setSecret(ByteString.copyFromUtf8("JBSWY3DPEHPK3PXP"))
        .build()
}
