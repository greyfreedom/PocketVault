package com.turisla.hellopocket.ui.feature.common

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.toColorInt
import com.turisla.hellopocket.R
import com.turisla.hellopocket.model.Category
import java.util.Locale

private const val DEFAULT_CATEGORY_COLOR = "#2196F3"

/**
 * 分类编辑对话框 - 统一的UI组件，可用于添加和编辑分类
 * 基于分类管理页面的设计标准
 */
@Composable
fun CategoryEditDialog(
    title: String,
    category: Category? = null,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            CategoryCreationContent(
                title = title,
                category = category,
                onSave = onSave,
                onCancel = onDismiss
            )
        }
    }
}

/**
 * 选择分类内容 - 可复用的分类选择组件
 */
@Composable
fun SelectCategoryContent(
    categories: List<Category>,
    selectedCategoryIds: Set<String>,
    onCategorySelect: (String) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
    multiSelect: Boolean = true,
    newlyCreatedCategoryId: String? = null // 新增参数：新创建的分类 ID
) {
    // 使用临时选择状态，只在点击完成时才应用
    var tempSelectedIds by remember { mutableStateOf(selectedCategoryIds) }
    val listState = rememberLazyListState()

    // 当新创建分类时，自动添加到临时选择列表并滚动到对应位置
    LaunchedEffect(newlyCreatedCategoryId) {
        if (newlyCreatedCategoryId != null && multiSelect) {
            // 添加到临时选择状态
            if (!(tempSelectedIds.contains(newlyCreatedCategoryId))) {
                tempSelectedIds = tempSelectedIds + newlyCreatedCategoryId
            }
            
            // 查找新分类在列表中的位置
            val newCategoryIndex = categories.indexOfFirst { it.id == newlyCreatedCategoryId }
            if (newCategoryIndex >= 0) {
                // 滚动到新分类位置
                listState.animateScrollToItem(newCategoryIndex)
            }
        }
    }
    
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .verticalScroll(rememberScrollState())
            .padding(24.dp), 
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标题行 - 标题和创建按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.select_category), 
                style = MaterialTheme.typography.titleLarge, 
                fontWeight = FontWeight.SemiBold,
            )
            
            // 创建新分类图标按钮
            IconButton(
                onClick = onCreateNew,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.create_new_category),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        if (categories.isEmpty()) {
            Text(
                text = stringResource(R.string.no_categories_available), 
                style = MaterialTheme.typography.bodyMedium, 
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // 改为LazyColumn列表布局
            LazyColumn(
                state = listState, // 使用列表状态以支持滚动
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp), // 增加高度以显示更多项目
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories, key = { it.id }) { category ->
                    SelectableCategoryItem(
                        category = category, 
                        isSelected = if (multiSelect) {
                            tempSelectedIds.contains(category.id)
                        } else {
                            selectedCategoryIds.contains(category.id)
                        },
                        onClick = {
                            if (multiSelect) {
                                // 多选模式：切换临时选择状态
                                val newTempSelected = tempSelectedIds.toMutableSet()
                                if (newTempSelected.contains(category.id)) {
                                    newTempSelected.remove(category.id)
                                } else {
                                    newTempSelected.add(category.id)
                                }
                                tempSelectedIds = newTempSelected
                            } else {
                                // 单选模式：立即应用选择并关闭对话框
                                onCategorySelect(category.id)
                                onDismiss()
                            }
                        }
                    )
                }
            }
        }

        // 底部操作按钮 - 只保留取消和完成按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 取消按钮
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.cancel))
            }
            
            // 多选模式显示完成按钮
            if (multiSelect) {
                Button(
                    onClick = {
                        // 应用所有临时选择的变更
                        val currentSelected = selectedCategoryIds.toMutableSet()
                        val tempSelected = tempSelectedIds
                        
                        // 处理新增的选择
                        tempSelected.forEach { categoryId ->
                            if (!currentSelected.contains(categoryId)) {
                                onCategorySelect(categoryId)
                            }
                        }
                        
                        // 处理取消的选择
                        currentSelected.forEach { categoryId ->
                            if (!tempSelected.contains(categoryId)) {
                                onCategorySelect(categoryId) // 这会切换选择状态
                            }
                        }
                        
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.done))
                }
            }
        }
    }
}

/**
 * 可选择的分类项 - 列表中的单个分类选择项
 */
