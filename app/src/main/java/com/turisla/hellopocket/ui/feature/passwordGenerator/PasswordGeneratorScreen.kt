package com.turisla.hellopocket.ui.feature.passwordGenerator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.turisla.hellopocket.R
import com.turisla.hellopocket.model.GeneratorMode
import com.turisla.hellopocket.model.GeneratorRule
import com.turisla.hellopocket.model.GeneratorStep
import com.turisla.hellopocket.ui.feature.common.AppSectionSurface
import com.turisla.hellopocket.ui.feature.common.ResetSensitiveStateOnBackground
import com.turisla.hellopocket.ui.theme.AppSpacing
import com.turisla.hellopocket.utils.ClipboardManagerHelper
import com.turisla.hellopocket.utils.RulePasswordGenerator
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.util.UUID
import kotlin.math.floor

private data class RuleEditorRequest(val rule: GeneratorRule?, val stepId: String? = null, val libraryOnly: Boolean = false)
private data class GeneratorDeleteRequest(val id: String, val isTemplate: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordGeneratorScreen(
    onNavigateBack: () -> Unit,
    onUsePassword: (String) -> Unit = {},
    viewModel: PasswordGeneratorViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 锁定后立即移除规则编辑器和结果；返回栈随后由统一的认证导航清理。
    if (state.locked) return
    val clipboard: ClipboardManagerHelper = koinInject()
    GeneratorClipboardProvider(onCopy = { text ->
        if (viewModel.isSessionActive()) clipboard.copyTextToClipboard(R.string.password, text)
    }) {
        PasswordGeneratorContent(state, viewModel, clipboard, onNavigateBack, onUsePassword)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordGeneratorContent(
    state: PasswordGeneratorState,
    viewModel: PasswordGeneratorViewModel,
    clipboard: ClipboardManagerHelper,
    onNavigateBack: () -> Unit,
    onUsePassword: (String) -> Unit,
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val drag = rememberGeneratorDragState(listState, state.steps, viewModel::moveStep)
    var showRules by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var showSaveTemplate by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<RuleEditorRequest?>(null) }
    var deleting by remember { mutableStateOf<GeneratorDeleteRequest?>(null) }
    val saveError = stringResource(R.string.save_failed)
    val copySuffix = stringResource(R.string.generator_name_copy)

    ResetSensitiveStateOnBackground {
        drag.stop()
        viewModel.flushSelection()
    }
    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed) {
            snackbar.showSnackbar(saveError)
            viewModel.dismissSaveError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.password_generator)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface {
                Button(
                    onClick = { viewModel.currentPassword().takeIf(String::isNotEmpty)?.let(onUsePassword) },
                    enabled = state.password.isNotEmpty() && !state.saving,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(AppSpacing.md).heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.use_password)) }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            item("mode") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    GeneratorMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.mode == mode,
                            onClick = { drag.stop(); viewModel.setMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                        ) {
                            Text(stringResource(if (mode == GeneratorMode.RANDOM) R.string.generator_mode_random else R.string.generator_mode_rules))
                        }
                    }
                }
            }
            item("result") {
                GeneratorResultPanel(
                    state = state,
                    onGenerate = viewModel::generate,
                    onCopy = {
                        viewModel.currentPassword().takeIf(String::isNotEmpty)?.let {
                            clipboard.copyTextToClipboard(R.string.password, it)
                        }
                    },
                )
            }
            if (state.rememberFailed) item("remember_error") {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.errorContainer) {
                    Column(Modifier.fillMaxWidth().padding(AppSpacing.md)) {
                        Text(stringResource(R.string.generator_remember_failed), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = viewModel::rememberCurrentSelection, modifier = Modifier.align(Alignment.End)) {
                            Text(stringResource(R.string.generator_retry))
                        }
                    }
                }
            }
            if (state.mode == GeneratorMode.RANDOM) {
                item("random_options") {
                    RandomOptionsPanel(state.options, viewModel::setOptions)
                }
            } else {
                item("composition_header") {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                state.templateName.ifBlank { stringResource(R.string.generator_current_rules) },
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(onClick = { showTemplates = true }) { Text(stringResource(R.string.generator_templates)) }
                        }
                        Text(
                            stringResource(R.string.generator_composition_count, state.steps.size, state.analysis.length),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.steps.isEmpty()) Text(stringResource(R.string.generator_empty_composition))
                    }
                }
                itemsIndexed(state.steps, key = { _, step -> step.id }) { index, step ->
                    val moveUp = stringResource(R.string.move_up)
                    val moveDown = stringResource(R.string.move_down)
                    val dragLabel = stringResource(R.string.drag_to_reorder)
                    GeneratorStepCard(
                        step = step, index = index, dragging = drag.draggedId == step.id,
                        isLast = index == state.steps.lastIndex,
                        modifier = Modifier.zIndex(if (drag.draggedId == step.id) 1f else 0f)
                            .graphicsLayer { translationY = drag.translation(step.id) },
                        dragHandle = {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .semantics {
                                        contentDescription = dragLabel
                                        customActions = buildList {
                                            if (index > 0) add(CustomAccessibilityAction(moveUp) { viewModel.moveStep(step.id, index - 1); true })
                                            if (index < state.steps.lastIndex) add(CustomAccessibilityAction(moveDown) { viewModel.moveStep(step.id, index + 1); true })
                                        }
                                    }
                                    .pointerInput(step.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { drag.start(step.id) },
                                            onDragCancel = drag::stop,
                                            onDragEnd = drag::stop,
                                            onDrag = { change, amount -> change.consume(); drag.drag(amount.y) },
                                        )
                                    },
                            ) { Icon(Icons.Default.DragHandle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        },
                        onEdit = { editor = RuleEditorRequest(GeneratorRule(step.id, step.name, step.segment), step.id) },
                        onDuplicate = { viewModel.duplicateStep(step.id) },
                        onRemove = { viewModel.removeStep(step.id) },
                        onMoveUp = { viewModel.moveStep(step.id, index - 1) },
                        onMoveDown = { viewModel.moveStep(step.id, index + 1) },
                    )
                }
                item("composition_actions") {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        OutlinedButton(onClick = { showRules = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Add, null)
                            Text(stringResource(R.string.generator_add_rule), Modifier.padding(start = AppSpacing.xs))
                        }
                        OutlinedButton(
                            onClick = { showSaveTemplate = true },
                            enabled = state.analysis.issue == null && !state.saving,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.generator_save_template)) }
                        OutlinedButton(
                            onClick = { drag.stop(); viewModel.clearRules() },
                            enabled = (state.steps.isNotEmpty() || state.templateId != null) && !state.saving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.ClearAll, null)
                            Text(stringResource(R.string.generator_clear_rules), Modifier.padding(start = AppSpacing.xs))
                        }
                    }
                }
            }
        }
    }

    if (showRules) GeneratorRulePicker(
        rules = library.rules,
        busy = state.saving,
        onDismiss = { showRules = false },
        onAdd = { viewModel.addRules(it); showRules = false },
        onNew = { showRules = false; editor = RuleEditorRequest(null) },
        onEdit = { editor = RuleEditorRequest(it, libraryOnly = true) },
        onDuplicate = { editor = RuleEditorRequest(it.copy(id = UUID.randomUUID().toString(), name = (it.name + copySuffix).take(RulePasswordGenerator.MAX_NAME_LENGTH)), libraryOnly = true) },
        onDelete = { deleting = GeneratorDeleteRequest(it.id, false) },
    )
    if (showTemplates) GeneratorTemplatePicker(
        templates = library.templates,
        busy = state.saving,
        onDismiss = { showTemplates = false },
        onSelect = { viewModel.useTemplate(it); showTemplates = false },
        onDelete = { deleting = GeneratorDeleteRequest(it.id, true) },
    )
    editor?.let { request ->
        GeneratorRuleEditor(
            initial = request.rule,
            libraryOnly = request.libraryOnly,
            busy = state.saving,
            onDismiss = { if (!state.saving) editor = null },
            onSave = { rule, saveToLibrary ->
                val applyToComposition = {
                    if (!request.libraryOnly) {
                        if (request.stepId == null) viewModel.addRules(listOf(rule)) else viewModel.updateStep(request.stepId, rule)
                    }
                    editor = null
                }
                if (request.libraryOnly || saveToLibrary) {
                    val storedRule = if (request.libraryOnly) rule else rule.copy(id = UUID.randomUUID().toString())
                    viewModel.saveRule(storedRule, applyToComposition)
                } else applyToComposition()
            },
        )
    }
    if (showSaveTemplate) GeneratorTemplateNameDialog(
        initialName = state.templateName,
        canReplace = state.templateId != null && library.templates.any { it.id == state.templateId },
        busy = state.saving,
        onDismiss = { if (!state.saving) showSaveTemplate = false },
        onSave = { name, replace -> viewModel.saveTemplate(name, replace) { showSaveTemplate = false } },
    )
    deleting?.let { request ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(if (request.isTemplate) R.string.generator_delete_template else R.string.generator_delete_rule)) },
            text = { Text(stringResource(if (request.isTemplate) R.string.generator_delete_template_hint else R.string.generator_snapshot_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    if (request.isTemplate) viewModel.deleteTemplate(request.id) else viewModel.deleteRule(request.id)
                    deleting = null
                }, enabled = !state.saving) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun GeneratorResultPanel(state: PasswordGeneratorState, onGenerate: () -> Unit, onCopy: () -> Unit) {
    val rulesMode = state.mode == GeneratorMode.RULES
    val issue = state.analysis.issue
    val canGenerate = if (rulesMode) issue == null else state.options.run { uppercase || lowercase || digits || symbols }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth().padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.generated_password), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onCopy, enabled = state.password.isNotEmpty()) {
                    Icon(Icons.Default.ContentCopy, stringResource(R.string.copy))
                }
            }
            GeneratorPasswordText(state.password)
            if (state.password.isNotEmpty()) Text(
                stringResource(R.string.generator_result_length, state.password.codePointCount(0, state.password.length)),
                style = MaterialTheme.typography.bodySmall,
            )
            if (rulesMode && issue != null) {
                val message = generatorIssueText(issue)
                Text(
                    state.analysis.stepIndex?.let { stringResource(R.string.generator_error_prefix, it + 1, message) } ?: message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (rulesMode) {
                Text(stringResource(R.string.generator_randomness, floor(state.analysis.entropyBits).toInt()), style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(if (state.analysis.entropyBits < 60) R.string.generator_randomness_low else R.string.generator_randomness_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (!canGenerate) Text(stringResource(R.string.password_generator_condition_failed))
            Button(onClick = onGenerate, enabled = canGenerate) {
                Icon(Icons.Default.Refresh, null)
                Text(stringResource(if (state.password.isEmpty()) R.string.generator_generate else R.string.refresh_password), Modifier.padding(start = AppSpacing.xs))
            }
        }
    }
}

@Composable
internal fun GeneratorPasswordText(password: String) {
    SelectionContainer {
        Text(
            password.ifEmpty { stringResource(R.string.generator_pending_result) },
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

@Composable
private fun RandomOptionsPanel(options: RandomGeneratorOptions, onChange: (RandomGeneratorOptions) -> Unit) {
    AppSectionSurface {
        Column(Modifier.padding(AppSpacing.md)) {
            Text(stringResource(R.string.password_length) + ": " + options.length, style = MaterialTheme.typography.titleMedium)
            Slider(value = options.length.toFloat(), onValueChange = { onChange(options.copy(length = it.toInt())) }, valueRange = 6f..50f, steps = 43)
            GeneratorSwitchRow(stringResource(R.string.include_uppercase), options.uppercase) { onChange(options.copy(uppercase = it)) }
            HorizontalDivider()
            GeneratorSwitchRow(stringResource(R.string.include_lowercase), options.lowercase) { onChange(options.copy(lowercase = it)) }
            HorizontalDivider()
            GeneratorSwitchRow(stringResource(R.string.include_numbers), options.digits) { onChange(options.copy(digits = it)) }
            HorizontalDivider()
            GeneratorSwitchRow(stringResource(R.string.include_symbols), options.symbols) { onChange(options.copy(symbols = it)) }
            HorizontalDivider()
            GeneratorSwitchRow(stringResource(R.string.avoid_confusing), options.avoidConfusing) { onChange(options.copy(avoidConfusing = it)) }
        }
    }
}

@Composable
internal fun GeneratorSwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}

@Composable
private fun GeneratorStepCard(
    step: GeneratorStep, index: Int, isLast: Boolean, dragging: Boolean, modifier: Modifier,
    dragHandle: @Composable () -> Unit,
    onEdit: () -> Unit, onDuplicate: () -> Unit, onRemove: () -> Unit,
    onMoveUp: () -> Unit, onMoveDown: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = if (dragging) 6.dp else 0.dp,
    ) {
        GeneratorRuleCardContent(
            name = step.name,
            segment = step.segment,
            leading = {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(Modifier.sizeIn(minWidth = 32.dp, minHeight = 32.dp).padding(horizontal = AppSpacing.xs), contentAlignment = Alignment.Center) {
                        Text((index + 1).toString(), style = MaterialTheme.typography.labelLarge)
                    }
                }
            },
            trailing = {
                dragHandle()
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.edit)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.generator_duplicate)) }, onClick = { menu = false; onDuplicate() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.move_up)) }, enabled = index > 0, onClick = { menu = false; onMoveUp() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.move_down)) }, enabled = !isLast, onClick = { menu = false; onMoveDown() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.generator_remove)) }, onClick = { menu = false; onRemove() })
                    }
                }
            },
        )
    }
}
