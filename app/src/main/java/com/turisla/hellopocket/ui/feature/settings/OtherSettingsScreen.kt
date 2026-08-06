package com.turisla.hellopocket.ui.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.turisla.hellopocket.R
import com.turisla.hellopocket.router.RouteCategoryManagement
import com.turisla.hellopocket.router.RouteHistory

/**
 * 其他设置页面
 * 包含历史记录、管理分类
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherSettingsScreen(
    navController: NavController,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(
                    stringResource(R.string.settings_other),
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
                        title = stringResource(R.string.history),
                        icon = Icons.Outlined.History,
                        onClick = { navController.navigate(RouteHistory) },
                        inGroup = true,
                    )
                    SettingsGroupDivider()
                    SettingsListItem(
                        title = stringResource(R.string.manage_categories),
                        icon = Icons.Outlined.Category,
                        onClick = { navController.navigate(RouteCategoryManagement) },
                        inGroup = true,
                    )
                }
            }
            item { SettingsDivider() }
        }
    }
}