@Composable
fun SelectableCategoryItem(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp) // 适合列表项的高度
            .clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        border = if (isSelected) {
            BorderStroke(1.5.dp, Color(category.color.toColorInt()))
        } else {
            BorderStroke(1.dp, Color.Transparent)
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 颜色圆点
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(category.color.toColorInt()))
            )
            
            // 分类名称 - 占据可用空间
            Text(
                text = getCategoryDisplayName(category = category),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            // 勾选图标 - 固定在右侧
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color(category.color.toColorInt())
                )
            } else {
                Icon(
                    Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 分类创建内容 - 可以嵌入到其他对话框中的内容组件
 * 基于分类管理页面的设计标准
 */
@Composable
fun CategoryCreationContent(
    title: String = "",
    category: Category? = null,
    onSave: (String, String) -> Unit,
    onCancel: () -> Unit,
    isSaving: Boolean = false,
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    val initialHsv = remember(category?.color) {
        colorToHsv(category?.color ?: DEFAULT_CATEGORY_COLOR)
    }
    var hue by remember(category?.color) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(category?.color) { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember(category?.color) { mutableFloatStateOf(initialHsv[2]) }
    val selectedColor = remember(hue, saturation, brightness) {
        hsvToHex(hue, saturation, brightness)
    }

    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        
        // 分类名称输入
        Text(
            text = stringResource(R.string.category_name),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        BasicTextField(
            value = name,
            onValueChange = { name = it },
            enabled = !isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    MaterialTheme.shapes.medium,
                )
                .padding(16.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            decorationBox = { innerTextField ->
                if (name.isEmpty()) {
                    Text(
                        text = stringResource(R.string.enter_category_name),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                innerTextField()
            }
        )
        
        // 颜色选择
        Text(
            text = stringResource(R.string.select_color),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        SlidingColorPalette(
            hue = hue,
            saturation = saturation,
            brightness = brightness,
            enabled = !isSaving,
            onColorChange = { newHue, newSaturation, newBrightness ->
                hue = newHue
                saturation = newSaturation
                brightness = newBrightness
            },
        )
        
        // 按钮行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onCancel,
                enabled = !isSaving,
            ) {
                Text(stringResource(R.string.cancel))
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name, selectedColor)
                    }
                },
                enabled = name.isNotBlank() && !isSaving,
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

/** 连续 HSV 调色板：二维区域选择饱和度/明度，底部色带选择色相。 */
@Composable
private fun SlidingColorPalette(
    hue: Float,
    saturation: Float,
    brightness: Float,
    enabled: Boolean,
    onColorChange: (hue: Float, saturation: Float, brightness: Float) -> Unit,
) {
    val hueColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    val selectedColor = Color(
        AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)),
    )
    val outlineColor = MaterialTheme.colorScheme.outline
    val surfaceColor = MaterialTheme.colorScheme.surface
    val selectedColorHex = remember(hue, saturation, brightness) {
        hsvToHex(hue, saturation, brightness)
    }
    val hueColors = remember {
        listOf(
            Color.Red,
            Color.Yellow,
            Color.Green,
            Color.Cyan,
            Color.Blue,
            Color.Magenta,
            Color.Red,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .clip(MaterialTheme.shapes.medium)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                .pointerInput(enabled, hue) {
                    if (enabled) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)

                            fun updateColor(position: Offset) {
                                val paletteWidth = size.width.toFloat().coerceAtLeast(1f)
                                val paletteHeight = size.height.toFloat().coerceAtLeast(1f)
                                onColorChange(
                                    hue,
                                    (position.x / paletteWidth).coerceIn(0f, 1f),
                                    (1f - position.y / paletteHeight).coerceIn(0f, 1f),
                                )
                            }

                            updateColor(down.position)
                            down.consume()
                            var change = down
                            while (change.pressed) {
                                val event = awaitPointerEvent()
                                change = event.changes.firstOrNull { it.id == down.id } ?: break
                                updateColor(change.position)
                                change.consume()
                            }
                        }
                    }
                },
        ) {
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.White, hueColor)),
            )
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
            )

            val outerRadius = 11.dp.toPx()
            val indicatorX = (saturation * size.width).coerceIn(
                outerRadius,
                size.width - outerRadius,
            )
            val indicatorY = ((1f - brightness) * size.height).coerceIn(
                outerRadius,
                size.height - outerRadius,
            )
            val indicatorCenter = Offset(indicatorX, indicatorY)

            drawCircle(
                color = Color.Black.copy(alpha = 0.35f),
                radius = outerRadius,
                center = indicatorCenter,
                style = Stroke(width = 1.dp.toPx()),
            )
            drawCircle(
                color = selectedColor,
                radius = 8.dp.toPx(),
                center = indicatorCenter,
            )
            drawCircle(
                color = Color.White,
                radius = 9.dp.toPx(),
                center = indicatorCenter,
                style = Stroke(width = 2.dp.toPx()),
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .pointerInput(enabled, saturation, brightness) {
                    if (enabled) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)

                            fun updateHue(position: Offset) {
                                val trackWidth = size.width.toFloat().coerceAtLeast(1f)
                                val newHue = (position.x / trackWidth * 360f).coerceIn(0f, 360f)
                                onColorChange(newHue, saturation, brightness)
                            }

                            updateHue(down.position)
                            down.consume()
                            var change = down
                            while (change.pressed) {
                                val event = awaitPointerEvent()
                                change = event.changes.firstOrNull { it.id == down.id } ?: break
                                updateHue(change.position)
                                change.consume()
                            }
                        }
                    }
                },
        ) {
            val trackHeight = 12.dp.toPx()
            val trackTop = (size.height - trackHeight) / 2f
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = hueColors,
                    startX = 0f,
                    endX = size.width,
                ),
                topLeft = Offset(0f, trackTop),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f),
            )

            val thumbRadius = 10.dp.toPx()
            val thumbX = (hue / 360f * size.width).coerceIn(
                thumbRadius,
                size.width - thumbRadius,
            )
            val thumbCenter = Offset(thumbX, size.height / 2f)
            drawCircle(
                color = surfaceColor,
                radius = thumbRadius,
                center = thumbCenter,
            )
            drawCircle(
                color = outlineColor,
                radius = thumbRadius,
                center = thumbCenter,
                style = Stroke(width = 1.dp.toPx()),
            )
            drawCircle(
                color = hueColor,
                radius = 6.dp.toPx(),
                center = thumbCenter,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(selectedColor)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
            Text(
                text = selectedColorHex,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun colorToHsv(colorHex: String): FloatArray {
    val colorInt = runCatching { colorHex.toColorInt() }
        .getOrElse { DEFAULT_CATEGORY_COLOR.toColorInt() }
    return FloatArray(3).also { hsv -> AndroidColor.colorToHSV(colorInt, hsv) }
}

private fun hsvToHex(hue: Float, saturation: Float, brightness: Float): String {
    val colorInt = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness))
    return String.format(Locale.US, "#%06X", colorInt and 0xFFFFFF)
}
