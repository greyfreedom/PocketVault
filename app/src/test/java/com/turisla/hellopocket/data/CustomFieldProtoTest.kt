package com.turisla.hellopocket.data

import com.turisla.hellopocket.model.CustomField
import com.turisla.hellopocket.model.CustomFieldType
import com.turisla.hellopocket.model.PasswordEntries
import com.turisla.hellopocket.model.PasswordEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomFieldProtoTest {

    @Test
    fun protobufRoundTripPreservesFieldOrderTypesAndValues() {
        val source = PasswordEntries.newBuilder()
            .setSchemaVersion(PasswordRepository.CURRENT_SCHEMA_VERSION)
            .addEntries(
                PasswordEntry.newBuilder()
                    .setId("entry-1")
                    .setTitle("Example")
                    .addCustomFields(customField("field-1", "Account ID", "A-100", CustomFieldType.TEXT))
                    .addCustomFields(customField("field-2", "PIN", "1234", CustomFieldType.CONCEALED))
            )
            .build()

        val restored = PasswordEntries.parseFrom(source.toByteArray())
        val fields = restored.entriesList.single().customFieldsList

        assertEquals(listOf("field-1", "field-2"), fields.map { it.id })
        assertEquals(listOf(CustomFieldType.TEXT, CustomFieldType.CONCEALED), fields.map { it.type })
        assertEquals(listOf("A-100", "1234"), fields.map { it.value })
    }

    private fun customField(
        id: String,
        name: String,
        value: String,
        type: CustomFieldType,
    ): CustomField = CustomField.newBuilder()
        .setId(id)
        .setName(name)
        .setValue(value)
        .setType(type)
        .build()
}
