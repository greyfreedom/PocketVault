package com.turisla.hellopocket.ui.feature.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.turisla.hellopocket.R
import com.turisla.hellopocket.ui.feature.common.LoadingOverlay
import com.turisla.hellopocket.ui.theme.AppSpacing
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(
    onNavigateBack: () -> Unit, viewModel: ChangePasswordViewModel = koinViewModel()) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLoading = uiState is ChangePasswordUiState.Loading
    // 密码显示/隐藏状态
    var currentPasswordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    // 修改主密码会原子更新保险库配置，处理中禁止返回以免取消页面作用域任务。
    BackHandler(enabled = isLoading) {}

    LaunchedEffect(uiState) {
            when (val state = uiState) {
                is ChangePasswordUiState.Success -> {
                    val message = if (state.biometricsWereDisabled) {
                        context.getString(R.string.password_change_success_biometric_disabled)
                    } else {
                        context.getString(R.string.password_change_success)
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    viewModel.consumeResult()
                    onNavigateBack()
                }

                is ChangePasswordUiState.Error -> {
                    Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                    viewModel.consumeResult()
                }

                else -> {}
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(
                    stringResource(R.string.change_master_password),
                    style = MaterialTheme.typography.titleLarge,
                )
            }, navigationIcon = {
                IconButton(onClick = onNavigateBack, enabled = !isLoading) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }, colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ))
        }, containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OutlinedTextField(
                    value = formState.currentPassword,
                    onValueChange = viewModel::updateCurrentPassword,
                    label = { Text(stringResource(R.string.current_master_password)) },
                    visualTransformation = if (currentPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    trailingIcon = {
                        IconButton(onClick = { currentPasswordVisible = !currentPasswordVisible }) {
                            Icon(
                                imageVector = if (currentPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = stringResource(
                                    if (currentPasswordVisible) R.string.hide_password else R.string.show_password
                                )
                            )
                        }
                    }
                )
                Spacer(modifier = Modifier.height(AppSpacing.md))
                OutlinedTextField(
                    value = formState.newPassword,
                    onValueChange = viewModel::updateNewPassword,
                    label = { Text(stringResource(R.string.new_password)) },
                    visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    trailingIcon = {
                        IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                            Icon(
                                imageVector = if (newPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = stringResource(
                                    if (newPasswordVisible) R.string.hide_password else R.string.show_password
                                )
                            )
                        }
                    }
                )
                Spacer(modifier = Modifier.height(AppSpacing.md))
                OutlinedTextField(
                    value = formState.confirmPassword,
                    onValueChange = viewModel::updateConfirmPassword,
                    label = { Text(stringResource(R.string.confirm_new_password)) },
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    trailingIcon = {
                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                            Icon(
                                imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = stringResource(
                                    if (confirmPasswordVisible) R.string.hide_password else R.string.show_password
                                )
                            )
                        }
                    }
                )
                Spacer(modifier = Modifier.height(AppSpacing.xxl))
                Button(
                    onClick = {
                        keyboardController?.hide()
                        viewModel.attemptChangePassword(context)
                    }, modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    enabled = !isLoading,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(stringResource(R.string.confirm_change))
                }
                Spacer(modifier = Modifier.height(AppSpacing.xxl))
            }
            if (isLoading) {
                LoadingOverlay()
            }
        }
    }
}
