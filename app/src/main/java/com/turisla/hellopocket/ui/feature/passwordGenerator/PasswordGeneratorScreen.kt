package com.turisla.hellopocket.ui.feature.passwordGenerator

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.turisla.hellopocket.R
import com.turisla.hellopocket.utils.PasswordGenerator
import com.turisla.hellopocket.utils.ClipboardManagerHelper
import com.turisla.hellopocket.utils.ext.toast
import org.koin.compose.koinInject

/**
 * 为密码文本添加颜色区分
 */
@Composable
fun createColoredPassword(password: String): AnnotatedString {
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
fun PasswordGeneratorScreen(
    onNavigateBack: () -> Unit,
    onUsePassword: (String) -> Unit = {}
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

    // Generate initial password
    LaunchedEffect(passwordLength, includeUppercase, includeLowercase, includeNumbers, includeSymbols, avoidConfusing) {
        if (includeUppercase || includeLowercase || includeNumbers || includeSymbols) {
            generatedPassword = PasswordGenerator.generatePassword(
                length = passwordLength,
                includeUppercase = includeUppercase,
                includeLowercase = includeLowercase,
                includeNumbers = includeNumbers,
                includeSymbols = includeSymbols,
                avoidConfusing = avoidConfusing
            )
        } else {
            context.toast(R.string.password_generator_condition_failed)
        }
    }
    
    val regeneratePassword = {
        if (includeUppercase || includeLowercase || includeNumbers || includeSymbols) {
            generatedPassword = PasswordGenerator.generatePassword(
                length = passwordLength,
                includeUppercase = includeUppercase,
                includeLowercase = includeLowercase,
                includeNumbers = includeNumbers,
                includeSymbols = includeSymbols,
                avoidConfusing = avoidConfusing
            )
        } else {
            context.toast(R.string.password_generator_condition_failed)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.password_generator)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Password length slider
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.password_length),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = passwordLength.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = passwordLength.toFloat(),
                        onValueChange = { passwordLength = it.toInt() },
                        valueRange = 6f..50f,
                        steps = 43,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                    )
                }
            }
            
            // Character type options
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.include_uppercase))
                        Switch(
                            checked = includeUppercase,
                            onCheckedChange = { includeUppercase = it }
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.include_lowercase))
                        Switch(
                            checked = includeLowercase,
                            onCheckedChange = { includeLowercase = it }
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.include_numbers))
                        Switch(
                            checked = includeNumbers,
                            onCheckedChange = { includeNumbers = it }
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.include_symbols))
                        Switch(
                            checked = includeSymbols,
                            onCheckedChange = { includeSymbols = it }
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.avoid_confusing))
                        Switch(
                            checked = avoidConfusing,
                            onCheckedChange = { avoidConfusing = it }
                        )
                    }
                }
            }
            
            // Generated password display
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.generated_password),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 带颜色的密码显示
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(
                            1.dp, 
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SelectionContainer {
                                Text(
                                    text = createColoredPassword(generatedPassword),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row {
                                IconButton(
                                    onClick = {
                                        clipboardManager.copyTextToClipboard(R.string.password, generatedPassword)
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy))
                                }
                            }
                        }
                    }
                }
            }
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = regeneratePassword,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.refresh_password))
                }

                Button(
                    onClick = {
                        onUsePassword(generatedPassword)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = generatedPassword.isNotEmpty()
                ) {
                    Text(stringResource(R.string.use_password))
                }
            }
        }
    }
}
