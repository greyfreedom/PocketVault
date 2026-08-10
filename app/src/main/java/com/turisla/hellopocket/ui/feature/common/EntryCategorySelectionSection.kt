package com.turisla.hellopocket.ui.feature.common

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.toColorInt
import com.turisla.hellopocket.R
import com.turisla.hellopocket.model.Category
import com.turisla.hellopocket.utils.AppConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 新增条目页面共用的分类选择器，分类写入完成前保持对话框存活。 */
@Composable
fun EntryCategorySelectionSection(
    categories: List<Category>,
    selectedCategoryIds: Set<String>,
    onCategoryAdd: (String) -> Unit,
    onCategoryRemove: (String) -> Unit,
    onCreateCategory: suspend (String, String) -> String,
) {
    var showCategoryDialog by remember { mutableStateOf(false) }
    val isFavorite = AppConstants.CATEGORY_ID_FAVORITES in selectedCategoryIds

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.categories),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row {
                IconButton(
                    onClick = {
                        if (isFavorite) {
                            onCategoryRemove(AppConstants.CATEGORY_ID_FAVORITES)
                        } else {
                            onCategoryAdd(AppConstants.CATEGORY_ID_FAVORITES)
                        }
                    },
                ) {
                    Icon(
                        imageVector = if (isFavorite) {
                            Icons.Filled.Favorite
                        } else {
                            Icons.Outlined.FavoriteBorder
                        },
                        contentDescription = stringResource(
                            if (isFavorite) {
                                R.string.remove_from_favorites
                            } else {
                                R.string.add_to_favorites
                            },
                        ),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = { showCategoryDialog = true }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.add_category),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        val selectedCategories = remember(categories, selectedCategoryIds) {
            categories.filter { it.id in selectedCategoryIds }
        }
        if (selectedCategories.isEmpty()) {
            Text(
                text = stringResource(R.string.no_categories_selected),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                items(selectedCategories, key = Category::getId) { category ->
                    InputChip(
                        selected = true,
                        onClick = { onCategoryRemove(category.id) },
                        label = { Text(getCategoryDisplayName(category)) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(category.color.toColorInt())),
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.remove_category),
                                modifier = Modifier.size(InputChipDefaults.IconSize),
                            )
                        },
                        shape = MaterialTheme.shapes.small,
                    )
                }
            }
        }
    }

    if (showCategoryDialog) {
        EntryCategorySelectionDialog(
            categories = categories.filterNot { it.id.startsWith("default_") },
            selectedCategoryIds = selectedCategoryIds,
            onCategorySelect = { categoryId ->
                if (categoryId in selectedCategoryIds) {
                    onCategoryRemove(categoryId)
                } else {
                    onCategoryAdd(categoryId)
                }
            },
            onCreateCategory = onCreateCategory,
            onDismiss = { showCategoryDialog = false },
        )
    }
}

@Composable
private fun EntryCategorySelectionDialog(
    categories: List<Category>,
    selectedCategoryIds: Set<String>,
    onCategorySelect: (String) -> Unit,
    onCreateCategory: suspend (String, String) -> String,
    onDismiss: () -> Unit,
) {
    var showCreateCategory by remember { mutableStateOf(false) }
    var newlyCreatedCategoryId by remember { mutableStateOf<String?>(null) }
    var isCreatingCategory by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Dialog(
        onDismissRequest = { if (!isCreatingCategory) onDismiss() },
        properties = DialogProperties(dismissOnClickOutside = false),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            if (showCreateCategory) {
                CategoryCreationContent(
                    onSave = { name, color ->
                        if (!isCreatingCategory) {
                            isCreatingCategory = true
                            scope.launch {
                                try {
                                    val id = onCreateCategory(name, color)
                                    // 新分类先进入选择对话框的临时状态；只有点击“完成”才关联到草稿，
                                    // 因而用户随后点击“取消”时不会留下意外关联。
                                    newlyCreatedCategoryId = id
                                    showCreateCategory = false
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                    Toast.makeText(
                                        context,
                                        R.string.create_category_failed,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } finally {
                                    isCreatingCategory = false
                                }
                            }
                        }
                    },
                    onCancel = { showCreateCategory = false },
                    isSaving = isCreatingCategory,
                )
            } else {
                SelectCategoryContent(
                    categories = categories,
                    selectedCategoryIds = selectedCategoryIds,
                    onCategorySelect = onCategorySelect,
                    onCreateNew = {
                        newlyCreatedCategoryId = null
                        showCreateCategory = true
                    },
                    onDismiss = onDismiss,
                    multiSelect = true,
                    newlyCreatedCategoryId = newlyCreatedCategoryId,
                )
            }
        }
    }
}
