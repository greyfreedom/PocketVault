package com.turisla.hellopocket.ui.feature.passwordGenerator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.turisla.hellopocket.R
import com.turisla.hellopocket.model.GeneratorLetterCase
import com.turisla.hellopocket.model.GeneratorRule
import com.turisla.hellopocket.model.GeneratorSegment
import com.turisla.hellopocket.model.GeneratorTemplate
import com.turisla.hellopocket.ui.theme.AppSpacing
import com.turisla.hellopocket.utils.GeneratorIssue
import com.turisla.hellopocket.utils.RulePasswordGenerator
import java.util.UUID

@Composable
internal fun generatorIssueText(issue: GeneratorIssue): String = when (issue) {
    GeneratorIssue.EMPTY_COMPOSITION -> stringResource(R.string.generator_error_empty)
    GeneratorIssue.EMPTY_TEXT -> stringResource(R.string.generator_error_empty_text)
    GeneratorIssue.INVALID_LENGTH -> stringResource(R.string.generator_error_length, RulePasswordGenerator.MAX_PASSWORD_LENGTH)
    GeneratorIssue.EMPTY_ALPHABET -> stringResource(R.string.generator_error_alphabet)
    GeneratorIssue.INVALID_SYMBOLS -> stringResource(R.string.generator_error_symbols)
    GeneratorIssue.UNSUPPORTED_TEXT -> stringResource(R.string.generator_error_text)
    GeneratorIssue.TOO_LONG -> stringResource(R.string.generator_error_total, RulePasswordGenerator.MAX_PASSWORD_LENGTH)
    GeneratorIssue.NO_RANDOMNESS -> stringResource(R.string.generator_error_random)
}

@Composable
internal fun letterCaseName(value: GeneratorLetterCase): String = stringResource(when (value) {
    GeneratorLetterCase.LOWER -> R.string.generator_letter_lower
    GeneratorLetterCase.UPPER -> R.string.generator_letter_upper
    GeneratorLetterCase.MIXED -> R.string.generator_letter_mixed
})

