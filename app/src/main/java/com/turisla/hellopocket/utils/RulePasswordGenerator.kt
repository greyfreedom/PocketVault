package com.turisla.hellopocket.utils

import com.turisla.hellopocket.model.GeneratorLetterCase
import com.turisla.hellopocket.model.GeneratorSegment
import com.turisla.hellopocket.model.GeneratorStep
import com.turisla.hellopocket.model.GeneratorVaultData
import java.security.SecureRandom
import kotlin.math.log2

enum class GeneratorIssue {
    EMPTY_COMPOSITION, EMPTY_TEXT, INVALID_LENGTH, EMPTY_ALPHABET, INVALID_SYMBOLS,
    UNSUPPORTED_TEXT, TOO_LONG, NO_RANDOMNESS,
}

data class GeneratorAnalysis(
    val length: Int,
    val entropyBits: Double,
    val issue: GeneratorIssue? = null,
    val stepIndex: Int? = null,
)

/**
 * 密码按片段顺序生成。固定内容贡献 0 比特，随机片段按去重后的实际字符集计算熵。
 * 不限制片段数量；128 字符限制只在验证输出长度时应用。
 */
class RulePasswordGenerator(private val random: SecureRandom = SecureRandom()) {
    companion object {
        const val MAX_PASSWORD_LENGTH = 128
        const val MAX_NAME_LENGTH = 80
        const val DEFAULT_SYMBOLS = "!@#$%^&*()_+-=[]{}|;:,.<>?"
        private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
        private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val DIGITS = "0123456789"
        private const val CONFUSING = "O0oIl1|"

        fun alphabet(segment: GeneratorSegment): String = when (segment) {
            is GeneratorSegment.Text -> ""
            is GeneratorSegment.Digits -> DIGITS.filterNot { segment.avoidConfusing && it in CONFUSING }
            is GeneratorSegment.Letters -> when (segment.letterCase) {
                GeneratorLetterCase.LOWER -> LOWERCASE
                GeneratorLetterCase.UPPER -> UPPERCASE
                GeneratorLetterCase.MIXED -> LOWERCASE + UPPERCASE
            }.filterNot { segment.avoidConfusing && it in CONFUSING }
            is GeneratorSegment.Symbols -> segment.characters.toSet().joinToString("")
        }

        fun segmentLength(segment: GeneratorSegment): Int = when (segment) {
            is GeneratorSegment.Text -> segment.value.codePointCount(0, segment.value.length)
            is GeneratorSegment.Digits -> segment.length
            is GeneratorSegment.Letters -> segment.length
            is GeneratorSegment.Symbols -> segment.length
        }

        fun validateSegment(segment: GeneratorSegment): GeneratorIssue? {
            if (segment is GeneratorSegment.Text) {
                if (segment.value.isEmpty()) return GeneratorIssue.EMPTY_TEXT
                if (segment.value.codePoints().anyMatch {
                        Character.isISOControl(it) || it in 0xD800..0xDFFF ||
                            Character.getType(it) == Character.FORMAT.toInt()
                    }) return GeneratorIssue.UNSUPPORTED_TEXT
            }
            if (segmentLength(segment) !in 1..MAX_PASSWORD_LENGTH) return GeneratorIssue.INVALID_LENGTH
            if (segment is GeneratorSegment.Symbols && segment.characters.any {
                    it !in '!'..'~' || it.isLetterOrDigit()
                }) return GeneratorIssue.INVALID_SYMBOLS
            if (segment !is GeneratorSegment.Text && alphabet(segment).isEmpty()) {
                return GeneratorIssue.EMPTY_ALPHABET
            }
            return null
        }

        fun analyze(steps: List<GeneratorStep>): GeneratorAnalysis {
            if (steps.isEmpty()) return GeneratorAnalysis(0, 0.0, GeneratorIssue.EMPTY_COMPOSITION)
            val totalLength = steps.sumOf { segmentLength(it.segment).coerceAtLeast(0).toLong() }
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            var length = 0L
            var bits = 0.0
            steps.forEachIndexed { index, step ->
                validateSegment(step.segment)?.let {
                    return GeneratorAnalysis(totalLength, bits, it, index)
                }
                val size = segmentLength(step.segment)
                length += size
                if (length > MAX_PASSWORD_LENGTH) {
                    return GeneratorAnalysis(totalLength, bits, GeneratorIssue.TOO_LONG, index)
                }
                if (step.segment !is GeneratorSegment.Text) {
                    bits += size * log2(alphabet(step.segment).length.toDouble())
                }
            }
            return GeneratorAnalysis(
                length.toInt(), bits,
                if (bits == 0.0) GeneratorIssue.NO_RANDOMNESS else null,
            )
        }

        /** 导入、读取和写入采用同一语义校验，不信任已通过 AEAD 的畸形配置。 */
        fun validateVaultData(data: GeneratorVaultData) {
            require(data.formatVersion == 1) { "Unsupported generator data version" }
            fun validateIds(ids: List<String>) {
                require(ids.all { it.isNotBlank() && it.length <= 80 } && ids.toSet().size == ids.size) {
                    "Invalid generator identifiers"
                }
            }
            fun validateName(name: String) {
                require(name.isNotBlank() && name.length <= MAX_NAME_LENGTH) { "Invalid generator name" }
            }
            validateIds(data.rules.map { it.id })
            validateIds(data.templates.map { it.id })
            data.rules.forEach {
                validateName(it.name)
                require(validateSegment(it.segment) == null) { "Invalid generator rule" }
            }
            data.templates.forEach { template ->
                validateName(template.name)
                validateIds(template.steps.map { it.id })
                template.steps.forEach { validateName(it.name) }
                require(analyze(template.steps).issue == null) { "Invalid generator template" }
            }
            // 上次编辑的组合允许为空、只有固定文本或总长超限，但每个片段仍须合法。
            val selection = data.lastSelection
            selection.templateId?.let { id ->
                require(data.templates.any { it.id == id }) { "Unknown selected generator template" }
            }
            validateIds(selection.steps.map { it.id })
            selection.steps.forEach {
                validateName(it.name)
                require(validateSegment(it.segment) == null) { "Invalid selected generator rule" }
            }
        }
    }

    fun generate(steps: List<GeneratorStep>): String {
        require(analyze(steps).issue == null) { "Invalid password composition" }
        return buildString {
            steps.forEach { step ->
                when (val segment = step.segment) {
                    is GeneratorSegment.Text -> append(segment.value)
                    else -> {
                        val characters = alphabet(segment)
                        // 有界 nextInt 避免取模偏差；同一个规则的多个实例也分别独立抽样。
                        repeat(segmentLength(segment)) { append(characters[random.nextInt(characters.length)]) }
                    }
                }
            }
        }
    }
}
