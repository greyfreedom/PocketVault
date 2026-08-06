package com.turisla.hellopocket.ui.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.turisla.hellopocket.R
import com.turisla.hellopocket.ui.feature.common.AppLogo
import com.turisla.hellopocket.ui.theme.AppSpacing
import com.turisla.hellopocket.utils.AppConstants.MIN_MASTER_PASSWORD_LENGTH

/** 首次启动表单限制最大宽度，兼顾小屏滚动与平板可读性。 */
@Composable
fun SetupScreen(
    onSetupComplete: (String, String?) -> Unit,
    onImportRequest: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordHint by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    val passwordMismatchMessage = stringResource(R.string.password_mismatch_error)
    val passwordTooShortMessage = pluralStringResource(
        R.plurals.master_password_too_short,
        MIN_MASTER_PASSWORD_LENGTH,
        MIN_MASTER_PASSWORD_LENGTH,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppLogo(size = 88.dp)
            Spacer(modifier = Modifier.height(AppSpacing.xl))
            Text(
                text = stringResource(R.string.setup_password_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(AppSpacing.xs))
            Text(
                text = stringResource(R.string.setup_password_description),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(AppSpacing.xxl))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    error = null
                },
                label = { Text(stringResource(R.string.master_password)) },
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Default.Visibility
                            } else {
                                Icons.Default.VisibilityOff
                            },
                            contentDescription = stringResource(
                                if (passwordVisible) R.string.hide_password else R.string.show_password,
                            ),
                        )
                    }
                },
            )
            Spacer(modifier = Modifier.height(AppSpacing.md))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    error = null
                },
                label = { Text(stringResource(R.string.confirm_password)) },
                visualTransformation = if (confirmPasswordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                ),
                singleLine = true,
                isError = error != null,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                trailingIcon = {
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(
                            imageVector = if (confirmPasswordVisible) {
                                Icons.Default.Visibility
                            } else {
                                Icons.Default.VisibilityOff
                            },
                            contentDescription = stringResource(
                                if (confirmPasswordVisible) R.string.hide_password else R.string.show_password,
                            ),
                        )
                    }
                },
            )
            Spacer(modifier = Modifier.height(AppSpacing.md))

            OutlinedTextField(
                value = passwordHint,
                onValueChange = { passwordHint = it },
                label = { Text(stringResource(R.string.password_hint_optional)) },
                supportingText = {
                    Text(stringResource(R.string.password_hint_description))
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )

            error?.let {
                Spacer(modifier = Modifier.height(AppSpacing.xs))
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(AppSpacing.xl))
            Button(
                onClick = {
                    if (password.length < MIN_MASTER_PASSWORD_LENGTH) {
                        error = passwordTooShortMessage
                        return@Button
                    }
                    if (password != confirmPassword) {
                        error = passwordMismatchMessage
                        return@Button
                    }
                    onSetupComplete(password, passwordHint.ifBlank { null })
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(stringResource(R.string.create_vault))
            }
            TextButton(
                onClick = onImportRequest,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.import_from_file))
            }
        }
    }
}
