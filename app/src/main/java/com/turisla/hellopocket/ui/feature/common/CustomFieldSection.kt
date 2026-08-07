package com.turisla.hellopocket.ui.feature.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.turisla.hellopocket.R
import com.turisla.hellopocket.model.CustomField
import com.turisla.hellopocket.model.CustomFieldType
import com.turisla.hellopocket.utils.AppConstants
import kotlinx.coroutines.launch
import kotlin.math.abs

private data class FieldBounds(
    val parentTop: Float,
    val parentBottom: Float,
    val windowTop: Float,
    val windowBottom: Float,
) {
    val parentCenter: Float get() = (parentTop + parentBottom) / 2f
    val windowCenter: Float get() = (windowTop + windowBottom) / 2f
}

internal data class CustomFieldDragBounds(
    val top: Float,
    val bottom: Float,
)

/**
 * 计算一次拖拽手势内各最终索引对应的卡片基准中心。
 *
 * 目标位置在手势开始时冻结，避免列表重排后的布局回调分批更新坐标时，
 * 同一个字段在相邻索引之间来回跳动。
 */
internal fun calculateCustomFieldDragTargetCenters(
    itemBounds: List<CustomFieldDragBounds>,
    draggedIndex: Int,
): List<Float> {
    val draggedBounds = itemBounds.getOrNull(draggedIndex) ?: return emptyList()
    val draggedHalfHeight = (draggedBounds.bottom - draggedBounds.top) / 2f
    if (draggedHalfHeight <= 0f) return emptyList()

    return itemBounds.mapIndexed { index, bounds ->
        when {
            index < draggedIndex -> bounds.top + draggedHalfHeight
            index > draggedIndex -> bounds.bottom - draggedHalfHeight
            else -> (draggedBounds.top + draggedBounds.bottom) / 2f
        }
    }
}

internal fun resolveCustomFieldDragTargetIndex(
    targetCenters: List<Float>,
    currentTargetIndex: Int,
    dragCenter: Float,
    hysteresis: Float,
): Int {
    if (currentTargetIndex !in targetCenters.indices) return currentTargetIndex
    val nearestIndex = targetCenters.indices.minByOrNull { index ->
        abs(targetCenters[index] - dragCenter)
    } ?: return currentTargetIndex
    if (nearestIndex == currentTargetIndex) return currentTargetIndex

    val safeHysteresis = hysteresis.coerceAtLeast(0f)
    return when {
        nearestIndex > currentTargetIndex && currentTargetIndex < targetCenters.lastIndex -> {
            val boundary = (
                targetCenters[currentTargetIndex] + targetCenters[currentTargetIndex + 1]
            ) / 2f + safeHysteresis
            if (dragCenter >= boundary) nearestIndex else currentTargetIndex
        }
        nearestIndex < currentTargetIndex && currentTargetIndex > 0 -> {
            val boundary = (
                targetCenters[currentTargetIndex] + targetCenters[currentTargetIndex - 1]
            ) / 2f - safeHysteresis
            if (dragCenter <= boundary) nearestIndex else currentTargetIndex
        }
        else -> currentTargetIndex
    }
}

private class CustomFieldDragRuntime {
    var targetCenters: List<Float> = emptyList()
    var targetIndex: Int = -1
}

