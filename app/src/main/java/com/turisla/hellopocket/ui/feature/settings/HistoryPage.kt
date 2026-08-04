package com.turisla.hellopocket.ui.feature.settings

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.navigation.NavController
import com.turisla.hellopocket.MainActivity
import com.turisla.hellopocket.R
import com.turisla.hellopocket.ui.feature.common.LoadingOverlay
import com.turisla.hellopocket.ui.feature.common.ImportMasterPasswordDialog
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    navController: NavController, viewModel: HistoryViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val event by viewModel.event.collectAsStateWithLifecycle()
    val isBiometricUnlockEnabled by viewModel.isBiometricUnlockEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showRestoreDialog by remember { mutableStateOf<File?>(null) }
    var showDeleteDialog by remember { mutableStateOf<File?>(null) }
    var pendingRestoreFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(event) {
        event?.let { currentEvent ->
            viewModel.consumeEvent(currentEvent)
            when (currentEvent) {
                is HistoryViewModel.Event.RestoreSuccess -> {
                    Toast.makeText(context, context.getString(R.string.restore_success), Toast.LENGTH_LONG).show()
                    val intent = Intent(context, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    (context as? Activity)?.finish()
                }

                is HistoryViewModel.Event.RestoreFailed -> {
                    Toast.makeText(context, context.getString(R.string.restore_failed), Toast.LENGTH_SHORT).show()
                }

                is HistoryViewModel.Event.DeleteSuccess -> {
                    Toast.makeText(context, context.getString(R.string.delete_backup_success), Toast.LENGTH_SHORT).show()
                }

                is HistoryViewModel.Event.DeleteFailed -> {
                    Toast.makeText(context, context.getString(R.string.delete_backup_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    showRestoreDialog?.let { file ->
        RestoreWarningDialog(isBiometricUnlockEnabled = isBiometricUnlockEnabled, fileName = file.name, onDismiss = { showRestoreDialog = null }, onConfirm = {
            pendingRestoreFile = file
            showRestoreDialog = null
        })
    }

    pendingRestoreFile?.let { file ->
        ImportMasterPasswordDialog(
            onDismiss = { pendingRestoreFile = null },
            onConfirm = { masterPassword ->
                pendingRestoreFile = null
                viewModel.restoreBackup(file, masterPassword)
            }
        )
    }

    showDeleteDialog?.let { file ->
        DeleteWarningDialog(file = file, onDismiss = { showDeleteDialog = null }, onConfirm = {
            viewModel.deleteBackup(file)
            showDeleteDialog = null
        })
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text(stringResource(R.string.history_title)) }, navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            })
        }) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
        ) {
            if (uiState.backupFiles.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.backupFiles, key = { it.name }) {
                        HistoryItem(file = it, onRestoreClick = { showRestoreDialog = it }, onDeleteClick = { showDeleteDialog = it })
                    }
                }
            }
            if (uiState.isLoading) {
                LoadingOverlay()
            }
        }
    }
}

@Composable
private fun HistoryItem(file: File, onRestoreClick: (File) -> Unit, onDeleteClick: (File) -> Unit) {
    val context = LocalContext.current
    val formattedDate = remember(file.lastModified()) {
        try {
            val sdf = SimpleDateFormat(context.getString(R.string.data_format), Locale.getDefault())
            sdf.format(Date(file.lastModified()))
        } catch (e: Exception) {
            context.getString(R.string.unknown_date)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.backup_date_format, formattedDate), style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = file.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                Modifier.align(Alignment.End)
            ) {
                IconButton(onClick = { onDeleteClick(file) }) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.width(15.dp))
                IconButton(onClick = { onRestoreClick(file) }) {
                    Icon(Icons.Outlined.Restore, contentDescription = stringResource(R.string.confirm_restore))
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.History, contentDescription = null, modifier = Modifier
                .width(64.dp)
                .height(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.no_backup_history), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun RestoreWarningDialog(isBiometricUnlockEnabled: Boolean, fileName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val additionInfo = if (isBiometricUnlockEnabled) stringResource(R.string.restore_biometric_info) else ""
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = { Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.confirm_restore), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = {
            Text(
                text = stringResource(R.string.restore_warning, fileName, additionInfo),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        confirmButton = { Button(onClick = onConfirm) { Text(stringResource(R.string.confirm_restore_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun DeleteWarningDialog(file: File, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = { Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.confirm_delete_backup), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = {
            Text(
                text = stringResource(R.string.delete_backup_warning, file.name), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )
        },
        confirmButton = { Button(onClick = onConfirm) { Text(stringResource(R.string.confirm_delete_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
