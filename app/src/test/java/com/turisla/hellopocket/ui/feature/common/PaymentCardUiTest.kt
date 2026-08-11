package com.turisla.hellopocket.ui.feature.common

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentCardUiTest {

    @Test
    fun cardNumberVisualTransformationGroupsDigitsAndMapsCursorOffsets() {
        val transformed = PaymentCardNumberVisualTransformation.filter(
            AnnotatedString("123456789"),
        )

        assertEquals("1234 5678 9", transformed.text.text)
        assertEquals(5, transformed.offsetMapping.originalToTransformed(4))
        assertEquals(10, transformed.offsetMapping.originalToTransformed(8))
        assertEquals(4, transformed.offsetMapping.transformedToOriginal(5))
        assertEquals(8, transformed.offsetMapping.transformedToOriginal(10))
        assertEquals(9, transformed.offsetMapping.transformedToOriginal(11))
    }

    @Test
    fun cardNumberVisualTransformationDoesNotMapPastACompleteFinalGroup() {
        val transformed = PaymentCardNumberVisualTransformation.filter(
            AnnotatedString("12345678"),
        )

        assertEquals("1234 5678", transformed.text.text)
        assertEquals(9, transformed.offsetMapping.originalToTransformed(8))
        assertEquals(8, transformed.offsetMapping.transformedToOriginal(9))
    }
}
