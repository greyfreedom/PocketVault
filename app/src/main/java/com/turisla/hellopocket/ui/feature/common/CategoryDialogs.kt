package com.turisla.hellopocket.ui.feature.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.toColorInt
import com.turisla.hellopocket.R
import com.turisla.hellopocket.model.Category

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
            shape = RoundedCornerShape(16.dp)
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
            .background(MaterialTheme.colorScheme.background)
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
                fontWeight = FontWeight.Bold
            )
            
            // 创建新分类图标按钮
            IconButton(
                onClick = onCreateNew,
                modifier = Modifier.size(40.dp)
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, Color(category.color.toColorInt()))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        )
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
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    var selectedColor by remember { mutableStateOf(category?.color ?: "#2196F3") }
    
    val predefinedColors = listOf(
        // 基础颜色
        "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5",
        "#2196F3", "#03A9F4", "#00BCD4", "#009688", "#4CAF50",
        "#8BC34A", "#CDDC39", "#FFEB3B", "#FFC107", "#FF9800",
        "#FF5722", "#795548", "#9E9E9E", "#607D8B", "#000000",
        
        // 浅色调
        "#FFCDD2", "#F8BBD9", "#E1BEE7", "#D1C4E9", "#C5CAE9",
        "#BBDEFB", "#B3E5FC", "#B2EBF2", "#B2DFDB", "#C8E6C9",
        "#DCEDC8", "#F0F4C3", "#FFF9C4", "#FFECB3", "#FFE0B2",
        "#FFCCBC", "#D7CCC8", "#F5F5F5", "#CFD8DC", "#EFEBE9",
        
        // 深色调
        "#B71C1C", "#880E4F", "#4A148C", "#311B92", "#1A237E",
        "#0D47A1", "#01579B", "#006064", "#004D40", "#1B5E20",
        "#33691E", "#827717", "#F57F17", "#FF6F00", "#E65100",
        "#BF360C", "#3E2723", "#212121", "#263238", "#5D4037",
        
        // 特殊颜色
        "#FF1744", "#F50057", "#D500F9", "#651FFF", "#3D5AFE",
        "#2979FF", "#00B0FF", "#00E5FF", "#1DE9B6", "#00E676",
        "#76FF03", "#C6FF00", "#FFEA00", "#FFC400", "#FF9100",
        "#FF3D00", "#DD2C00", "#6A4C93", "#A8E6CF", "#FFD3A5"
    )

    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
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
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)
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
        
        // 颜色选择网格
        LazyVerticalGrid(
            columns = GridCells.FixedSize(30.dp), // 8列颜色网格
            modifier = Modifier.height(200.dp), // 固定高度以支持滚动
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(4.dp)
        ) {
            items(predefinedColors, key = { it }) { color ->
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(color.toColorInt()))
                        .clickable { selectedColor = color }
                        .let { modifier ->
                            if (selectedColor == color) {
                                modifier.border(2.dp, Color.White, CircleShape)
                            } else {
                                modifier
                            }
                        }
                ) {
                    if (selectedColor == color) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }
        
        // 按钮行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name, selectedColor)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}