/** 敏感内容一旦离开前台立即重新隐藏，不等待一分钟自动锁定计时。 */
@Composable
fun ResetSensitiveStateOnBackground(onReset: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnReset by rememberUpdatedState(onReset)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) currentOnReset()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomFieldEditSection(
    fields: List<CustomFieldDraft>,
    parentScrollState: ScrollState,
    snackbarHostState: SnackbarHostState,
    showValidationErrors: Boolean,
    onAdd: (CustomFieldType) -> String?,
    onNameChange: (String, String) -> Unit,
    onValueChange: (String, String) -> Unit,
    onTypeChange: (String, CustomFieldType) -> Unit,
    onRemove: (String) -> Unit,
    onRestore: (CustomFieldDraft, Int) -> Unit,
    onMove: (String, Int) -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddSheet by remember { mutableStateOf(false) }
    var focusFieldId by remember { mutableStateOf<String?>(null) }
    val hiddenVisibility = remember { mutableStateMapOf<String, Boolean>() }
    // 坐标只供拖拽命中测试使用；普通页面滚动不能因为每个卡片的坐标变化而触发重组。
    val fieldBounds = remember { mutableMapOf<String, FieldBounds>() }
    val dragRuntime = remember { CustomFieldDragRuntime() }
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val view = LocalView.current
    val density = LocalDensity.current
    val edgeThresholdPx = with(density) { 80.dp.toPx() }
    val scrollStepPx = with(density) { 18.dp.toPx() }
    val reorderHysteresisPx = with(density) { 8.dp.toPx() }
    val currentFields by rememberUpdatedState(fields)
    val currentOnMove by rememberUpdatedState(onMove)

    var draggedFieldId by remember { mutableStateOf<String?>(null) }
    var dragCenterInParent by remember { mutableFloatStateOf(0f) }
    var draggedFieldParentCenter by remember { mutableFloatStateOf(0f) }
    var autoScrollDirection by remember { mutableIntStateOf(0) }

    val moveDraggedFieldToClosestIndex by rememberUpdatedState<(String) -> Unit> { fieldId ->
        val orderedFields = currentFields
        val targetCenters = dragRuntime.targetCenters
        if (
            orderedFields.none { it.id == fieldId } ||
            targetCenters.size != orderedFields.size
        ) {
            return@rememberUpdatedState
        }
        val targetIndex = resolveCustomFieldDragTargetIndex(
            targetCenters = targetCenters,
            currentTargetIndex = dragRuntime.targetIndex,
            dragCenter = dragCenterInParent,
            hysteresis = reorderHysteresisPx,
        )
        if (targetIndex != dragRuntime.targetIndex) {
            dragRuntime.targetIndex = targetIndex
            // 先补偿即将发生的布局位移，避免卡片在新旧位置之间闪一下。
            draggedFieldParentCenter = targetCenters[targetIndex]
            currentOnMove(fieldId, targetIndex)
        }
    }

    ResetSensitiveStateOnBackground {
        hiddenVisibility.clear()
        draggedFieldId = null
        autoScrollDirection = 0
        dragRuntime.targetCenters = emptyList()
        dragRuntime.targetIndex = -1
    }

    LaunchedEffect(fields.map(CustomFieldDraft::id)) {
        val activeIds = fields.mapTo(mutableSetOf(), CustomFieldDraft::id)
        hiddenVisibility.keys.retainAll(activeIds)
        fieldBounds.keys.retainAll(activeIds)
        if (draggedFieldId !in activeIds) {
            draggedFieldId = null
            autoScrollDirection = 0
            dragRuntime.targetCenters = emptyList()
            dragRuntime.targetIndex = -1
        } else if (
            draggedFieldId != null &&
            dragRuntime.targetCenters.size != fields.size
        ) {
            draggedFieldId = null
            autoScrollDirection = 0
            dragRuntime.targetCenters = emptyList()
            dragRuntime.targetIndex = -1
        }
    }

    LaunchedEffect(showValidationErrors) {
        if (showValidationErrors) {
            focusFieldId = fields.firstOrNull { it.name.isBlank() }?.id
        }
    }

    LaunchedEffect(draggedFieldId, autoScrollDirection, parentScrollState) {
        val direction = autoScrollDirection
        if (draggedFieldId == null || direction == 0) return@LaunchedEffect

        // 手指停在边缘时仍需逐帧滚动，否则长列表无法连续拖到屏幕外的位置。
        while (draggedFieldId != null && autoScrollDirection == direction) {
            withFrameNanos { }
            val consumed = parentScrollState.scrollBy(direction * scrollStepPx)
            if (consumed != 0f) {
                dragCenterInParent += consumed
                draggedFieldId?.let(moveDraggedFieldToClosestIndex)
            }
        }
    }

    val addField: (CustomFieldType) -> Unit = { type ->
        onAdd(type)?.let { newId ->
            focusFieldId = newId
            showAddSheet = false
        }
    }
    val canAdd = fields.size < AppConstants.MAX_CUSTOM_FIELDS_PER_ENTRY
    val deletedMessage = stringResource(R.string.custom_field_deleted)
    val undoLabel = stringResource(R.string.undo)

    Box(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.custom_fields),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { showAddSheet = true },
                    enabled = canAdd,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_custom_field),
                    )
                }
            }

            if (fields.isEmpty()) {
                OutlinedButton(
                    onClick = { showAddSheet = true },
                    enabled = canAdd,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_custom_field))
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    fields.forEachIndexed { index, field ->
                        key(field.id) {
                            val isDragging = draggedFieldId == field.id
                            val translationY = if (isDragging) {
                                dragCenterInParent - draggedFieldParentCenter
                            } else {
                                0f
                            }
                            val moveUpLabel = stringResource(R.string.move_up)
                            val moveDownLabel = stringResource(R.string.move_down)
                            val reorderDescription = stringResource(R.string.drag_to_reorder)
                            val accessibilityActions = buildList {
                                if (index > 0) {
                                    add(CustomAccessibilityAction(moveUpLabel) {
                                        onMove(field.id, index - 1)
                                        true
                                    })
                                }
                                if (index < fields.lastIndex) {
                                    add(CustomAccessibilityAction(moveDownLabel) {
                                        onMove(field.id, index + 1)
                                        true
                                    })
                                }
                            }
                            val dragHandleModifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .semantics {
                                    contentDescription = reorderDescription
                                    role = Role.Button
                                    customActions = accessibilityActions
                                }
                                .pointerInput(field.id, parentScrollState) {
                                    // 指针窗口坐标只在当前手势协程内使用，不进入 Compose 快照状态。
                                    var pointerCenterInWindow = 0f
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = dragStart@{
                                            val orderedFields = currentFields
                                            val draggedIndex = orderedFields.indexOfFirst {
                                                it.id == field.id
                                            }
                                            val fieldPosition = fieldBounds[field.id]
                                                ?: return@dragStart
                                            val orderedBounds = orderedFields.map { orderedField ->
                                                val bounds = fieldBounds[orderedField.id]
                                                    ?: return@dragStart
                                                CustomFieldDragBounds(
                                                    top = bounds.parentTop,
                                                    bottom = bounds.parentBottom,
                                                )
                                            }
                                            val targetCenters = calculateCustomFieldDragTargetCenters(
                                                itemBounds = orderedBounds,
                                                draggedIndex = draggedIndex,
                                            )
                                            if (targetCenters.size != orderedFields.size) {
                                                return@dragStart
                                            }

                                            dragRuntime.targetCenters = targetCenters
                                            dragRuntime.targetIndex = draggedIndex
                                            draggedFieldId = field.id
                                            dragCenterInParent = fieldPosition.parentCenter
                                            draggedFieldParentCenter = fieldPosition.parentCenter
                                            pointerCenterInWindow = fieldPosition.windowCenter
                                            autoScrollDirection = 0
                                            hapticFeedback.performHapticFeedback(
                                                HapticFeedbackType.LongPress
                                            )
                                        },
                                        onDragCancel = {
                                            draggedFieldId = null
                                            autoScrollDirection = 0
                                            dragRuntime.targetCenters = emptyList()
                                            dragRuntime.targetIndex = -1
                                        },
                                        onDragEnd = {
                                            draggedFieldId = null
                                            autoScrollDirection = 0
                                            dragRuntime.targetCenters = emptyList()
                                            dragRuntime.targetIndex = -1
                                        },
                                        onDrag = { change, dragAmount ->
                                            if (draggedFieldId != field.id) return@detectDragGesturesAfterLongPress
                                            change.consume()
                                            dragCenterInParent += dragAmount.y
                                            pointerCenterInWindow += dragAmount.y

                                            autoScrollDirection = when {
                                                pointerCenterInWindow < edgeThresholdPx -> -1
                                                pointerCenterInWindow > view.height - edgeThresholdPx -> 1
                                                else -> 0
                                            }
                                            moveDraggedFieldToClosestIndex(field.id)
                                        },
                                    )
                                }

                            CustomFieldEditCard(
                                field = field,
                                isDragging = isDragging,
                                translationY = translationY,
                                isValueVisible = hiddenVisibility[field.id] == true,
                                showNameError = showValidationErrors && field.name.isBlank(),
                                requestNameFocus = focusFieldId == field.id,
                                dragHandle = {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = dragHandleModifier,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DragHandle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onPositioned = { bounds ->
                                    fieldBounds[field.id] = bounds
                                },
                                onNameFocusHandled = { focusFieldId = null },
                                onNameChange = { onNameChange(field.id, it) },
                                onValueChange = { onValueChange(field.id, it) },
                                onVisibilityToggle = {
                                    hiddenVisibility[field.id] = hiddenVisibility[field.id] != true
                                },
                                onTypeChange = { newType ->
                                    hiddenVisibility.remove(field.id)
                                    onTypeChange(field.id, newType)
                                },
                                onCopy = { onCopy(field.value) },
                                onDelete = {
                                    val removedIndex = currentFields.indexOfFirst { it.id == field.id }
                                    if (removedIndex >= 0) {
                                        onRemove(field.id)
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = deletedMessage,
                                                actionLabel = undoLabel,
                                                duration = SnackbarDuration.Short,
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                onRestore(field, removedIndex)
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            if (!canAdd) {
                Text(
                    text = pluralStringResource(
                        R.plurals.custom_field_limit_reached,
                        AppConstants.MAX_CUSTOM_FIELDS_PER_ENTRY,
                        AppConstants.MAX_CUSTOM_FIELDS_PER_ENTRY,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            Text(
                text = stringResource(R.string.add_custom_field),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            CustomFieldTypeChoice(
                type = CustomFieldType.TEXT,
                title = stringResource(R.string.custom_field_text),
                description = stringResource(R.string.custom_field_text_description),
                onClick = { addField(CustomFieldType.TEXT) },
            )
            CustomFieldTypeChoice(
                type = CustomFieldType.CONCEALED,
                title = stringResource(R.string.custom_field_concealed),
                description = stringResource(R.string.custom_field_concealed_description),
                onClick = { addField(CustomFieldType.CONCEALED) },
            )
            Spacer(Modifier.height(16.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun CustomFieldTypeChoice(
    type: CustomFieldType,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = {
            Icon(
                imageVector = if (type == CustomFieldType.TEXT) {
                    Icons.Outlined.TextFields
                } else {
                    Icons.Outlined.VisibilityOff
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun CustomFieldEditCard(
    field: CustomFieldDraft,
    isDragging: Boolean,
    translationY: Float,
    isValueVisible: Boolean,
    showNameError: Boolean,
    requestNameFocus: Boolean,
    dragHandle: @Composable () -> Unit,
    onPositioned: (FieldBounds) -> Unit,
    onNameFocusHandled: () -> Unit,
    onNameChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onVisibilityToggle: () -> Unit,
    onTypeChange: (CustomFieldType) -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val focusRequester = remember(field.id) { FocusRequester() }
    val isConcealed = field.type != CustomFieldType.TEXT

    LaunchedEffect(requestNameFocus) {
        if (requestNameFocus) {
            focusRequester.requestFocus()
            onNameFocusHandled()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer { this.translationY = translationY }
            .then(if (isDragging) Modifier.shadow(8.dp, MaterialTheme.shapes.large) else Modifier)
            .onGloballyPositioned { coordinates ->
                val parentPosition = coordinates.positionInParent()
                val windowPosition = coordinates.positionInWindow()
                onPositioned(
                    FieldBounds(
                        parentTop = parentPosition.y,
                        parentBottom = parentPosition.y + coordinates.size.height,
                        windowTop = windowPosition.y,
                        windowBottom = windowPosition.y + coordinates.size.height,
                    )
                )
            },
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isConcealed) {
                        Icons.Outlined.VisibilityOff
                    } else {
                        Icons.Outlined.TextFields
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (isConcealed) R.string.custom_field_concealed else R.string.custom_field_text
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                dragHandle()
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.totp_more),
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.copy)) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            enabled = field.value.isNotEmpty(),
                            onClick = {
                                showMenu = false
                                onCopy()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (isConcealed) {
                                            R.string.change_to_text_field
                                        } else {
                                            R.string.change_to_concealed_field
                                        }
                                    )
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isConcealed) {
                                        Icons.Outlined.TextFields
                                    } else {
                                        Icons.Outlined.VisibilityOff
                                    },
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                showMenu = false
                                onTypeChange(
                                    if (isConcealed) CustomFieldType.TEXT else CustomFieldType.CONCEALED
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.delete_custom_field),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = field.name,
                onValueChange = onNameChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                label = { Text(stringResource(R.string.custom_field_name)) },
                singleLine = true,
                isError = showNameError,
                supportingText = if (showNameError) {
                    { Text(stringResource(R.string.custom_field_name_required)) }
                } else {
                    null
                },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = field.value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.custom_field_value)) },
                singleLine = isConcealed,
                maxLines = if (isConcealed) 1 else 4,
                visualTransformation = if (isConcealed && !isValueVisible) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                keyboardOptions = if (isConcealed) {
                    KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false,
                    )
                } else {
                    KeyboardOptions(keyboardType = KeyboardType.Text)
                },
                trailingIcon = if (isConcealed) {
                    {
                        IconButton(onClick = onVisibilityToggle) {
                            Icon(
                                imageVector = if (isValueVisible) {
                                    Icons.Outlined.Visibility
                                } else {
                                    Icons.Outlined.VisibilityOff
                                },
                                contentDescription = stringResource(
                                    if (isValueVisible) {
                                        R.string.hide_custom_field
                                    } else {
                                        R.string.show_custom_field
                                    }
                                ),
                            )
                        }
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
fun CustomFieldViewSection(
    fields: List<CustomField>,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (fields.isEmpty()) return

    val hiddenVisibility = remember { mutableStateMapOf<String, Boolean>() }
    ResetSensitiveStateOnBackground { hiddenVisibility.clear() }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.custom_fields),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            fields.forEachIndexed { index, field ->
                CustomFieldViewRow(
                    field = field,
                    isValueVisible = hiddenVisibility[field.id] == true,
                    onVisibilityToggle = {
                        hiddenVisibility[field.id] = hiddenVisibility[field.id] != true
                    },
                    onCopy = { onCopy(field.id) },
                )
                if (index != fields.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomFieldViewRow(
    field: CustomField,
    isValueVisible: Boolean,
    onVisibilityToggle: () -> Unit,
    onCopy: () -> Unit,
) {
    val isConcealed = field.type != CustomFieldType.TEXT
    val displayedValue = when {
        field.value.isEmpty() -> stringResource(R.string.not_filled)
        isConcealed && !isValueVisible -> "••••••••"
        else -> field.value
    }
    val hiddenDescription = stringResource(R.string.custom_field_value_hidden)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
    ) {
        Text(
            text = field.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayedValue,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (field.value.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isConcealed && !isValueVisible && field.value.isNotEmpty()) {
                                Modifier.clearAndSetSemantics {
                                    contentDescription = hiddenDescription
                                }
                            } else {
                                Modifier
                            }
                        ),
                )
            }
            if (isConcealed && field.value.isNotEmpty()) {
                IconButton(onClick = onVisibilityToggle) {
                    Icon(
                        imageVector = if (isValueVisible) {
                            Icons.Outlined.Visibility
                        } else {
                            Icons.Outlined.VisibilityOff
                        },
                        contentDescription = stringResource(
                            if (isValueVisible) {
                                R.string.hide_custom_field
                            } else {
                                R.string.show_custom_field
                            }
                        ),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(
                onClick = onCopy,
                enabled = field.value.isNotEmpty(),
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.copy),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.alpha(if (field.value.isNotEmpty()) 1f else 0.38f),
                )
            }
        }
    }
}
