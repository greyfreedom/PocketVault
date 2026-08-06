package com.turisla.hellopocket.ui.feature.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import com.turisla.hellopocket.ui.feature.common.AppListRow
import com.turisla.hellopocket.ui.feature.common.AppSectionSurface
import com.turisla.hellopocket.ui.theme.AppSpacing

/** 设置入口使用统一的色面分组，避免整页分割线形成旧式系统列表观感。 */
@Composable
fun SettingsPage(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item { SettingsDivider() }
        item {
            SettingsGroup {
                SettingsCategoryItem(
                    title = stringResource(R.string.settings_appearance),
                    description = stringResource(R.string.settings_appearance_desc),
                    icon = Icons.Outlined.Palette,
                    onClick = { navController.navigate(RouteAppearanceSettings) },
                    inGroup = true,
                )
                SettingsGroupDivider()
                SettingsCategoryItem(
                    title = stringResource(R.string.settings_security),
                    description = stringResource(R.string.settings_security_desc),
                    icon = Icons.Outlined.Security,
                    onClick = { navController.navigate(RouteSecuritySettings) },
                    inGroup = true,
                )
                SettingsGroupDivider()
                SettingsCategoryItem(
                    title = stringResource(R.string.settings_data),
                    description = stringResource(R.string.settings_data_desc),
                    icon = Icons.Outlined.Storage,
                    onClick = { navController.navigate(RouteDataSettings) },
                    inGroup = true,
                )
                SettingsGroupDivider()
                SettingsCategoryItem(
                    title = stringResource(R.string.settings_other),
                    description = stringResource(R.string.settings_other_desc),
                    icon = Icons.Outlined.MoreHoriz,
                    onClick = { navController.navigate(RouteOtherSettings) },
                    inGroup = true,
                )
            }
        }
        item { SettingsDivider() }
        item {
            SettingsGroup {
                SettingsCategoryItem(
                    title = stringResource(R.string.about_us),
                    icon = Icons.Outlined.Info,
                    onClick = { navController.navigate(RouteAbout) },
                    inGroup = true,
                )
            }
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
    inGroup: Boolean = false,
) {
    SettingsListItem(
        title = title,
        description = description,
        icon = icon,
        onClick = onClick,
        inGroup = inGroup,
    )
}

/** 旧页面仍调用该函数；它现在表示卡片之间的呼吸间距。 */
@Composable
fun SettingsDivider() {
    Spacer(modifier = Modifier.height(AppSpacing.xs))
}

@Composable
fun SettingsGroup(
    content: @Composable ColumnScope.() -> Unit,
) {
    AppSectionSurface(
        modifier = Modifier.padding(horizontal = AppSpacing.md),
        content = content,
    )
}

@Composable
fun SettingsGroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
fun SettingsListItem(
    title: String,
    icon: ImageVector,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    inGroup: Boolean = false,
) {
    val row: @Composable () -> Unit = {
        AppListRow(
            headline = title,
            supporting = description,
            onClick = onClick,
            leading = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            trailing = {
                when {
                    trailingContent != null -> trailingContent()
                    onClick != null -> Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }

    if (inGroup) {
        row()
    } else {
        AppSectionSurface(
            modifier = Modifier.padding(horizontal = AppSpacing.md),
        ) {
            row()
        }
    }
}
