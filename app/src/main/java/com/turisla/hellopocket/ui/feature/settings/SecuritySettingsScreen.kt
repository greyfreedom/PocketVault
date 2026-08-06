package com.turisla.hellopocket.ui.feature.settings

import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import com.turisla.hellopocket.R
import com.turisla.hellopocket.router.RouteChangePassword
import com.turisla.hellopocket.utils.AppUtils
import org.koin.compose.viewmodel.koinViewModel
import javax.crypto.Cipher

/**
 * 安全设置页面
 * 包含指纹解锁、修改主密码、主密码提示
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    navController: NavController,
    viewModel: SettingsPageViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val isBiometricEnabled by viewModel.isBiometricUnlockEnabled.collectAsStateWithLifecycle()
    val passwordHint by viewModel.passwordHint.collectAsStateWithLifecycle()
    val event by viewModel.event.collectAsStateWithLifecycle()

    var showChangePasswordWarning by remember { mutableStateOf(false) }
    var showEnrollBiometricDialog by remember { mutableStateOf(false) }
    var showPasswordHintDialog by remember { mutableStateOf(false) }

    // 修改密码警告对话框
    if (showChangePasswordWarning) {
        ChangePasswordWarningDialog(isBiometricEnabled = isBiometricEnabled, onDismiss = { showChangePasswordWarning = false }, onConfirm = {
            showChangePasswordWarning = false
            navController.navigate(RouteChangePassword)
        })
    }

    // 注册生物识别对话框
    if (showEnrollBiometricDialog) {
        EnrollBiometricDialog(onDismiss = { showEnrollBiometricDialog = false }, onConfirm = {
            showEnrollBiometricDialog = false
            AppUtils.goBiometricOrSecurity(context)
        })
    }

    // 密码提示对话框
    if (showPasswordHintDialog) {
        PasswordHintDialog(currentHint = passwordHint ?: "", onDismiss = { showPasswordHintDialog = false }, onConfirm = { newHint ->
            viewModel.updatePasswordHint(newHint)
            showPasswordHintDialog = false
        })
    }

    // 处理事件
    LaunchedEffect(event) {
        event?.let { currentEvent ->
            viewModel.consumeEvent(currentEvent)
            when (currentEvent) {
                is SettingsPageViewModel.Event.RequestBiometricEncryption -> {
                    activity?.showBiometricPromptForEncryption(currentEvent.cipher) {
                        viewModel.onBiometricEncryptionSucceeded(it)
                    }
                }

                is SettingsPageViewModel.Event.BiometricSetupSuccess -> Toast.makeText(context, R.string.biometric_setup_success, Toast.LENGTH_SHORT).show()
                is SettingsPageViewModel.Event.BiometricSetupDisabled -> Toast.makeText(context, R.string.biometric_setup_disabled, Toast.LENGTH_SHORT).show()
                is SettingsPageViewModel.Event.ShowEnrollBiometricDialog -> showEnrollBiometricDialog = true
                is SettingsPageViewModel.Event.ShowBiometricError -> Toast.makeText(context, currentEvent.message, Toast.LENGTH_SHORT).show()
                is SettingsPageViewModel.Event.PasswordHintUpdated -> Toast.makeText(context, R.string.password_hint_updated, Toast.LENGTH_SHORT).show()
                else -> { /* 其他事件在这里不处理 */
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(
                    stringResource(R.string.settings_security),
                    style = MaterialTheme.typography.titleLarge,
                )
            }, navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }, colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ))
        }, containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item { SettingsDivider() }
            item {
                SettingsGroup {
                    SettingsListItem(
                        title = stringResource(R.string.enable_fingerprint_unlock),
                        icon = Icons.Outlined.Fingerprint,
                        trailingContent = {
                            Switch(
                                checked = isBiometricEnabled,
                                onCheckedChange = viewModel::onBiometricUnlockToggled,
                            )
                        },
                        inGroup = true,
                    )
                    SettingsGroupDivider()
                    SettingsListItem(
                        title = stringResource(R.string.change_master_password),
                        icon = Icons.Outlined.Lock,
                        onClick = { showChangePasswordWarning = true },
                        inGroup = true,
                    )
                    SettingsGroupDivider()
                    SettingsListItem(
                        title = stringResource(R.string.master_password_hint),
                        description = passwordHint?.takeIf(String::isNotBlank)
                            ?: stringResource(R.string.password_hint_optional),
                        icon = Icons.AutoMirrored.Outlined.HelpOutline,
                        onClick = { showPasswordHintDialog = true },
                        inGroup = true,
                    )
                }
            }
            item { SettingsDivider() }
        }
    }
}

@Composable
private fun ChangePasswordWarningDialog(
    isBiometricEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val biometricInfo = stringResource(R.string.change_password_biometric_info)
    val additionInfo = if (isBiometricEnabled) biometricInfo else ""
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = { Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.important_notice), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = { Text(stringResource(R.string.change_password_warning, additionInfo), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        confirmButton = { Button(onClick = onConfirm) { Text(stringResource(R.string.continue_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun EnrollBiometricDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.enroll_biometric_dialog_title)) },
        text = { Text(stringResource(R.string.enroll_biometric_dialog_message)) },
        confirmButton = { Button(onClick = onConfirm) { Text(stringResource(R.string.go_to_settings)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun PasswordHintDialog(
    currentHint: String,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var hint by remember(currentHint) { mutableStateOf(currentHint) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.enter_password_hint)) },
        text = {
            OutlinedTextField(
                value = hint,
                onValueChange = { hint = it },
                label = { Text(stringResource(R.string.password_hint)) },
                supportingText = { Text(stringResource(R.string.password_hint_description)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(hint.takeIf(String::isNotBlank)) }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        })
}

private fun FragmentActivity.showBiometricPromptForEncryption(
    cipher: Cipher,
    onSuccess: (Cipher) -> Unit,
) {
    val promptInfo = BiometricPrompt.PromptInfo.Builder().setTitle(getString(R.string.biometric_prompt_title)).setSubtitle(getString(R.string.biometric_prompt_subtitle))
        .setNegativeButtonText(getString(R.string.biometric_prompt_negative)).build()

    val biometricPrompt = BiometricPrompt(
        this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Toast.makeText(this@showBiometricPromptForEncryption, errString, Toast.LENGTH_SHORT).show()
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                result.cryptoObject?.cipher?.let(onSuccess)
            }
        })
    biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
}
