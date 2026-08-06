package com.turisla.hellopocket.ui.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.turisla.hellopocket.R
import com.turisla.hellopocket.ui.feature.common.AppLogo
import com.turisla.hellopocket.ui.theme.AppSpacing
import kotlinx.coroutines.flow.StateFlow

@Composable
fun UnlockScreen(
    onUnlockAttempt: (String) -> Unit,
    onBiometricUnlockRequest: () -> Unit,
    showInvalidPasswordError: Boolean,
    vaultErrorMessage: String? = null,
    isBiometricUnlockEnabled: StateFlow<Boolean>,
    passwordHint: StateFlow<String?>? = null,
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var hasEditedSinceError by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val isBiometricEnabled by isBiometricUnlockEnabled.collectAsStateWithLifecycle()
    val hint by (passwordHint?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) })

    LaunchedEffect(showInvalidPasswordError) {
        if (showInvalidPasswordError) hasEditedSinceError = false
    }
    LaunchedEffect(isBiometricEnabled) {
        if (isBiometricEnabled) onBiometricUnlockRequest()
    }

    val shouldShowError = showInvalidPasswordError && !hasEditedSinceError

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
                text = stringResource(R.string.welcome_back),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(AppSpacing.xxl))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    hasEditedSinceError = true
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
                isError = shouldShowError,
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

            if (shouldShowError) {
                Spacer(modifier = Modifier.height(AppSpacing.xs))
                Text(
                    text = stringResource(R.string.invalid_password),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            vaultErrorMessage?.let {
                Spacer(modifier = Modifier.height(AppSpacing.xs))
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            hint?.let { currentHint ->
                Spacer(modifier = Modifier.height(AppSpacing.xs))
                if (showHint) {
                    Text(
                        text = stringResource(R.string.hint_prefix, currentHint),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    TextButton(
                        onClick = { showHint = true },
                        modifier = Modifier.align(Alignment.Start),
                    ) {
                        Text(stringResource(R.string.show_hint))
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppSpacing.xl))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        keyboardController?.hide()
                        onUnlockAttempt(password)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp),
                    enabled = password.isNotEmpty(),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(stringResource(R.string.unlock))
                }
                if (isBiometricEnabled) {
                    Spacer(modifier = Modifier.width(AppSpacing.sm))
                    FilledTonalIconButton(
                        onClick = onBiometricUnlockRequest,
                        modifier = Modifier.size(56.dp),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(
                            Icons.Default.Fingerprint,
                            contentDescription = stringResource(R.string.fingerprint_unlock_title),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        }
    }
}
