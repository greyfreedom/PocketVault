package com.turisla.hellopocket.ui.feature.common

import androidx.annotation.StringRes
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.turisla.hellopocket.R
import com.turisla.hellopocket.model.PaymentCardBrand
import com.turisla.hellopocket.utils.AppConstants
import java.util.Locale

val selectablePaymentCardBrands: List<PaymentCardBrand> = listOf(
    PaymentCardBrand.PAYMENT_CARD_BRAND_UNSPECIFIED,
    PaymentCardBrand.VISA,
    PaymentCardBrand.MASTERCARD,
    PaymentCardBrand.AMERICAN_EXPRESS,
    PaymentCardBrand.DISCOVER,
    PaymentCardBrand.JCB,
    PaymentCardBrand.UNIONPAY,
    PaymentCardBrand.DINERS_CLUB,
    PaymentCardBrand.OTHER,
)

@StringRes
fun PaymentCardBrand.labelResId(): Int = when (this) {
    PaymentCardBrand.PAYMENT_CARD_BRAND_UNSPECIFIED -> R.string.card_brand_not_set
    PaymentCardBrand.VISA -> R.string.card_brand_visa
    PaymentCardBrand.MASTERCARD -> R.string.card_brand_mastercard
    PaymentCardBrand.AMERICAN_EXPRESS -> R.string.card_brand_american_express
    PaymentCardBrand.DISCOVER -> R.string.card_brand_discover
    PaymentCardBrand.JCB -> R.string.card_brand_jcb
    PaymentCardBrand.UNIONPAY -> R.string.card_brand_unionpay
    PaymentCardBrand.DINERS_CLUB -> R.string.card_brand_diners_club
    PaymentCardBrand.OTHER,
    PaymentCardBrand.UNRECOGNIZED,
    -> R.string.card_brand_other
}

fun normalizeCardholderNameInput(value: String): String {
    val maxLength = AppConstants.MAX_CARDHOLDER_NAME_LENGTH
    if (value.length <= maxLength) return value
    // UTF-16 限长不能把 emoji 等代理对从中间截断，否则保存后无法稳定往返。
    val endIndex = if (
        value[maxLength - 1].isHighSurrogate() && value[maxLength].isLowSurrogate()
    ) {
        maxLength - 1
    } else {
        maxLength
    }
    return value.substring(0, endIndex)
}

fun normalizePaymentCardNumberInput(value: String): String = value
    .toAsciiDecimalDigits()
    .take(AppConstants.MAX_PAYMENT_CARD_NUMBER_LENGTH)

/**
 * 草稿始终保存纯数字，空格只在显示层生成，避免每次输入重写文本后把光标推到末尾。
 */
object PaymentCardNumberVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val originalLength = text.text.length
        val transformedText = text.text.chunked(4).joinToString(" ")
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val safeOffset = offset.coerceIn(0, originalLength)
                return (safeOffset + safeOffset / 4).coerceAtMost(transformedText.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val safeOffset = offset.coerceIn(0, transformedText.length)
                return (safeOffset - safeOffset / 5).coerceAtMost(originalLength)
            }
        }
        return TransformedText(AnnotatedString(transformedText), offsetMapping)
    }
}

fun paymentCardNumberDigits(value: String): String = value.toAsciiDecimalDigits()

fun normalizeSecurityCodeInput(value: String): String = value
    .toAsciiDecimalDigits()
    .take(AppConstants.MAX_SECURITY_CODE_LENGTH)

fun formatPaymentCardNumber(value: String): String = paymentCardNumberDigits(value)
    .chunked(4)
    .joinToString(" ")

fun formatExpirationDate(month: Int, year: Int): String {
    if (month !in 1..12 || year <= 0) return ""
    return String.format(Locale.ROOT, "%02d / %04d", month, year)
}

// 显示层立即把本地化十进制数字规范为 ASCII，避免用户看到的值与落盘值不一致。
private fun String.toAsciiDecimalDigits(): String = buildString(length) {
    this@toAsciiDecimalDigits.forEach { character ->
        character.digitToIntOrNull()?.let { digit -> append(('0'.code + digit).toChar()) }
    }
}
