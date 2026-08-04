package com.turisla.hellopocket.ui.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.turisla.hellopocket.R
import com.turisla.hellopocket.router.RouteAbout
import com.turisla.hellopocket.router.RouteAppearanceSettings
import com.turisla.hellopocket.router.RouteDataSettings
import com.turisla.hellopocket.router.RouteOtherSettings
import com.turisla.hellopocket.router.RouteSecuritySettings

/**
 * 设置主页面
 * 显示分类入口，点击进入对应的子设置页面
 */
@Composable
fun SettingsPage(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item { SettingsDivider() }
        
        // 外观
        item {
            SettingsCategoryItem(
                title = stringResource(R.string.settings_appearance),
                description = stringResource(R.string.settings_appearance_desc),
                icon = Icons.Outlined.Palette,
                onClick = { navController.navigate(RouteAppearanceSettings) }
            )
        }
        item { SettingsDivider() }
        
        // 安全
        item {
            SettingsCategoryItem(
                title = stringResource(R.string.settings_security),
                description = stringResource(R.string.settings_security_desc),
                icon = Icons.Outlined.Security,
                onClick = { navController.navigate(RouteSecuritySettings) }
            )
        }
        item { SettingsDivider() }
        
        // 数据
        item {
            SettingsCategoryItem(
                title = stringResource(R.string.settings_data),
                description = stringResource(R.string.settings_data_desc),
                icon = Icons.Outlined.Storage,
                onClick = { navController.navigate(RouteDataSettings) }
            )
        }
        item { SettingsDivider() }
        
        // 其他
        item {
            SettingsCategoryItem(
                title = stringResource(R.string.settings_other),
                description = stringResource(R.string.settings_other_desc),
                icon = Icons.Outlined.MoreHoriz,
                onClick = { navController.navigate(RouteOtherSettings) }
            )
        }
        item { SettingsDivider() }
        
        // 关于
        item {
            SettingsCategoryItem(
                title = stringResource(R.string.about_us),
                icon = Icons.Outlined.Info,
                onClick = {
                    navController.navigate(RouteAbout)
                }
            )
        }
        item { SettingsDivider() }
    }
}

@Composable
private fun SettingsCategoryItem(
    title: String,
    icon: ImageVector,
    description: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
fun SettingsListItem(
    title: String,
    icon: ImageVector,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = title, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailingContent != null) {
            trailingContent()
        }
    }
}
