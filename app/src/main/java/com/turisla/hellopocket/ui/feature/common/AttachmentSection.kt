package com.turisla.hellopocket.ui.feature.common

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.turisla.hellopocket.R
import com.turisla.hellopocket.model.AttachmentManifestEntry

@Composable
fun AttachmentSection(
    attachmentIds: Set<String>,
    allAttachments: List<AttachmentManifestEntry>,
    onAddAttachment: (Uri) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    modifier: Modifier = Modifier,
    onViewAttachment: ((String) -> Unit)? = null, // 如果为null，则点击无动作（或者只显示预览）
    isEditable: Boolean = true, // 是否为可编辑模式
    loadThumbnail: suspend (String) -> Any? = { null } // 加载缩略图的方法
) {
    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { onAddAttachment(it) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 只在可编辑模式下显示添加按钮
            if (isEditable) {
                Text(
                    text = stringResource(id = R.string.attachments),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = { launcher.launch(arrayOf("*/*")) },
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_attachment),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val currentAttachments = remember(allAttachments, attachmentIds) {
            allAttachments.filter { attachmentIds.contains(it.id) }
        }

        if (currentAttachments.isEmpty()) {
            Text(
                text = stringResource(R.string.no_attachments),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(currentAttachments, key = { it.id }) { attachment ->
                    AttachmentChip(
                        attachment = attachment,
                        onRemove = { onRemoveAttachment(attachment.id) },
                        onClick = { onViewAttachment?.invoke(attachment.id) },
                        showRemoveButton = isEditable, // 只在可编辑模式下显示删除按钮
                        loadThumbnail = loadThumbnail
                    )
                }
            }
        }
    }
}

@Composable
fun AttachmentChip(
    attachment: AttachmentManifestEntry,
    onRemove: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showRemoveButton: Boolean = true, // 是否显示删除按钮
    loadThumbnail: suspend (String) -> Any?
) {
    val isImageOrVideo = attachment.mimeType.startsWith("image/") || attachment.mimeType.startsWith("video/")
    
    // 异步加载缩略图
    val thumbnailModel by produceState<Any?>(
        null,
        attachment.id,
        attachment.mimeType,
        loadThumbnail,
    ) {
        value = null
        if (isImageOrVideo) {
            value = loadThumbnail(attachment.id)
        }
    }

    val icon = when {
        attachment.mimeType.startsWith("image/") -> Icons.Default.Image
        attachment.mimeType.startsWith("video/") -> Icons.Default.VideoFile
        attachment.mimeType.startsWith("audio/") -> Icons.Default.AudioFile
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }

    Box(
        modifier = modifier
            .width(120.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                if (thumbnailModel != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(thumbnailModel)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp))
                    )
                    // 如果是视频，叠加一个播放图标
                    if (attachment.mimeType.startsWith("video/")) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                // 只在可编辑模式下显示删除按钮
                if (showRemoveButton) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 12.dp, y = (-12).dp),
                    ) {
                        // 将 24dp 可见按钮贴到预览右上角，同时保留 48dp 触控热区。
                        Surface(
                            modifier = Modifier.size(24.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.remove_attachment),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = attachment.originalFileName,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
