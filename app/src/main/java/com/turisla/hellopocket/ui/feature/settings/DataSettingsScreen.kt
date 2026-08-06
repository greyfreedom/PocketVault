package com.turisla.hellopocket.ui.feature.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.navigation.NavController
import com.turisla.hellopocket.R
import com.turisla.hellopocket.ui.feature.common.LoadingOverlay
import com.turisla.hellopocket.ui.feature.common.ImportMasterPasswordDialog
import com.turisla.hellopocket.utils.AppUtils
import org.koin.compose.viewmodel.koinViewModel

/**
 * 数据设置页面
 * 包含导出、导入、分享数据
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSettingsScreen(
    navController: NavController,
    viewModel: SettingsPageViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isBiometricEnabled by viewModel.isBiometricUnlockEnabled.collectAsStateWithLifecycle()
    val event by viewModel.event.collectAsStateWithLifecycle()

    var showImportDataWarning by remember { mutableStateOf(false) }
    var showShareSecurityInfo by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // 导入、导出和分享文件都依赖页面作用域，处理中禁止返回取消任务。
    BackHandler(enabled = isLoading) {}

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"), onResult = { uri -> uri?.let { viewModel.exportData(it) } })

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(), onResult = { uri -> pendingImportUri = uri })

    pendingImportUri?.let { uri ->
        ImportMasterPasswordDialog(
            onDismiss = { pendingImportUri = null },
            onConfirm = { masterPassword ->
                pendingImportUri = null
                viewModel.importData(uri, masterPassword)
            }
        )
    }

    // 导入警告对话框
    if (showImportDataWarning) {
        ImportDataWarningDialog(isBiometricEnabled = isBiometricEnabled, onDismiss = { showImportDataWarning = false }, onConfirm = {
            showImportDataWarning = false
            importLauncher.launch(arrayOf("*/*"))
        })
    }

    // 分享数据安全提示对话框
    if (showShareSecurityInfo) {
        ShareSecurityInfoDialog(onDismiss = { showShareSecurityInfo = false })
    }

    // 处理事件
    LaunchedEffect(event) {
        event?.let { currentEvent ->
            viewModel.consumeEvent(currentEvent)
            when (currentEvent) {
                is SettingsPageViewModel.Event.ExportSuccess -> Toast.makeText(context, R.string.export_success, Toast.LENGTH_SHORT).show()
                is SettingsPageViewModel.Event.ExportFailed -> Toast.makeText(context, R.string.generate_file_failed, Toast.LENGTH_SHORT).show()
                is SettingsPageViewModel.Event.ImportSuccess -> {
                    if (currentEvent.biometricsWereDisabled) {
                        Toast.makeText(context, R.string.import_success_biometric_reset, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, R.string.import_success_restart, Toast.LENGTH_LONG).show()
                    }
                    AppUtils.restartApp(context)
                }

                is SettingsPageViewModel.Event.ImportFailed -> Toast.makeText(context, R.string.import_failed, Toast.LENGTH_SHORT).show()
                is SettingsPageViewModel.Event.ImportUpgradeFailed -> {
                    Toast.makeText(context, R.string.vault_upgrade_failed, Toast.LENGTH_LONG).show()
                }
                is SettingsPageViewModel.Event.ShareFile -> {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", currentEvent.file
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/octet-stream"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_data)))
                }

                else -> { /* 其他事件在这里不处理 */
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(
                    stringResource(R.string.settings_data),
                    style = MaterialTheme.typography.titleLarge,
                )
            }, navigationIcon = {
                IconButton(
                    onClick = { navController.popBackStack() },
                    enabled = !isLoading,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }, colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ))
        }, containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item { SettingsDivider() }
                item {
                    SettingsGroup {
                        SettingsListItem(
                            title = stringResource(R.string.export_data),
                            icon = Icons.Outlined.FileDownload,
                            onClick = { exportLauncher.launch("pocketvault.hpb") },
                            inGroup = true,
                        )
                        SettingsGroupDivider()
                        SettingsListItem(
                            title = stringResource(R.string.share_data),
                            icon = Icons.Outlined.Share,
                            onClick = { viewModel.shareData(context) },
                            trailingContent = {
                                IconButton(
                                    onClick = { showShareSecurityInfo = true },
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = stringResource(R.string.share_data_security_title),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                            inGroup = true,
                        )
                        SettingsGroupDivider()
                        SettingsListItem(
                            title = stringResource(R.string.import_data),
                            icon = Icons.Outlined.FileUpload,
                            onClick = { showImportDataWarning = true },
                            inGroup = true,
                        )
                    }
                }
                item { SettingsDivider() }
            }
            if (isLoading) {
                LoadingOverlay()
            }
        }
    }
}

/**
 * 分享数据安全提示对话框
 */
@Composable
private fun ShareSecurityInfoDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                stringResource(R.string.share_data_security_title),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                stringResource(R.string.share_data_security_message),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.got_it))
            }
        }
    )
}

@Composable
private fun ImportDataWarningDialog(
    isBiometricEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val biometricInfo = stringResource(R.string.import_data_biometric_info)
    val additionInfo = if (isBiometricEnabled) biometricInfo else ""
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = { Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.important_notice), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = { Text(stringResource(R.string.import_data_warning, additionInfo), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        confirmButton = { Button(onClick = onConfirm) { Text(stringResource(R.string.continue_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
