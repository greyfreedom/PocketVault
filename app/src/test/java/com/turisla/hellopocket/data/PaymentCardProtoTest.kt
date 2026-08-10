package com.turisla.hellopocket.data

import com.turisla.hellopocket.model.PasswordEntries
import com.turisla.hellopocket.model.PasswordEntry
import com.turisla.hellopocket.model.PaymentCardBrand
import com.turisla.hellopocket.model.VaultItemType
import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentCardProtoTest {

    @Test
    fun protobufRoundTripPreservesPaymentCardFields() {
        val source = PasswordEntries.newBuilder()
            .setSchemaVersion(PasswordRepository.CURRENT_SCHEMA_VERSION)
            .addEntries(
                PasswordEntry.newBuilder()
                    .setId("card-1")
                    .setTitle("Travel card")
                    .setType(VaultItemType.PAYMENT_CARD)
                    .setCardholderName("Alex Example")
                    .setCardNumber("4111111111111111")
                    .setCardBrand(PaymentCardBrand.VISA)
                    .setExpirationMonth(12)
                    .setExpirationYear(2032)
                    .setSecurityCode("123")
                    .setNotes("Use abroad")
            )
            .build()

        val restored = PasswordEntries.parseFrom(source.toByteArray()).entriesList.single()

        assertEquals(VaultItemType.PAYMENT_CARD, restored.type)
        assertEquals("Alex Example", restored.cardholderName)
        assertEquals("4111111111111111", restored.cardNumber)
        assertEquals(PaymentCardBrand.VISA, restored.cardBrand)
        assertEquals(12, restored.expirationMonth)
        assertEquals(2032, restored.expirationYear)
        assertEquals("123", restored.securityCode)
        assertEquals("Use abroad", restored.notes)
    }
}
