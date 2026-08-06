package com.turisla.hellopocket.ui.feature.passwordGenerator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.turisla.hellopocket.R
import com.turisla.hellopocket.ui.feature.common.AppSectionSurface
import com.turisla.hellopocket.ui.theme.AppSpacing
import com.turisla.hellopocket.utils.ClipboardManagerHelper
import com.turisla.hellopocket.utils.PasswordGenerator
import com.turisla.hellopocket.utils.ext.toast
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordGeneratorScreen(
    onNavigateBack: () -> Unit,
    onUsePassword: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val clipboardManager: ClipboardManagerHelper = koinInject()

    var passwordLength by remember { mutableIntStateOf(12) }
    var includeUppercase by remember { mutableStateOf(true) }
    var includeLowercase by remember { mutableStateOf(true) }
    var includeNumbers by remember { mutableStateOf(true) }
    var includeSymbols by remember { mutableStateOf(false) }
    var avoidConfusing by remember { mutableStateOf(false) }
    var generatedPassword by remember { mutableStateOf("") }

    val regeneratePassword = {
        if (includeUppercase || includeLowercase || includeNumbers || includeSymbols) {
            generatedPassword = PasswordGenerator.generatePassword(
                length = passwordLength,
                includeUppercase = includeUppercase,
                includeLowercase = includeLowercase,
                includeNumbers = includeNumbers,
                includeSymbols = includeSymbols,
                avoidConfusing = avoidConfusing,
            )
        } else {
            generatedPassword = ""
            context.toast(R.string.password_generator_condition_failed)
        }
    }

    LaunchedEffect(
        passwordLength,
        includeUppercase,
        includeLowercase,
        includeNumbers,
        includeSymbols,
        avoidConfusing,
    ) {
        regeneratePassword()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.password_generator),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            GeneratorResultPanel(
                password = generatedPassword,
                onRefresh = regeneratePassword,
                onCopy = {
                    clipboardManager.copyTextToClipboard(
                        R.string.password,
                        generatedPassword,
                    )
                },
            )

            AppSectionSurface {
                Column(modifier = Modifier.padding(AppSpacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.password_length),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = passwordLength.toString(),
                                modifier = Modifier.padding(
                                    horizontal = AppSpacing.sm,
                                    vertical = AppSpacing.xxs,
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                    Slider(
                        value = passwordLength.toFloat(),
                        onValueChange = { passwordLength = it.toInt() },
                        valueRange = 6f..50f,
                        steps = 43,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                }
            }

            AppSectionSurface {
                GeneratorSwitchRow(
                    label = stringResource(R.string.include_uppercase),
                    checked = includeUppercase,
                    onCheckedChange = { includeUppercase = it },
                )
                GeneratorDivider()
                GeneratorSwitchRow(
                    label = stringResource(R.string.include_lowercase),
                    checked = includeLowercase,
                    onCheckedChange = { includeLowercase = it },
                )
                GeneratorDivider()
                GeneratorSwitchRow(
                    label = stringResource(R.string.include_numbers),
                    checked = includeNumbers,
                    onCheckedChange = { includeNumbers = it },
                )
                GeneratorDivider()
                GeneratorSwitchRow(
                    label = stringResource(R.string.include_symbols),
                    checked = includeSymbols,
                    onCheckedChange = { includeSymbols = it },
                )
                GeneratorDivider()
                GeneratorSwitchRow(
                    label = stringResource(R.string.avoid_confusing),
                    checked = avoidConfusing,
                    onCheckedChange = { avoidConfusing = it },
                )
            }

            Button(
                onClick = { onUsePassword(generatedPassword) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                enabled = generatedPassword.isNotEmpty(),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(stringResource(R.string.use_password))
            }

            Spacer(modifier = Modifier.width(AppSpacing.xs))
        }
    }
}

@Composable
private fun GeneratorResultPanel(
    password: String,
    onRefresh: () -> Unit,
    onCopy: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(modifier = Modifier.padding(AppSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.generated_password),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh_password),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                IconButton(onClick = onCopy, enabled = password.isNotEmpty()) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.copy),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            SelectionContainer {
                Text(
                    text = password,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AppSpacing.sm),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun GeneratorSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun GeneratorDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = AppSpacing.md),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
