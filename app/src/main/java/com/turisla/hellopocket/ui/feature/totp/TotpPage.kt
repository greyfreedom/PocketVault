package com.turisla.hellopocket.ui.feature.totp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.turisla.hellopocket.R
import com.turisla.hellopocket.router.RouteEditTotp
import com.turisla.hellopocket.ui.feature.common.ConfirmDeleteDialog
import org.koin.androidx.compose.koinViewModel

/**
 * TOTP 页面
 * 显示已保存的 TOTP 条目列表
 */
@Composable
fun TotpPage(
    navController: NavController,
    viewModel: TotpViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val totpEntries by viewModel.totpEntries.collectAsStateWithLifecycle()
    val expandedIds by viewModel.expandedIds.collectAsStateWithLifecycle()

    var entryToDelete by remember { mutableStateOf<com.turisla.hellopocket.model.TotpEntry?>(null) }

    LaunchedEffect(viewModel, context) {
        viewModel.repositoryErrorEvents.collect { error ->
            android.widget.Toast.makeText(
                context,
                context.getString(error.messageResId),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    // 删除确认对话框
    entryToDelete?.let { entry ->
        ConfirmDeleteDialog(
            itemName = "${entry.issuer}: ${entry.account}",
            onConfirm = {
                viewModel.deleteTotpEntry(entry.id)
                entryToDelete = null
            },
            onDismiss = { entryToDelete = null }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (totpEntries.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.totp_empty_message),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.totp_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // TOTP 列表
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.size(8.dp))
                }
                items(totpEntries, key = { it.id }) { entry ->
                    val isExpanded = expandedIds.contains(entry.id)

                    TotpListItem(
                        entry = entry,
                        isExpanded = isExpanded,
                        generateCode = { viewModel.generateCode(entry) },
                        onToggleExpand = { viewModel.toggleExpanded(entry.id) },
                        onCopy = { code -> viewModel.copyCode(code) },
                        onDelete = { entryToDelete = entry },
                        onEdit = { navController.navigate(RouteEditTotp(entry.id)) },
                    )
                }
                item {
                    Spacer(modifier = Modifier.size(72.dp)) // 为 FAB 留出空间
                }
            }
        }
    }
}