private enum class RuleKind(val label: Int) {
    TEXT(R.string.generator_type_text), DIGITS(R.string.generator_type_digits),
    LETTERS(R.string.generator_type_letters), SYMBOLS(R.string.generator_type_symbols),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GeneratorRuleEditor(
    initial: GeneratorRule?, libraryOnly: Boolean, busy: Boolean,
    onDismiss: () -> Unit, onSave: (GeneratorRule, Boolean) -> Unit,
) {
    // Dialog 会创建新的窗口并重置平台 CompositionLocal，必须把受保护的剪贴板带入内容。
    val clipboard = LocalClipboard.current
    val original = initial?.segment
    val id = remember(initial?.id) { initial?.id ?: UUID.randomUUID().toString() }
    var name by remember(id) { mutableStateOf(initial?.name.orEmpty()) }
    var kind by remember(id) { mutableStateOf(when (original) {
        is GeneratorSegment.Digits -> RuleKind.DIGITS
        is GeneratorSegment.Letters -> RuleKind.LETTERS
        is GeneratorSegment.Symbols -> RuleKind.SYMBOLS
        else -> RuleKind.TEXT
    }) }
    var text by remember(id) { mutableStateOf((original as? GeneratorSegment.Text)?.value.orEmpty()) }
    var length by remember(id) { mutableStateOf(if (original != null && original !is GeneratorSegment.Text) RulePasswordGenerator.segmentLength(original).toString() else "6") }
    var letterCase by remember(id) { mutableStateOf((original as? GeneratorSegment.Letters)?.letterCase ?: GeneratorLetterCase.MIXED) }
    var avoidConfusing by remember(id) { mutableStateOf(when (original) {
        is GeneratorSegment.Letters -> original.avoidConfusing
        is GeneratorSegment.Digits -> original.avoidConfusing
        else -> false
    }) }
    var symbols by remember(id) { mutableStateOf((original as? GeneratorSegment.Symbols)?.characters ?: RulePasswordGenerator.DEFAULT_SYMBOLS) }
    var saveToLibrary by remember(id) { mutableStateOf(false) }
    val segment = when (kind) {
        RuleKind.TEXT -> GeneratorSegment.Text(text)
        RuleKind.DIGITS -> GeneratorSegment.Digits(length.toIntOrNull() ?: 0, avoidConfusing)
        RuleKind.LETTERS -> GeneratorSegment.Letters(length.toIntOrNull() ?: 0, letterCase, avoidConfusing)
        RuleKind.SYMBOLS -> GeneratorSegment.Symbols(length.toIntOrNull() ?: 0, symbols)
    }
    val issue = RulePasswordGenerator.validateSegment(segment)
    val validName = name.trim().isNotEmpty() && name.length <= RulePasswordGenerator.MAX_NAME_LENGTH
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.generator_new_rule else R.string.generator_edit_rule)) },
        text = {
            CompositionLocalProvider(LocalClipboard provides clipboard) {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text(stringResource(R.string.generator_rule_name)) },
                        singleLine = true, enabled = !busy,
                        isError = name.isNotEmpty() && !validName,
                        supportingText = { if (!validName && name.isNotEmpty()) Text(stringResource(R.string.generator_error_name, RulePasswordGenerator.MAX_NAME_LENGTH)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.generator_rule_type), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        RuleKind.entries.forEach { choice ->
                            FilterChip(selected = kind == choice, enabled = !busy, onClick = { kind = choice }, label = { Text(stringResource(choice.label)) })
                        }
                    }
                    if (kind == RuleKind.TEXT) {
                        OutlinedTextField(
                            value = text, onValueChange = { text = it },
                            label = { Text(stringResource(R.string.generator_fixed_text)) },
                            singleLine = true, enabled = !busy,
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(stringResource(R.string.generator_fixed_hint), style = MaterialTheme.typography.bodySmall)
                    } else {
                        OutlinedTextField(
                            value = length,
                            onValueChange = { value -> length = value.mapNotNull { it.digitToIntOrNull() }.joinToString("") },
                            label = { Text(stringResource(R.string.generator_segment_length)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (kind == RuleKind.LETTERS) {
                        Text(stringResource(R.string.generator_letter_case), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                            GeneratorLetterCase.entries.forEach { choice ->
                                FilterChip(selected = letterCase == choice, enabled = !busy, onClick = { letterCase = choice }, label = { Text(letterCaseName(choice)) })
                            }
                        }
                        if (letterCase == GeneratorLetterCase.MIXED) Text(stringResource(R.string.generator_mixed_hint), style = MaterialTheme.typography.bodySmall)
                    }
                    if (kind == RuleKind.DIGITS || kind == RuleKind.LETTERS) {
                        GeneratorSwitchRow(stringResource(R.string.avoid_confusing), avoidConfusing, !busy) { avoidConfusing = it }
                    }
                    if (kind == RuleKind.SYMBOLS) OutlinedTextField(
                        value = symbols, onValueChange = { symbols = it },
                        label = { Text(stringResource(R.string.generator_symbol_set)) },
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                        enabled = !busy, modifier = Modifier.fillMaxWidth(),
                    )
                    if (issue != null) Text(generatorIssueText(issue), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    if (!libraryOnly) GeneratorSwitchRow(stringResource(R.string.generator_save_to_library), saveToLibrary, !busy) { saveToLibrary = it }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && validName && issue == null, onClick = { onSave(GeneratorRule(id, name.trim(), segment), saveToLibrary) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GeneratorRulePicker(
    rules: List<GeneratorRule>, busy: Boolean,
    onDismiss: () -> Unit, onAdd: (List<GeneratorRule>) -> Unit, onNew: () -> Unit,
    onEdit: (GeneratorRule) -> Unit, onDuplicate: (GeneratorRule) -> Unit, onDelete: (GeneratorRule) -> Unit,
) {
    var selected by remember { mutableStateOf(emptyList<String>()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).navigationBarsPadding().padding(bottom = AppSpacing.md)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.generator_rules_library), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onNew, enabled = !busy) { Text(stringResource(R.string.generator_new_rule)) }
            }
            if (rules.isEmpty()) Text(stringResource(R.string.generator_empty_library), Modifier.padding(AppSpacing.md))
            LazyColumn(
                Modifier.weight(1f, fill = false),
                contentPadding = PaddingValues(AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                items(rules, key = { it.id }) { rule ->
                    val checked = rule.id in selected
                    val toggle = { selected = if (checked) selected - rule.id else selected + rule.id }
                    Surface(
                        modifier = Modifier.fillMaxWidth().toggleable(value = checked, enabled = !busy, role = Role.Checkbox, onValueChange = { toggle() }),
                        shape = MaterialTheme.shapes.large,
                        color = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        GeneratorRuleCardContent(
                            name = rule.name, segment = rule.segment,
                            leading = { Checkbox(checked = checked, onCheckedChange = null, enabled = !busy) },
                            trailing = { RuleLibraryMenu(busy, { onEdit(rule) }, { onDuplicate(rule) }, { onDelete(rule) }) },
                        )
                    }
                }
            }
            Button(
                onClick = { onAdd(selected.mapNotNull { id -> rules.find { it.id == id } }) },
                enabled = !busy && selected.any { id -> rules.any { it.id == id } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md),
            ) { Text(stringResource(R.string.generator_add_selected)) }
        }
    }
}

@Composable
private fun RuleLibraryMenu(busy: Boolean, onEdit: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = !busy) { Icon(Icons.Default.MoreVert, stringResource(R.string.edit)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { expanded = false; onEdit() })
            DropdownMenuItem(text = { Text(stringResource(R.string.generator_duplicate)) }, onClick = { expanded = false; onDuplicate() })
            DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { expanded = false; onDelete() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GeneratorTemplatePicker(
    templates: List<GeneratorTemplate>, busy: Boolean, onDismiss: () -> Unit,
    onSelect: (GeneratorTemplate) -> Unit, onDelete: (GeneratorTemplate) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).navigationBarsPadding().padding(bottom = AppSpacing.md)) {
            Text(stringResource(R.string.generator_templates), Modifier.padding(AppSpacing.md), style = MaterialTheme.typography.titleLarge)
            if (templates.isEmpty()) Text(stringResource(R.string.generator_empty_templates), Modifier.padding(AppSpacing.md))
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(templates, key = { it.id }) { template ->
                    var expanded by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text(template.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(stringResource(R.string.generator_composition_count, template.steps.size, RulePasswordGenerator.analyze(template.steps).length)) },
                        modifier = Modifier.clickable(enabled = !busy) { onSelect(template) },
                        trailingContent = {
                            Box {
                                IconButton(onClick = { expanded = true }, enabled = !busy) { Icon(Icons.Default.MoreVert, stringResource(R.string.edit)) }
                                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { expanded = false; onDelete(template) })
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun GeneratorTemplateNameDialog(
    initialName: String, canReplace: Boolean, busy: Boolean,
    onDismiss: () -> Unit, onSave: (String, Boolean) -> Unit,
) {
    val clipboard = LocalClipboard.current
    var name by remember { mutableStateOf(initialName) }
    val valid = name.isNotBlank() && name.length <= RulePasswordGenerator.MAX_NAME_LENGTH
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.generator_save_template)) },
        text = {
            CompositionLocalProvider(LocalClipboard provides clipboard) {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text(stringResource(R.string.generator_template_name)) },
                        singleLine = true, enabled = !busy,
                        isError = name.isNotEmpty() && !valid,
                        supportingText = { if (!valid && name.isNotEmpty()) Text(stringResource(R.string.generator_error_name, RulePasswordGenerator.MAX_NAME_LENGTH)) },
                    )
                    Text(stringResource(R.string.generator_snapshot_hint), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (canReplace) TextButton(enabled = valid && !busy, onClick = { onSave(name, true) }) { Text(stringResource(R.string.generator_update_template)) }
                TextButton(enabled = valid && !busy, onClick = { onSave(name, false) }) { Text(stringResource(R.string.generator_save_as_new)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
    )
}
