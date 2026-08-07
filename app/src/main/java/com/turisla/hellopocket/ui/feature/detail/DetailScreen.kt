package com.turisla.hellopocket.ui.feature.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.turisla.hellopocket.R
import com.turisla.hellopocket.model.Category
import com.turisla.hellopocket.model.AttachmentManifestEntry
import com.turisla.hellopocket.model.PasswordEntry
import com.turisla.hellopocket.model.VaultItemType
import com.turisla.hellopocket.ui.feature.common.CategoryCreationContent
import com.turisla.hellopocket.ui.feature.common.ConfirmDeleteDialog
import com.turisla.hellopocket.ui.feature.common.CustomFieldDraft
import com.turisla.hellopocket.ui.feature.common.CustomFieldEditSection
import com.turisla.hellopocket.ui.feature.common.CustomFieldViewSection
import com.turisla.hellopocket.ui.feature.common.ResetSensitiveStateOnBackground
import com.turisla.hellopocket.ui.feature.common.SelectCategoryContent
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import androidx.core.graphics.toColorInt
import com.turisla.hellopocket.ui.feature.common.getCategoryDisplayName
import com.turisla.hellopocket.ui.feature.common.AttachmentSection

/**
 * Create colored password text with different colors for different character types
 */
@Composable
fun createColoredPasswordText(password: String): AnnotatedString {
    return buildAnnotatedString {
        password.forEach { char ->
            when {
                char.isDigit() -> {
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append(char)
                    }
                }
                char.isLetter() -> {
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                        append(char)
                    }
                }
                else -> {
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.secondary)) {
                        append(char)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit, 
    onNavigateToGenerator: () -> Unit = {},
    onNavigateToCategoryManagement: () -> Unit = {},
    onLoading: (Boolean) -> Unit = {}, // 新增加载状态回调
    viewModel: DetailViewModel = koinViewModel(),
    editDraftViewModel: DetailEditDraftViewModel = koinViewModel(),
) {
    val entry by viewModel.passwordEntry.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
    val editDraft by editDraftViewModel.draft.collectAsStateWithLifecycle()
    val isAttachmentLoading by viewModel.isAttachmentLoading.collectAsStateWithLifecycle()
    val isDeleting by viewModel.isDeleting.collectAsStateWithLifecycle()
    val event by viewModel.event.collectAsStateWithLifecycle()
    val isInEditMode = editDraft.isEditing
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel, context) {
        viewModel.repositoryErrorEvents.collect { error ->
            android.widget.Toast.makeText(
                context,
                context.getString(error.messageResId),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEntryActions by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var customFieldValidationAttempted by remember { mutableStateOf(false) }
    val isBusy = isSaving || isAttachmentLoading || isDeleting
    val scrollState = rememberScrollState()
    val customFieldSnackbarHostState = remember { SnackbarHostState() }

    // 编辑时返回会放弃草稿；持久化期间则阻止退出，避免取消页面作用域中的事务。
    BackHandler(enabled = isInEditMode || isBusy) {
        if (!isBusy) {
            customFieldValidationAttempted = false
            editDraftViewModel.clear()
        }
    }

    LaunchedEffect(event) {
        event?.let { currentEvent ->
            viewModel.consumeEvent(currentEvent)
            when (currentEvent) {
                is DetailViewModel.Event.NavigateBack -> onBack()
            }
        }
    }

    if (showDeleteDialog) {
        ConfirmDeleteDialog(
            itemName = entry?.title,
            onConfirm = { viewModel.deletePassword() },
            onDismiss = { showDeleteDialog = false }
        )
    }

    val currentOnLoading by rememberUpdatedState(onLoading)

    LaunchedEffect(isSaving, isAttachmentLoading, isDeleting) {
        currentOnLoading(isSaving || isAttachmentLoading || isDeleting)
    }
    DisposableEffect(Unit) {
        onDispose { currentOnLoading(false) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(customFieldSnackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isInEditMode) {
                            stringResource(R.string.edit_mode)
                        } else {
                            stringResource(R.string.detail)
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    if (isInEditMode) {
                        IconButton(
                            onClick = {
                                customFieldValidationAttempted = false
                                editDraftViewModel.clear()
                            },
                            enabled = !isBusy,
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    } else {
                        IconButton(onClick = onBack, enabled = !isBusy) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (isInEditMode) {
                        IconButton(onClick = {
                            entry?.let {
                                val validationMessage = when {
                                    editDraft.title.isBlank() -> context.getString(R.string.title_cannot_be_empty)
                                    it.type == VaultItemType.PASSWORD && editDraft.password.isBlank() -> {
                                        context.getString(R.string.title_and_password_required)
                                    }
                                    editDraft.customFields.any { field -> field.name.isBlank() } -> {
                                        customFieldValidationAttempted = true
                                        context.getString(R.string.custom_field_name_required)
                                    }
                                    else -> null
                                }
                                if (validationMessage != null) {
                                    android.widget.Toast.makeText(
                                        context,
                                        validationMessage,
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    return@let
                                }
                                val updatedEntry = editDraftViewModel.buildUpdatedEntry(it)
                                
                                scope.launch {
                                    isSaving = true
                                    try {
                                        viewModel.updatePassword(updatedEntry)
                                        customFieldValidationAttempted = false
                                        editDraftViewModel.clear()
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(
                                            context,
                                            context.getString(R.string.save_failed),
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            }
                        }, enabled = !isBusy) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
                        }
                    } else {
                        FilledTonalIconButton(
                            onClick = {
                                customFieldValidationAttempted = false
                                entry?.let(editDraftViewModel::beginEditing)
                            },
                            enabled = !isBusy,
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                        }
                        Box {
                            IconButton(
                                onClick = { showEntryActions = true },
                                enabled = !isBusy,
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.totp_more),
                                )
                            }
                            DropdownMenu(
                                expanded = showEntryActions,
                                onDismissRequest = { showEntryActions = false },
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(R.string.delete),
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
                                        showEntryActions = false
                                        showDeleteDialog = true
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        entry?.let { passwordEntry ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(scrollState)
            ) {
                if (isInEditMode) {
                    EditModeContent(
                        entry = passwordEntry,  // 传递整个entry以获取类型
                        title = editDraft.title,
                        onTitleChange = editDraftViewModel::updateTitle,
                        username = editDraft.username,
                        onUsernameChange = editDraftViewModel::updateUsername,
                        password = editDraft.password,
                        onPasswordChange = editDraftViewModel::updatePassword,
                        notes = editDraft.notes,
                        onNotesChange = editDraftViewModel::updateNotes,
                        customFields = editDraft.customFields,
                        onCustomFieldAdd = editDraftViewModel::addCustomField,
                        onCustomFieldNameChange = { id, value ->
                            customFieldValidationAttempted = false
                            editDraftViewModel.updateCustomFieldName(id, value)
                        },
                        onCustomFieldValueChange = editDraftViewModel::updateCustomFieldValue,
                        onCustomFieldTypeChange = editDraftViewModel::updateCustomFieldType,
                        onCustomFieldRemove = editDraftViewModel::removeCustomField,
                        onCustomFieldRestore = editDraftViewModel::restoreCustomField,
                        onCustomFieldMove = editDraftViewModel::moveCustomField,
                        onCopyCustomFieldValue = viewModel::copyCustomFieldValue,
                        showCustomFieldValidationErrors = customFieldValidationAttempted,
                        parentScrollState = scrollState,
                        customFieldSnackbarHostState = customFieldSnackbarHostState,
                        categories = categories,
                        selectedCategoryIds = editDraft.selectedCategoryIds,
                        onCategoryAdd = editDraftViewModel::addCategory,
                        onCategoryRemove = editDraftViewModel::removeCategory,
                        selectedAttachmentIds = editDraft.selectedAttachmentIds,
                        onAttachmentAdd = editDraftViewModel::addAttachment,
                        onAttachmentRemove = editDraftViewModel::removeAttachment,
                        onNavigateToGenerator = onNavigateToGenerator,
                        attachments = attachments,
                        onAddAttachment = viewModel::addAttachment,
                        onLoadAttachmentThumbnail = viewModel::loadAttachmentThumbnail,
                        onCreateCategory = viewModel::addCategory
                    )
                } else {
                    ViewModeContent(
                        entry = passwordEntry,
                        categories = categories,
                        attachments = attachments,
                        onCopyUsername = viewModel::onCopyUsername,
                        onCopyPassword = viewModel::onCopyPassword,
                        onCopyCustomField = viewModel::onCopyCustomField,
                        onGetAttachmentFile = viewModel::getAttachmentFile,
                        onLoadAttachmentThumbnail = viewModel::loadAttachmentThumbnail,
                        isNote = passwordEntry.type == VaultItemType.NOTE  // 传递是否为笔记的标志
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryIdentityHeader(entry: PasswordEntry) {
    val isNote = entry.type == VaultItemType.NOTE
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = if (isNote) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isNote) Icons.Filled.NoteAlt else Icons.Outlined.Key,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = if (isNote) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                )
            }
        }
        Text(
            text = entry.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(
                if (isNote) R.string.type_note else R.string.type_password,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ViewModeContent(
    entry: PasswordEntry, 
    categories: List<Category>,
    attachments: List<AttachmentManifestEntry>,
    onCopyUsername: () -> Unit,
    onCopyPassword: () -> Unit,
    onCopyCustomField: (String) -> Unit,
    onGetAttachmentFile: suspend (String) -> java.io.File?,
    onLoadAttachmentThumbnail: suspend (String) -> Any?,
    isNote: Boolean = false  // 新增：是否为笔记类型
) {
    var passwordVisible by remember { mutableStateOf(false) }
    ResetSensitiveStateOnBackground { passwordVisible = false }

    EntryIdentityHeader(entry = entry)

    // 根据类型显示不同的内容区域
    if (isNote) {
        // 笔记类型：显示内容区域
        if (entry.content.isNotBlank()) {
            SectionHeader(title = stringResource(R.string.content))
            CardContainer {
                DetailNotesSection(notes = entry.content)  // 复用DetailNotesSection组件
            }
        }
    } else {
        // 密码类型：显示登录信息区域
        SectionHeader(title = stringResource(R.string.login_info))
        CardContainer {
            // Username section
            DetailItemSection(
                title = stringResource(R.string.username),
                value = entry.username,
                trailingIcon = Icons.Outlined.PersonOutline,
                onCopy = onCopyUsername
            )

            HorizontalDivider(
                Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp
            )

            // Password section
            DetailPasswordSection(
                title = stringResource(R.string.password),
                value = entry.password,
                isVisible = passwordVisible,
                onVisibilityToggle = { passwordVisible = !passwordVisible },
                onCopy = onCopyPassword
            )
        }

    }

    CustomFieldViewSection(
        fields = entry.customFieldsList,
        onCopy = onCopyCustomField,
    )

    // Notes section (仅密码类型显示)，放在结构化自定义字段之后。
    if (!isNote && entry.notes.isNotBlank()) {
        SectionHeader(title = stringResource(R.string.notes))
        CardContainer {
            DetailNotesSection(notes = entry.notes)
        }
    }

    // 分类显示区域
    if (entry.categoryIdsList.isNotEmpty()) {
        SectionHeader(title = stringResource(R.string.category))
        CardContainer {
            DetailCategorySection(
                categoryIds = entry.categoryIdsList,
                categories = categories
            )
        }
    }

    // 附件显示区域
    val entryAttachmentIds = remember(entry) { entry.attachmentIdsList.toSet() }
    val hasAttachments = remember(attachments, entryAttachmentIds) {
        attachments.any { it.id in entryAttachmentIds }
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    if (hasAttachments) {
        SectionHeader(title = stringResource(R.string.attachments))
        CardContainer {
            AttachmentSection(
                attachmentIds = entryAttachmentIds,
                allAttachments = attachments,
                onAddAttachment = {}, // 查看模式不可添加
                onRemoveAttachment = {}, // 查看模式不可删除
                onViewAttachment = { attachmentId ->
                    scope.launch {
                        val file = onGetAttachmentFile(attachmentId)
                        if (file != null) {
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, attachments.find { it.id == attachmentId }?.mimeType)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.no_app_found),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                },
                isEditable = false, // 查看模式不可编辑
                loadThumbnail = onLoadAttachmentThumbnail
            )
        }
    }
    
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun EditModeContent(
    entry: PasswordEntry,  // 新增：传入整个entry以获取类型
    title: String,
    onTitleChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    notes: TextFieldValue,
    onNotesChange: (TextFieldValue) -> Unit,
    customFields: List<CustomFieldDraft>,
    onCustomFieldAdd: (com.turisla.hellopocket.model.CustomFieldType) -> String?,
    onCustomFieldNameChange: (String, String) -> Unit,
    onCustomFieldValueChange: (String, String) -> Unit,
    onCustomFieldTypeChange: (String, com.turisla.hellopocket.model.CustomFieldType) -> Unit,
    onCustomFieldRemove: (String) -> Unit,
    onCustomFieldRestore: (CustomFieldDraft, Int) -> Unit,
    onCustomFieldMove: (String, Int) -> Unit,
    onCopyCustomFieldValue: (String) -> Unit,
    showCustomFieldValidationErrors: Boolean,
    parentScrollState: androidx.compose.foundation.ScrollState,
    customFieldSnackbarHostState: SnackbarHostState,
    categories: List<Category>,
    selectedCategoryIds: Set<String>,
    onCategoryAdd: (String) -> Unit,
    onCategoryRemove: (String) -> Unit,
    selectedAttachmentIds: Set<String>,
    onAttachmentAdd: (String) -> Unit,
    onAttachmentRemove: (String) -> Unit,
    onNavigateToGenerator: () -> Unit = {},
    attachments: List<AttachmentManifestEntry>,
    onAddAttachment: suspend (android.net.Uri) -> String,
    onLoadAttachmentThumbnail: suspend (String) -> Any?,
    onCreateCategory: suspend (String, String) -> String
) {
    val isNote = entry.type == VaultItemType.NOTE  // 判断是否为笔记类型
    var passwordVisible by remember { mutableStateOf(false) }
    ResetSensitiveStateOnBackground { passwordVisible = false }

    // Title section
    SectionHeader(title = stringResource(R.string.title))
    CardContainer {
        EditItemSection(
            title = stringResource(R.string.title_required),
            value = title,
            onValueChange = onTitleChange
        )
    }

    // 根据类型显示不同的编辑区域
    if (isNote) {
        // 笔记类型：显示内容编辑区域
        SectionHeader(title = stringResource(R.string.content))
        CardContainer {
            EditNoteSection(
                value = notes,  // 笔记类型使用notes字段存储content
                onValueChange = onNotesChange
            )
        }
    } else {
        // 密码类型：显示登录信息编辑区域
        SectionHeader(title = stringResource(R.string.login_info))
        CardContainer {
            // Username section
            EditItemSection(
                title = stringResource(R.string.username_optional),
                value = username,
                onValueChange = onUsernameChange,
                trailingIcon = Icons.Outlined.PersonOutline
            )

            HorizontalDivider(
                Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp
            )

            // Password section
            EditPasswordSection(
                title = stringResource(R.string.password_required),
                value = password,
                onValueChange = onPasswordChange,
                isVisible = passwordVisible,
                onVisibilityToggle = { passwordVisible = !passwordVisible },
                onGenerateClick = onNavigateToGenerator
            )
        }

    }

    CustomFieldEditSection(
        fields = customFields,
        parentScrollState = parentScrollState,
        snackbarHostState = customFieldSnackbarHostState,
        showValidationErrors = showCustomFieldValidationErrors,
        onAdd = onCustomFieldAdd,
        onNameChange = onCustomFieldNameChange,
        onValueChange = onCustomFieldValueChange,
        onTypeChange = onCustomFieldTypeChange,
        onRemove = onCustomFieldRemove,
        onRestore = onCustomFieldRestore,
        onMove = onCustomFieldMove,
        onCopy = onCopyCustomFieldValue,
    )

    // Notes section (仅密码类型显示)
    if (!isNote) {
        SectionHeader(title = stringResource(R.string.notes))
        CardContainer {
            EditNoteSection(
                value = notes,
                onValueChange = onNotesChange
            )
        }
    }

    // 分类选择区域
    SectionHeader(title = stringResource(R.string.category))
    CardContainer {
        EditCategorySection(
            categories = categories,
            selectedCategoryIds = selectedCategoryIds,
            onCategoryAdd = onCategoryAdd,
            onCategoryRemove = onCategoryRemove,
            onCreateCategory = onCreateCategory
        )
    }

    // 附件编辑区域
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    
    SectionHeader(title = stringResource(R.string.attachments))
    CardContainer {
        AttachmentSection(
            attachmentIds = selectedAttachmentIds,
            allAttachments = attachments,
            onAddAttachment = { uri ->
                scope.launch {
                    try {
                        val id = onAddAttachment(uri)
                        onAttachmentAdd(id)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: IllegalArgumentException) {
                        // 文件大小超限
                        android.widget.Toast.makeText(ctx, R.string.attachment_add_failed, android.widget.Toast.LENGTH_LONG).show()
                    } catch (_: Exception) {
                        android.widget.Toast.makeText(ctx, R.string.attachment_add_failed, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onRemoveAttachment = onAttachmentRemove,
            loadThumbnail = onLoadAttachmentThumbnail
        )
    }
    
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun CardContainer(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        content()
    }
}

@Composable
private fun DetailItemSection(
    title: String,
    value: String,
    trailingIcon: ImageVector? = null,
    onCopy: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // SelectionContainer 才是 Row 的直接子项，权重必须放在这里，
            // 否则长文本会按完整宽度测量并把右侧操作按钮挤出卡片。
            SelectionContainer(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = value.ifEmpty { "" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                trailingIcon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                onCopy?.let {
                    IconButton(
                        onClick = it,
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.copy),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailPasswordSection(
    title: String,
    value: String,
    isVisible: Boolean,
    onVisibilityToggle: () -> Unit,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 密码内容只使用操作区之外的剩余宽度；显示超长密码时允许文本换行，
            // 查看和复制按钮始终保留在卡片右侧。
            SelectionContainer(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = if (isVisible) createColoredPasswordText(value) else AnnotatedString("••••••••"),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row {
                IconButton(
                    onClick = onVisibilityToggle,
                ) {
                    Icon(
                        imageVector = if (isVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        contentDescription = stringResource(
                            if (isVisible) R.string.hide_password else R.string.show_password
                        ),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onCopy,
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.copy),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailNotesSection(notes: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        SelectionContainer {
            Text(
                text = notes,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun EditItemSection(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    trailingIcon: ImageVector? = null,
    isError: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            )

            trailingIcon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun EditPasswordSection(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    isVisible: Boolean,
    onVisibilityToggle: () -> Unit,
    onGenerateClick: () -> Unit = {},
    isError: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false
                ),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            )

            Row {
                IconButton(
                    onClick = onVisibilityToggle,
                ) {
                    Icon(
                        imageVector = if (isVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        contentDescription = stringResource(
                            if (isVisible) R.string.hide_password else R.string.show_password
                        ),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onGenerateClick,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoFixHigh,
                        contentDescription = stringResource(R.string.generate_password),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EditNoteSection(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            onTextLayout = { layoutResult ->
                coroutineScope.launch {
                    try {
                        if (value.selection.collapsed) {
                            val rect = layoutResult.getCursorRect(value.selection.end)
                            bringIntoViewRequester.bringIntoView(rect)
                        }
                    } catch (e: Exception) {
                        // Ignore layout errors during scrolling
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 80.dp)
                .bringIntoViewRequester(bringIntoViewRequester),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                if (value.text.isEmpty()) {
                    Text(
                        text = "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                innerTextField()
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
    )
}

/**
 * 分类显示区域（查看模式）
 */
@Composable
private fun DetailCategorySection(
    categoryIds: List<String>,
    categories: List<Category>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(categoryIds, key = { it }) { categoryId ->
                val category = categories.find { it.id == categoryId }
                if (category != null) {
                    CategoryDisplayChip(category = category)
                }
            }
        }
    }
}

/**
 * 分类选择区域（编辑模式）
 */
@Composable
private fun EditCategorySection(
    categories: List<Category>,
    selectedCategoryIds: Set<String>,
    onCategoryAdd: (String) -> Unit,
    onCategoryRemove: (String) -> Unit,
    onCreateCategory: suspend (String, String) -> String
) {
    var showCategoryDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.categories),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            IconButton(
                onClick = { showCategoryDialog = true },
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_category),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 显示已选分类
        val selectedCategories = categories.filter { selectedCategoryIds.contains(it.id) }
        if (selectedCategories.isEmpty()) {
            Text(
                text = stringResource(R.string.no_categories_selected),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(selectedCategories, key = { it.id }) { category ->
                    SelectedCategoryChip(
                        category = category,
                        onRemove = { onCategoryRemove(category.id) }
                    )
                }
            }
        }
    }
    
    // 分类选择对话框
    if (showCategoryDialog) {
        CategorySelectionDialog(
            categories = categories.filter { !it.id.startsWith("default_") },
            selectedCategoryIds = selectedCategoryIds,
            onCategorySelect = { categoryId ->
                onCategoryAdd(categoryId)
            },
            onDismiss = { showCategoryDialog = false },
            onCreateCategory = onCreateCategory
        )
    }
}

/**
 * 分类显示切片（查看模式）
 */
@Composable
private fun CategoryDisplayChip(category: Category) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(category.color.toColorInt()).copy(alpha = 0.2f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(category.color.toColorInt()))
            )
            
            Text(
                text = getCategoryDisplayName(category = category),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 已选分类切片（带删除按钮）
 */
@Composable
private fun SelectedCategoryChip(
    category: Category,
    onRemove: () -> Unit
) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = {
            Text(
                text = getCategoryDisplayName(category),
                style = MaterialTheme.typography.labelMedium,
            )
        },
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
                Icons.Default.Close,
                contentDescription = stringResource(R.string.remove_category),
                modifier = Modifier.size(InputChipDefaults.IconSize),
            )
        },
        shape = MaterialTheme.shapes.small,
        colors = InputChipDefaults.inputChipColors(
            selectedContainerColor = Color(category.color.toColorInt()).copy(alpha = 0.18f),
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

/**
 * 分类选择对话框
 */
@Composable
private fun CategorySelectionDialog(
    categories: List<Category>,
    selectedCategoryIds: Set<String>,
    onCategorySelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreateCategory: suspend (String, String) -> String
) {
    var showCreateCategory by remember { mutableStateOf(false) }
    var newlyCreatedCategoryId by remember { mutableStateOf<String?>(null) }
    var isCreatingCategory by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    Dialog(
        onDismissRequest = { if (!isCreatingCategory) onDismiss() },
        properties = DialogProperties(dismissOnClickOutside = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            if (showCreateCategory) {
                // 使用统一的分类编辑对话框内容，但嵌入在当前对话框中
                CategoryCreationContent(
                    onSave = { name, color ->
                        if (!isCreatingCategory) {
                            isCreatingCategory = true
                            // 分类持久化完成前保持对话框，避免页面作用域取消写入。
                            coroutineScope.launch {
                                try {
                                    val newCategoryId = onCreateCategory(name, color)
                                    newlyCreatedCategoryId = newCategoryId
                                    showCreateCategory = false
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                    android.widget.Toast.makeText(
                                        context,
                                        R.string.create_category_failed,
                                        android.widget.Toast.LENGTH_SHORT,
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
                    onCategorySelect = { categoryId ->
                        onCategorySelect(categoryId)
                    },
                    onCreateNew = { 
                        newlyCreatedCategoryId = null // 清除之前的新创建状态
                        showCreateCategory = true 
                    },
                    onDismiss = onDismiss,
                    multiSelect = true,
                    newlyCreatedCategoryId = newlyCreatedCategoryId
                )
            }
        }
    }
}
