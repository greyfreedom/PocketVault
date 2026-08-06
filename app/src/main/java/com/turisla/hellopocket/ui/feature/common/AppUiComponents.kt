package com.turisla.hellopocket.ui.feature.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.turisla.hellopocket.ui.theme.AppSpacing

@Composable
fun AppSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = AppSpacing.xs),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun AppSectionSurface(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = color,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(content = content)
    }
}

@Composable
fun AppIconTile(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    border: BorderStroke? = null,
) {
    AppIconTile(
        modifier = modifier,
        containerColor = containerColor,
        border = border,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
            tint = contentColor,
        )
    }
}

@Composable
fun AppIconTile(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    border: BorderStroke? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier.size(40.dp),
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        border = border,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center, content = content)
    }
}

@Composable
fun AppListRow(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(modifier = Modifier.width(AppSpacing.sm))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!supporting.isNullOrBlank()) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (trailing != null) {
                Spacer(modifier = Modifier.width(AppSpacing.xs))
                trailing()
            }
        }
    }

    if (onClick == null) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = Color.Transparent,
            content = rowContent,
        )
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            color = Color.Transparent,
            content = rowContent,
        )
    }
}

@Composable
fun AppEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = AppSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(AppSpacing.lg).size(32.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
