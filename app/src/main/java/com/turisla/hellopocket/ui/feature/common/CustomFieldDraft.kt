package com.turisla.hellopocket.ui.feature.common

import com.turisla.hellopocket.model.CustomField
import com.turisla.hellopocket.model.CustomFieldType
import com.turisla.hellopocket.utils.AppConstants
import java.util.UUID

private val customFieldNameLineBreaks = Regex("[\\r\\n]+")

/**
 * 仅存在于页面返回栈生命周期内的自定义字段草稿。
 *
 * [id] 在改名、改类型和排序时保持稳定，用作 Compose key 和持久化身份。
 */
data class CustomFieldDraft(
    val id: String,
    val name: String = "",
    val value: String = "",
    val type: CustomFieldType,
)

fun newCustomFieldDraft(type: CustomFieldType): CustomFieldDraft {
    require(type == CustomFieldType.TEXT || type == CustomFieldType.CONCEALED)
    return CustomFieldDraft(
        id = UUID.randomUUID().toString(),
        type = type,
    )
}

fun CustomField.toDraft(): CustomFieldDraft = CustomFieldDraft(
    id = id,
    name = name,
    value = value,
    // 保险库加载会先进行严格语义校验；这里仍采用隐藏优先，防止未来调用路径意外暴露异常类型。
    type = type.takeIf { it == CustomFieldType.TEXT || it == CustomFieldType.CONCEALED }
        ?: CustomFieldType.CONCEALED,
)

fun CustomFieldDraft.toProto(): CustomField = CustomField.newBuilder()
    .setId(id)
    .setName(name.trim())
    // 字段值可能有有意义的首尾空格，必须原样保存。
    .setValue(value)
    .setType(type)
    .build()

fun List<CustomFieldDraft>.toProtoCustomFields(): List<CustomField> = map(CustomFieldDraft::toProto)

/** 字段名固定为单行；粘贴多行文本时以空格连接，避免直到持久化才报错。 */
fun normalizeCustomFieldNameInput(value: String): String = value
    .replace(customFieldNameLineBreaks, " ")
    .truncateWithoutSplittingSurrogatePair(AppConstants.MAX_CUSTOM_FIELD_NAME_LENGTH)

/** 限长时保留完整 Unicode 字符，不能把 emoji 等代理对截成无法往返持久化的半个字符。 */
fun normalizeCustomFieldValueInput(value: String): String =
    value.truncateWithoutSplittingSurrogatePair(AppConstants.MAX_CUSTOM_FIELD_VALUE_LENGTH)

private fun String.truncateWithoutSplittingSurrogatePair(maxLength: Int): String {
    if (length <= maxLength) return this
    val endIndex = if (
        maxLength > 0 &&
        this[maxLength - 1].isHighSurrogate() &&
        this[maxLength].isLowSurrogate()
    ) {
        maxLength - 1
    } else {
        maxLength
    }
    return substring(0, endIndex)
}

fun List<CustomFieldDraft>.updateCustomField(
    fieldId: String,
    transform: (CustomFieldDraft) -> CustomFieldDraft,
): List<CustomFieldDraft> = map { field ->
    if (field.id == fieldId) transform(field) else field
}

fun List<CustomFieldDraft>.moveCustomField(fieldId: String, targetIndex: Int): List<CustomFieldDraft> {
    val sourceIndex = indexOfFirst { it.id == fieldId }
    if (sourceIndex == -1 || indices.isEmpty()) return this
    val safeTargetIndex = targetIndex.coerceIn(indices)
    if (sourceIndex == safeTargetIndex) return this

    return toMutableList().apply {
        val field = removeAt(sourceIndex)
        add(safeTargetIndex, field)
    }
}

fun List<CustomFieldDraft>.restoreCustomField(
    field: CustomFieldDraft,
    index: Int,
): List<CustomFieldDraft> {
    if (size >= AppConstants.MAX_CUSTOM_FIELDS_PER_ENTRY || any { it.id == field.id }) return this
    return toMutableList().apply { add(index.coerceIn(0..size), field) }
}
