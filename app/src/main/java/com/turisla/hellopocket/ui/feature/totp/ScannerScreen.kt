package com.turisla.hellopocket.ui.feature.totp

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.turisla.hellopocket.R
import org.koin.androidx.compose.koinViewModel

/**
 * TOTP 二维码扫描页面
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ScannerScreen(
    navController: NavController,
    viewModel: ScannerViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scanResult by viewModel.scanResult.collectAsStateWithLifecycle()
    val addSuccess by viewModel.addSuccess.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    // 相机权限状态
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // 处理扫码结果
    LaunchedEffect(scanResult) {
        scanResult?.let { result ->
            if (result.isSuccess) {
                val uri = result.getOrNull()
                if (uri != null) {
                    viewModel.processScannedUri(uri)
                }
            } else {
                viewModel.showError(R.string.totp_invalid_qr)
            }
        }
    }

    // 处理添加成功
    LaunchedEffect(addSuccess) {
        if (addSuccess) {
            viewModel.resetAddSuccess()
            navController.navigateUp()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_qr_code)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                // 权限已授予 - 显示扫码器
                cameraPermissionState.status.isGranted -> {
                    ScannerContent(
                        onScanResult = { result -> viewModel.handleScanResult(result) }
                    )
                }

                // 首次请求权限 - 显示说明对话框
                cameraPermissionState.status.shouldShowRationale -> {
                    PermissionRationaleDialog(
                        onDismiss = { navController.navigateUp() },
                        onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
                    )
                }

                // 权限被永久拒绝 - 显示设置引导
                !cameraPermissionState.status.isGranted &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_DENIED -> {
                    PermissionDeniedScreen(
                        onRequestAgain = { cameraPermissionState.launchPermissionRequest() },
                        onDismiss = { navController.navigateUp() }
                    )
                }
            }

            // 加载指示器
            if (addSuccess) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.totp_adding_entry))
                    }
                }
            }
        }

        // 错误提示对话框
        errorMessage?.let { messageRes ->
            AlertDialog(
                onDismissRequest = { viewModel.clearError() },
                title = { Text(stringResource(R.string.error)) },
                text = { Text(stringResource(messageRes)) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }
    }
}

/**
 * 扫码器内容 - 使用 ZXing
 */
@Composable
fun ScannerContent(onScanResult: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var captureManager by remember { mutableStateOf<CaptureManager?>(null) }
    var barcodeView by remember { mutableStateOf<DecoratedBarcodeView?>(null) }

    AndroidView(
        factory = { ctx ->
            DecoratedBarcodeView(ctx).apply {
                barcodeView = this

                val barcodeCallback = object : com.journeyapps.barcodescanner.BarcodeCallback {
                    override fun barcodeResult(result: com.journeyapps.barcodescanner.BarcodeResult) {
                        onScanResult(result.text)
                    }

                    override fun possibleResultPoints(resultPoints: List<com.google.zxing.ResultPoint>) {
                        // 可选：处理扫码过程中的视觉反馈
                    }
                }
                this.decodeContinuous(barcodeCallback)
            }
        },
        update = { view ->
            // View 更新
        },
        modifier = Modifier.fillMaxSize()
    )

    // 生命周期管理
    DisposableEffect(lifecycleOwner) {
        val activity = context as? androidx.activity.ComponentActivity
        val view = barcodeView

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (activity != null && view != null) {
                        if (captureManager == null) {
                            captureManager = CaptureManager(activity, view)
                        }
                        captureManager?.onResume()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    captureManager?.onPause()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    captureManager?.onDestroy()
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        // 初始化时启动
        if (activity != null && view != null) {
            if (captureManager == null) {
                captureManager = CaptureManager(activity, view)
            }
            captureManager?.onResume()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            captureManager?.onPause()
            captureManager?.onDestroy()
        }
    }
}

/**
 * 权限说明对话框
 */
@Composable
fun PermissionRationaleDialog(
    onDismiss: () -> Unit,
    onRequestPermission: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.camera_permission_required)) },
        text = { Text(stringResource(R.string.camera_permission_rationale)) },
        confirmButton = {
            TextButton(onClick = onRequestPermission) {
                Text(stringResource(R.string.grant_permission))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * 权限被永久拒绝时的提示界面
 */
@Composable
fun PermissionDeniedScreen(
    onRequestAgain: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.QrCodeScanner,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = stringResource(R.string.camera_permission_required),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            Text(
                text = stringResource(R.string.totp_permission_denied_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.totp_go_back))
                }
                TextButton(onClick = onRequestAgain) {
                    Text(stringResource(R.string.totp_try_again))
                }
            }
        }
    }
}
