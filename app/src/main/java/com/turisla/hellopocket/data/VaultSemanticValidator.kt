package com.turisla.hellopocket.data

import com.turisla.hellopocket.model.AttachmentManifestEntry
import com.turisla.hellopocket.model.Category
import com.turisla.hellopocket.model.CustomFieldType
import com.turisla.hellopocket.model.PasswordEntry
import com.turisla.hellopocket.model.PaymentCardBrand
import com.turisla.hellopocket.model.TotpEntry
import com.turisla.hellopocket.model.VaultItemType
import com.turisla.hellopocket.utils.AppConstants

/**
 * 校验“能够成功解密”之外的数据语义约束。
 *
 * AEAD 可以证明数据未被无密钥篡改，但不能保证旧版本或其他合法写入方生成的数据满足
 * 当前 UI 对唯一 ID、引用关系和颜色格式的要求。
 */
internal object VaultSemanticValidator {
    private val hexColorPattern = Regex("^#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")

    fun validateCore(
        passwords: List<PasswordEntry>,
        categories: List<Category>,
        attachments: List<AttachmentManifestEntry>,
    ) {
        requireUniqueNonBlankIds(passwords.map(PasswordEntry::getId), "password")
        requireUniqueNonBlankIds(categories.map(Category::getId), "category")
        requireUniqueNonBlankIds(attachments.map(AttachmentManifestEntry::id), "attachment")

        val categoryIds = categories.mapTo(mutableSetOf(), Category::getId)
        val attachmentIds = attachments.mapTo(mutableSetOf(), AttachmentManifestEntry::id)

        categories.forEach { category ->
            require(category.name.isNotBlank()) { "Vault contains a category with an empty name" }
            require(hexColorPattern.matches(category.color)) {
                "Vault contains an invalid category color"
            }
        }

        val encryptedFileNames = mutableSetOf<String>()
        attachments.forEach { attachment ->
            require(
                attachment.encryptedFileName.isNotBlank() &&
                    '/' !in attachment.encryptedFileName &&
                    '\\' !in attachment.encryptedFileName &&
                    attachment.encryptedFileName != "." &&
                    attachment.encryptedFileName != ".."
            ) {
                "Vault contains an invalid attachment file name"
            }
            require(encryptedFileNames.add(attachment.encryptedFileName)) {
                "Vault contains duplicate attachment file names"
            }
            require(attachment.size in 0..AppConstants.MAX_ATTACHMENT_SIZE_BYTES) {
                "Vault contains an attachment with an invalid size"
            }
        }

        passwords.forEach { entry ->
            require(
                entry.type == VaultItemType.PASSWORD ||
                    entry.type == VaultItemType.NOTE ||
                    entry.type == VaultItemType.PAYMENT_CARD
            ) { "Vault contains an unsupported entry type" }

            if (entry.type == VaultItemType.PAYMENT_CARD) {
                require(entry.title.isNotBlank()) {
                    "Vault contains a payment card with an empty title"
                }
                require(
                    entry.cardNumber.length in
                        AppConstants.MIN_PAYMENT_CARD_NUMBER_LENGTH..AppConstants.MAX_PAYMENT_CARD_NUMBER_LENGTH &&
                        entry.cardNumber.all { it in '0'..'9' }
                ) { "Vault contains an invalid payment card number" }
                require(entry.cardholderName.length <= AppConstants.MAX_CARDHOLDER_NAME_LENGTH) {
                    "Vault contains an oversized cardholder name"
                }
                require(entry.cardBrand != PaymentCardBrand.UNRECOGNIZED) {
                    "Vault contains an unsupported payment card brand"
                }
                require(entry.expirationMonth == 0 || entry.expirationMonth in 1..12) {
                    "Vault contains an invalid payment card expiration month"
                }
                require(
                    entry.expirationYear == 0 || entry.expirationYear in
                        AppConstants.MIN_EXPIRATION_YEAR..AppConstants.MAX_EXPIRATION_YEAR
                ) { "Vault contains an invalid payment card expiration year" }
                require((entry.expirationMonth == 0) == (entry.expirationYear == 0)) {
                    "Vault contains an incomplete payment card expiration date"
                }
                require(
                    entry.securityCode.isEmpty() ||
                        entry.securityCode.length in
                            AppConstants.MIN_SECURITY_CODE_LENGTH..AppConstants.MAX_SECURITY_CODE_LENGTH
                ) { "Vault contains an invalid payment card security code" }
                require(entry.securityCode.all { it in '0'..'9' }) {
                    "Vault contains an invalid payment card security code"
                }
            }

            val customFields = entry.customFieldsList
            require(customFields.size <= AppConstants.MAX_CUSTOM_FIELDS_PER_ENTRY) {
                "Vault entry contains too many custom fields"
            }
            requireUniqueNonBlankIds(customFields.map { it.id }, "custom field")
            customFields.forEach { field ->
                require(field.name.isNotBlank() && field.name == field.name.trim()) {
                    "Vault entry contains an invalid custom field name"
                }
                require('\n' !in field.name && '\r' !in field.name) {
                    "Vault entry contains a multi-line custom field name"
                }
                require(field.name.length <= AppConstants.MAX_CUSTOM_FIELD_NAME_LENGTH) {
                    "Vault entry contains an oversized custom field name"
                }
                require(field.value.length <= AppConstants.MAX_CUSTOM_FIELD_VALUE_LENGTH) {
                    "Vault entry contains an oversized custom field value"
                }
                require(
                    field.type == CustomFieldType.TEXT ||
                        field.type == CustomFieldType.CONCEALED
                ) {
                    "Vault entry contains an unsupported custom field type"
                }
            }

            val entryCategoryIds = entry.categoryIdsList
            require(entryCategoryIds.size == entryCategoryIds.toSet().size) {
                "Vault entry contains duplicate category references"
            }
            require(entryCategoryIds.all(categoryIds::contains)) {
                "Vault entry references a missing category"
            }

            val entryAttachmentIds = entry.attachmentIdsList
            require(entryAttachmentIds.size == entryAttachmentIds.toSet().size) {
                "Vault entry contains duplicate attachment references"
            }
            require(entryAttachmentIds.all(attachmentIds::contains)) {
                "Vault entry references a missing attachment"
            }
        }
    }

    fun validateTotpEntries(entries: List<TotpEntry>) {
        requireUniqueNonBlankIds(entries.map(TotpEntry::getId), "TOTP")
    }

    private fun requireUniqueNonBlankIds(ids: List<String>, itemName: String) {
        require(ids.all(String::isNotBlank)) { "Vault contains a $itemName with an empty id" }
        require(ids.size == ids.toSet().size) { "Vault contains duplicate $itemName ids" }
    }
}
