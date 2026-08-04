package com.turisla.hellopocket.ui.feature.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.turisla.hellopocket.R
import com.turisla.hellopocket.ui.feature.common.AppLogo
import kotlinx.coroutines.flow.StateFlow

@Composable
fun UnlockScreen(
    onUnlockAttempt: (String) -> Unit,
    onBiometricUnlockRequest: () -> Unit,
    showInvalidPasswordError: Boolean,
    vaultErrorMessage: String? = null,
    isBiometricUnlockEnabled: StateFlow<Boolean>,
    passwordHint: StateFlow<String?>? = null
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    // 追踪用户是否在错误后修改了输入
    var hasEditedSinceError by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val isBiometricEnabled by isBiometricUnlockEnabled.collectAsStateWithLifecycle()
    val hint by (passwordHint?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) })
    var showHint by remember { mutableStateOf(false) }
    
    // 当从父组件接收到新的错误状态时，重置编辑标记
    LaunchedEffect(showInvalidPasswordError) {
        if (showInvalidPasswordError) {
            hasEditedSinceError = false
        }
    }
    
    // 实际显示错误：只有当父组件报告错误且用户未修改输入时才显示
    val shouldShowError = showInvalidPasswordError && !hasEditedSinceError

    LaunchedEffect(isBiometricEnabled) {
        if (isBiometricEnabled) {
            onBiometricUnlockRequest.invoke()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppLogo()
            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.welcome_back), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Column {
            OutlinedTextField(
                value = password,
                onValueChange = { 
                    password = it
                    // 用户修改输入时，标记已编辑，清除错误显示
                    hasEditedSinceError = true
                },
                label = { Text(stringResource(R.string.master_password)) },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false
                ),
                singleLine = true,
                isError = shouldShowError,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = stringResource(
                                if (passwordVisible) R.string.hide_password else R.string.show_password
                            )
                        )
                    }
                }
            )

            if (shouldShowError) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.invalid_password), color = MaterialTheme.colorScheme.error)
            }

            if (vaultErrorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = vaultErrorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // 显示密码提示（如果存在）
            if (hint != null) {
                Spacer(modifier = Modifier.height(8.dp))
                if (showHint) {
                    Text(
                        stringResource(R.string.hint_prefix, hint!!),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        stringResource(R.string.show_hint, hint!!),
                        Modifier.clickable(onClick = { showHint = true }),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    keyboardController?.hide() // 点击后收起键盘
                    onUnlockAttempt(password)
                }, modifier = Modifier
                    .weight(1f)
                    .height(48.dp), enabled = password.isNotEmpty()
            ) {
                Text(stringResource(R.string.unlock))
            }
            if (isBiometricEnabled) {
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(
                    onClick = {
                        onBiometricUnlockRequest()
                    }, modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = stringResource(R.string.fingerprint_unlock_title), modifier = Modifier.size(32.dp))
                }
            }
        }

        Spacer(Modifier.weight(1.5f))
    }
}
