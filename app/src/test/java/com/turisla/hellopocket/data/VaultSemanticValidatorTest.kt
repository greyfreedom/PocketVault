package com.turisla.hellopocket.data

import com.google.protobuf.ByteString
import com.turisla.hellopocket.model.AttachmentManifestEntry
import com.turisla.hellopocket.model.Category
import com.turisla.hellopocket.model.CustomField
import com.turisla.hellopocket.model.CustomFieldType
import com.turisla.hellopocket.model.PasswordEntry
import com.turisla.hellopocket.model.PaymentCardBrand
import com.turisla.hellopocket.model.TotpEntry
import com.turisla.hellopocket.model.VaultItemType
import com.turisla.hellopocket.utils.AppConstants
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

    @Test
    fun acceptsOrderedTextAndConcealedCustomFields() {
        VaultSemanticValidator.validateCore(
            passwords = listOf(
                passwordEntry(
                    customFields = listOf(
                        customField(id = "field-1", type = CustomFieldType.TEXT),
                        customField(id = "field-2", type = CustomFieldType.CONCEALED),
                    )
                )
            ),
            categories = listOf(category()),
            attachments = listOf(attachment()),
        )
    }

    @Test
    fun rejectsDuplicateCustomFieldIdsWithinAnEntry() {
        assertThrows(IllegalArgumentException::class.java) {
            VaultSemanticValidator.validateCore(
                passwords = listOf(
                    passwordEntry(
                        customFields = listOf(
                            customField(id = "duplicate"),
                            customField(id = "duplicate"),
                        )
                    )
                ),
                categories = listOf(category()),
                attachments = listOf(attachment()),
            )
        }
    }

    @Test
    fun rejectsBlankPaddedOrMultilineCustomFieldNames() {
        listOf("", " Field ", "Line\nbreak").forEachIndexed { index, invalidName ->
            assertThrows("invalid name at index $index", IllegalArgumentException::class.java) {
                VaultSemanticValidator.validateCore(
                    passwords = listOf(
                        passwordEntry(
                            customFields = listOf(customField(name = invalidName))
                        )
                    ),
                    categories = listOf(category()),
                    attachments = listOf(attachment()),
                )
            }
        }
    }

    @Test
    fun rejectsUnsupportedCustomFieldType() {
        assertThrows(IllegalArgumentException::class.java) {
            VaultSemanticValidator.validateCore(
                passwords = listOf(
                    passwordEntry(
                        customFields = listOf(
                            customField(type = CustomFieldType.CUSTOM_FIELD_TYPE_UNSPECIFIED)
                        )
                    )
                ),
                categories = listOf(category()),
                attachments = listOf(attachment()),
            )
        }
    }

    @Test
    fun rejectsCustomFieldCountAndValueLengthOverLimits() {
        assertThrows(IllegalArgumentException::class.java) {
            VaultSemanticValidator.validateCore(
                passwords = listOf(
                    passwordEntry(
                        customFields = List(AppConstants.MAX_CUSTOM_FIELDS_PER_ENTRY + 1) { index ->
                            customField(id = "field-$index")
                        }
                    )
                ),
                categories = listOf(category()),
                attachments = listOf(attachment()),
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            VaultSemanticValidator.validateCore(
                passwords = listOf(
                    passwordEntry(
                        customFields = listOf(
                            customField(
                                value = "x".repeat(AppConstants.MAX_CUSTOM_FIELD_VALUE_LENGTH + 1)
                            )
                        )
                    )
                ),
                categories = listOf(category()),
                attachments = listOf(attachment()),
            )
        }
    }

    @Test
    fun acceptsAValidPaymentCardAndRejectsSensitiveFieldViolations() {
        VaultSemanticValidator.validateCore(
            passwords = listOf(paymentCardEntry()),
            categories = listOf(category()),
            attachments = listOf(attachment()),
        )

        listOf(
            paymentCardEntry().toBuilder().setCardNumber("4111-1111").build(),
            paymentCardEntry().toBuilder().setCardNumber("٤١١١١١١١").build(),
            paymentCardEntry().toBuilder().setExpirationMonth(0).build(),
            paymentCardEntry().toBuilder().setSecurityCode("12").build(),
            paymentCardEntry().toBuilder().setSecurityCode("١٢٣").build(),
        ).forEach { invalidCard ->
            assertThrows(IllegalArgumentException::class.java) {
                VaultSemanticValidator.validateCore(
                    passwords = listOf(invalidCard),
                    categories = listOf(category()),
                    attachments = listOf(attachment()),
                )
            }
        }
    }

    private fun passwordEntry(
        categoryId: String = "category-1",
        attachmentId: String = "attachment-1",
        customFields: List<CustomField> = emptyList(),
    ): PasswordEntry = PasswordEntry.newBuilder()
        .setId("password-1")
        .setTitle("Example")
        .addCategoryIds(categoryId)
        .addAttachmentIds(attachmentId)
        .addAllCustomFields(customFields)
        .build()

    private fun customField(
        id: String = "field-1",
        name: String = "Account ID",
        value: String = "A-100",
        type: CustomFieldType = CustomFieldType.TEXT,
    ): CustomField = CustomField.newBuilder()
        .setId(id)
        .setName(name)
        .setValue(value)
        .setType(type)
        .build()

    private fun paymentCardEntry(): PasswordEntry = PasswordEntry.newBuilder()
        .setId("card-1")
        .setTitle("Travel card")
        .setType(VaultItemType.PAYMENT_CARD)
        .setCardholderName("Alex Example")
        .setCardNumber("4111111111111111")
        .setCardBrand(PaymentCardBrand.VISA)
        .setExpirationMonth(12)
        .setExpirationYear(2032)
        .setSecurityCode("123")
        .addCategoryIds("category-1")
        .addAttachmentIds("attachment-1")
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
