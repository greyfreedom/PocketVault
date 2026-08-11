package com.turisla.hellopocket.ui.feature.totp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.turisla.hellopocket.R
import com.turisla.hellopocket.ui.feature.common.EntryFormCard
import com.turisla.hellopocket.ui.feature.common.EntryFormDivider
import com.turisla.hellopocket.ui.feature.common.EntryFormSectionHeader
import com.turisla.hellopocket.ui.feature.common.EntryFormTextField
import com.turisla.hellopocket.ui.feature.common.ResetSensitiveStateOnBackground
import org.koin.androidx.compose.koinViewModel

/**
 * 添加与编辑 TOTP 共用的表单页面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualTotpScreen(
    onNavigateBack: () -> Unit,
    entryId: String? = null,
    viewModel: ManualTotpViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isEditing = entryId != null
    val formEnabled = state.isInitialized && !state.isSaving && !state.entryMissing
    val algorithmLabel = when (state.algorithm) {
        "SHA1" -> "SHA-1"
        "SHA256" -> "SHA-256"
        "SHA512" -> "SHA-512"
        else -> state.algorithm
    }
    var showAdvancedOptions by rememberSaveable { mutableStateOf(false) }
    var secretVisible by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    ResetSensitiveStateOnBackground { secretVisible = false }

    LaunchedEffect(entryId) {
        viewModel.initialize(entryId)
    }

    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            viewModel.consumeSaveSuccess()
            onNavigateBack()
        }
    }

    // 写入加密保险库期间阻止退出，避免用户误以为保存已经取消。
    BackHandler(enabled = state.isSaving) {}

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (isEditing) {
                                R.string.totp_edit_entry_title
                            } else {
                                R.string.totp_manual_add_title
                            },
                        ),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        enabled = !state.isSaving,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
                .pointerInput(focusManager) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                },
        ) {
            EntryFormSectionHeader(title = stringResource(R.string.login_info))
            EntryFormCard {
                Text(
                    text = stringResource(
                        if (isEditing) {
                            R.string.totp_edit_entry_description
                        } else {
                            R.string.totp_manual_add_description
                        },
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                EntryFormDivider()
                EntryFormTextField(
                    label = stringResource(R.string.totp_issuer),
                    value = state.issuer,
                    onValueChange = viewModel::updateIssuer,
                    placeholder = stringResource(R.string.totp_issuer_hint),
                    isError = state.issuerError != null,
                    supportingText = state.issuerError?.let { stringResource(it) },
                    enabled = formEnabled,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                EntryFormDivider()
                EntryFormTextField(
                    label = stringResource(R.string.totp_account),
                    value = state.account,
                    onValueChange = viewModel::updateAccount,
                    placeholder = stringResource(R.string.totp_account_hint),
                    isError = state.accountError != null,
                    supportingText = state.accountError?.let { stringResource(it) },
                    enabled = formEnabled,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                EntryFormDivider()
                EntryFormTextField(
                    label = stringResource(R.string.totp_secret),
                    value = state.secret,
                    onValueChange = viewModel::updateSecret,
                    placeholder = stringResource(R.string.totp_secret_hint),
                    isError = state.secretError != null,
                    supportingText = stringResource(
                        state.secretError ?: R.string.totp_secret_supporting_text,
                    ),
                    visualTransformation = if (secretVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingContent = {
                        IconButton(
                            onClick = { secretVisible = !secretVisible },
                            enabled = formEnabled,
                        ) {
                            Icon(
                                imageVector = if (secretVisible) {
                                    Icons.Outlined.Visibility
                                } else {
                                    Icons.Outlined.VisibilityOff
                                },
                                contentDescription = stringResource(
                                    if (secretVisible) {
                                        R.string.hide_password
                                    } else {
                                        R.string.show_password
                                    },
                                ),
                            )
                        }
                    },
                    enabled = formEnabled,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    ),
                )
            }

            EntryFormSectionHeader(title = stringResource(R.string.totp_advanced_options))
            EntryFormCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = formEnabled) {
                            focusManager.clearFocus()
                            showAdvancedOptions = !showAdvancedOptions
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    ) {
                    Text(
                        text = "$algorithmLabel · ${state.digits} · ${state.period}s",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Icon(
                        imageVector = if (showAdvancedOptions) {
                            Icons.Default.KeyboardArrowUp
                        } else {
                            Icons.Default.KeyboardArrowDown
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                if (showAdvancedOptions) {
                    EntryFormDivider()
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.totp_algorithm),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AlgorithmOption(
                                label = "SHA-1",
                                selected = state.algorithm == "SHA1",
                                onClick = { viewModel.updateAlgorithm("SHA1") },
                                modifier = Modifier.weight(1f),
                                enabled = formEnabled,
                            )
                            AlgorithmOption(
                                label = "SHA-256",
                                selected = state.algorithm == "SHA256",
                                onClick = { viewModel.updateAlgorithm("SHA256") },
                                modifier = Modifier.weight(1f),
                                enabled = formEnabled,
                            )
                            AlgorithmOption(
                                label = "SHA-512",
                                selected = state.algorithm == "SHA512",
                                onClick = { viewModel.updateAlgorithm("SHA512") },
                                modifier = Modifier.weight(1f),
                                enabled = formEnabled,
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.totp_digits),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AlgorithmOption(
                                label = "6",
                                selected = state.digits == 6,
                                onClick = { viewModel.updateDigits(6) },
                                modifier = Modifier.weight(1f),
                                enabled = formEnabled,
                            )
                            AlgorithmOption(
                                label = "8",
                                selected = state.digits == 8,
                                onClick = { viewModel.updateDigits(8) },
                                modifier = Modifier.weight(1f),
                                enabled = formEnabled,
                            )
                        }
                    }
                    EntryFormDivider()
                    EntryFormTextField(
                        label = stringResource(R.string.totp_period_seconds),
                        value = state.period,
                        onValueChange = viewModel::updatePeriod,
                        isError = state.periodError != null,
                        supportingText = stringResource(
                            state.periodError ?: R.string.totp_period_supporting_text,
                        ),
                        enabled = formEnabled,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                    )
                }
            }

            state.saveError?.let { error ->
                Text(
                    text = stringResource(error),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.weight(1f),
                    enabled = !state.isSaving,
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = viewModel::save,
                    modifier = Modifier.weight(1f),
                    enabled = formEnabled,
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}

@Composable
private fun AlgorithmOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
    )
}
