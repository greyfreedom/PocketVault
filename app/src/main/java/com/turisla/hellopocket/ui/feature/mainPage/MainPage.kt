package com.turisla.hellopocket.ui.feature.mainPage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.turisla.hellopocket.R
import com.turisla.hellopocket.model.PasswordEntry
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import com.turisla.hellopocket.router.RouteQA
import com.turisla.hellopocket.router.RouteAddPassword
import com.turisla.hellopocket.router.RouteAddSecureNote
import com.turisla.hellopocket.router.RouteAddTotpManual
import com.turisla.hellopocket.router.RouteCategoryManagement
import com.turisla.hellopocket.router.RouteSearch
import com.turisla.hellopocket.router.RouteTotpScanner
import com.turisla.hellopocket.ui.feature.home.AddPasswordDialog
import com.turisla.hellopocket.ui.feature.home.HomePage
import com.turisla.hellopocket.ui.feature.home.HomePageViewModel
import com.turisla.hellopocket.ui.feature.mainPage.model.MainNavItem
import com.turisla.hellopocket.ui.feature.settings.SettingsPage
import com.turisla.hellopocket.ui.feature.totp.TotpPage
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainPage(navController: NavController, viewModel: HomePageViewModel = koinViewModel()) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showAddMenu by remember { mutableStateOf(false) }  // 提升状态到顶层
    var showTotpAddMenu by remember { mutableStateOf(false) }
    val filteredEntries by viewModel.filteredPasswordEntries.collectAsStateWithLifecycle()

    val navItemList = remember(LocalConfiguration.current) {
        listOf(
            MainNavItem(context.getString(R.string.tab_home), Icons.Default.Home),
            MainNavItem(context.getString(R.string.tab_totp), Icons.Default.QrCodeScanner),
            MainNavItem(context.getString(R.string.tab_settings), Icons.Default.Settings),
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                when (selectedTab) {
                    0 -> HomeTopAppBar(
                        onSearchClick = {
                            navController.navigate(RouteSearch) {
                                launchSingleTop = true
                            }
                        }
                    )
                    1 -> TopAppBar(
                        title = { Text(stringResource(R.string.tab_totp)) },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                    else -> TopAppBar(
                        title = { Text(stringResource(R.string.settings)) },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                        actions = {
                            IconButton(onClick = {
                                navController.navigate(RouteQA)
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                                    contentDescription = stringResource(R.string.qa_title)
                                )
                            }
                        }
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    tonalElevation = 6.dp,
                ) {
                    navItemList.forEachIndexed { index, navItem ->
                        NavigationBarItem(
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            selected = selectedTab == index,
                            onClick = {
                                if (index != selectedTab) {
                                    showAddMenu = false
                                    showTotpAddMenu = false
                                }
                                selectedTab = index
                            },
                            icon = { 
                                Icon(
                                    imageVector = navItem.icon, 
                                    contentDescription = null
                                ) 
                            },
                            label = { 
                                Text(
                                    text = navItem.name,
                                    style = MaterialTheme.typography.labelSmall
                                ) 
                            }
                        )
                    }
                }
            },
            floatingActionButton = {
                // 主页和 TOTP Tab 都显示添加按钮，并按当前类型提供对应的添加方式。
                AnimatedVisibility(
                    visible = selectedTab == 0 || selectedTab == 1,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box {
                        FloatingActionButton(
                            containerColor = MaterialTheme.colorScheme.primary,
                            onClick = {
                                when (selectedTab) {
                                    0 -> showAddMenu = !showAddMenu  // 主页：切换菜单显示状态
                                    1 -> showTotpAddMenu = !showTotpAddMenu
                                }
                            },
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = if (selectedTab == 1) {
                                    stringResource(R.string.totp_add_entry)
                                } else {
                                    stringResource(R.string.add_new_password)
                                },
                            )
                        }

                        DropdownMenu(
                            expanded = showTotpAddMenu,
                            onDismissRequest = { showTotpAddMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.scan_qr_code)) },
                                leadingIcon = {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                                },
                                onClick = {
                                    showTotpAddMenu = false
                                    navController.navigate(RouteTotpScanner)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.totp_enter_manually)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                },
                                onClick = {
                                    showTotpAddMenu = false
                                    navController.navigate(RouteAddTotpManual)
                                },
                            )
                        }
                        
                        // 使用 Popup 实现自定义卡片样式菜单
                        if (showAddMenu) {
                            val density = androidx.compose.ui.platform.LocalDensity.current
                            val spacing = with(density) { 12.dp.roundToPx() }

                            androidx.compose.ui.window.Popup(
                                popupPositionProvider = remember(spacing) {
                                    object : androidx.compose.ui.window.PopupPositionProvider {
                                        override fun calculatePosition(
                                            anchorBounds: androidx.compose.ui.unit.IntRect,
                                            windowSize: androidx.compose.ui.unit.IntSize,
                                            layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                                            popupContentSize: androidx.compose.ui.unit.IntSize
                                        ): androidx.compose.ui.unit.IntOffset {
                                            // 将菜单定位在锚点（FAB）的上方，且右对齐
                                            val x = anchorBounds.right - popupContentSize.width
                                            val y = anchorBounds.top - popupContentSize.height - spacing
                                            return androidx.compose.ui.unit.IntOffset(x, y)
                                        }
                                    }
                                },
                                onDismissRequest = { showAddMenu = false }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(end = 16.dp, bottom = 8.dp)
                                        .width(200.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // 添加密码选项
                                    Card(
                                        onClick = {
                                            showAddMenu = false
                                            navController.navigate(RouteAddPassword)
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Home,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Text(
                                                stringResource(R.string.add_new_password),
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        }
                                    }
                                    
                                    // 添加笔记选项
                                    Card(
                                        onClick = {
                                            showAddMenu = false
                                            navController.navigate(RouteAddSecureNote)
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Filled.NoteAlt,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Text(
                                                stringResource(R.string.add_secure_note),
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },

            floatingActionButtonPosition = FabPosition.End,
        ) { innerPadding ->
            MainScreen(
                modifier = Modifier.padding(innerPadding), 
                currentIndex = selectedTab, 
                navController = navController, 
                passwordEntries = filteredEntries,
                viewModel = viewModel
            )
        }
        
        // 全屏透明遮罩层，放在 Scaffold 外层，拦截所有外部点击
        if (showAddMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .clickable(onClick = { showAddMenu = false })
            )
        }
    }
}

@Composable
private fun MainScreen(
    modifier: Modifier = Modifier, 
    currentIndex: Int, 
    navController: NavController, 
    passwordEntries: List<PasswordEntry>,
    viewModel: HomePageViewModel
) {
    Box(modifier = modifier.fillMaxSize()) { // The padding is applied here to the Box
        when (currentIndex) {
            // The children should not receive the padding modifier again
            0 -> HomePage(
                navController = navController,
                passwordEntries = passwordEntries,
                viewModel = viewModel,
                onCategoryManagementClick = {
                    navController.navigate(RouteCategoryManagement)
                }
            )
            1 -> TotpPage(navController = navController)
            2 -> SettingsPage(navController = navController)
            else -> Box {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopAppBar(onSearchClick: () -> Unit) {
    TopAppBar(
        title = {
            Text(stringResource(id = R.string.app_name), color = MaterialTheme.colorScheme.onSurface)
        },
        actions = {
            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.search_vault)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}
