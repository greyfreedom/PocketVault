package com.turisla.hellopocket.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class GeneratorLetterCase { LOWER, UPPER, MIXED }

@Serializable
enum class GeneratorMode { RANDOM, RULES }

/** 规则只描述生成方式，绝不保存已经生成的密码或随机数种子。 */
@Serializable
sealed interface GeneratorSegment {
    @Serializable
    @SerialName("text")
    data class Text(val value: String) : GeneratorSegment

    @Serializable
    @SerialName("digits")
    data class Digits(val length: Int, val avoidConfusing: Boolean = false) : GeneratorSegment

    @Serializable
    @SerialName("letters")
    data class Letters(
        val length: Int,
        val letterCase: GeneratorLetterCase = GeneratorLetterCase.MIXED,
        val avoidConfusing: Boolean = false,
    ) : GeneratorSegment

    @Serializable
    @SerialName("symbols")
    data class Symbols(val length: Int, val characters: String) : GeneratorSegment
}

@Serializable
data class GeneratorRule(val id: String, val name: String, val segment: GeneratorSegment)

/** 每次加入组合都生成独立 ID，并复制规则内容；编辑规则库不会改变模板。 */
@Serializable
data class GeneratorStep(val id: String, val name: String, val segment: GeneratorSegment)

@Serializable
data class GeneratorTemplate(val id: String, val name: String, val steps: List<GeneratorStep>)

/** 只记住配置快照；未完成的组合也可恢复，生成结果永远不进入这个模型。 */
@Serializable
data class GeneratorSelection(
    val mode: GeneratorMode = GeneratorMode.RANDOM,
    val templateId: String? = null,
    val steps: List<GeneratorStep> = emptyList(),
)

@Serializable
data class GeneratorVaultData(
    val formatVersion: Int = 1,
    val rules: List<GeneratorRule> = emptyList(),
    val templates: List<GeneratorTemplate> = emptyList(),
    val lastSelection: GeneratorSelection = GeneratorSelection(),
)
