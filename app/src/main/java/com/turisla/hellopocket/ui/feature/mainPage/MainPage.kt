package com.turisla.hellopocket.ui.feature.mainPage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.turisla.hellopocket.R
import com.turisla.hellopocket.model.PasswordEntry
import com.turisla.hellopocket.router.RouteAddPassword
import com.turisla.hellopocket.router.RouteAddPaymentCard
import com.turisla.hellopocket.router.RouteAddSecureNote
import com.turisla.hellopocket.router.RouteAddTotpManual
import com.turisla.hellopocket.router.RouteCategoryManagement
import com.turisla.hellopocket.router.RouteQA
import com.turisla.hellopocket.router.RouteSearch
import com.turisla.hellopocket.router.RouteTotpScanner
import com.turisla.hellopocket.ui.feature.common.AppIconTile
import com.turisla.hellopocket.ui.feature.common.AppListRow
import com.turisla.hellopocket.ui.feature.common.AppSectionSurface
import com.turisla.hellopocket.ui.feature.home.HomePage
import com.turisla.hellopocket.ui.feature.home.HomePageViewModel
import com.turisla.hellopocket.ui.feature.mainPage.model.MainNavItem
import com.turisla.hellopocket.ui.feature.settings.SettingsPage
import com.turisla.hellopocket.ui.feature.totp.TotpPage
import com.turisla.hellopocket.ui.theme.AppSpacing
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainPage(
    navController: NavController,
    viewModel: HomePageViewModel = koinViewModel(),
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    val filteredEntries by viewModel.filteredPasswordEntries.collectAsStateWithLifecycle()

    val navItems = listOf(
        MainNavItem(
            name = stringResource(R.string.tab_home),
            selectedIcon = Icons.Filled.Home,
            unselectedIcon = Icons.Outlined.Home,
        ),
        MainNavItem(
            name = stringResource(R.string.tab_totp),
            selectedIcon = Icons.Filled.QrCodeScanner,
            unselectedIcon = Icons.Outlined.QrCodeScanner,
        ),
        MainNavItem(
            name = stringResource(R.string.tab_settings),
            selectedIcon = Icons.Filled.Settings,
            unselectedIcon = Icons.Outlined.Settings,
        ),
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MainTopAppBar(
                selectedTab = selectedTab,
                onSearchClick = {
                    navController.navigate(RouteSearch) { launchSingleTop = true }
                },
                onHelpClick = { navController.navigate(RouteQA) },
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                tonalElevation = 0.dp,
            ) {
                navItems.forEachIndexed { index, navItem ->
                    val selected = selectedTab == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            selectedTab = index
                            showAddSheet = false
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) {
                                    navItem.selectedIcon
                                } else {
                                    navItem.unselectedIcon
                                },
                                contentDescription = null,
                            )
                        },
                        label = {
                            Text(
                                text = navItem.name,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = selectedTab == HOME_TAB || selectedTab == TOTP_TAB,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                FloatingActionButton(
                    onClick = { showAddSheet = true },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.primary,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 2.dp,
                        pressedElevation = 5.dp,
                        focusedElevation = 3.dp,
                        hoveredElevation = 3.dp,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.add_item),
                        modifier = Modifier.size(24.dp),
                    )
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
            viewModel = viewModel,
        )
    }

    if (showAddSheet) {
        AddItemSheet(
            isTotpTab = selectedTab == TOTP_TAB,
            onDismiss = { showAddSheet = false },
            onAddPassword = {
                showAddSheet = false
                navController.navigate(RouteAddPassword)
            },
            onAddNote = {
                showAddSheet = false
                navController.navigate(RouteAddSecureNote)
            },
            onAddPaymentCard = {
                showAddSheet = false
                navController.navigate(RouteAddPaymentCard)
            },
            onScanTotp = {
                showAddSheet = false
                navController.navigate(RouteTotpScanner)
            },
            onAddTotpManually = {
                showAddSheet = false
                navController.navigate(RouteAddTotpManual)
            },
        )
    }
}

@Composable
private fun MainScreen(
    currentIndex: Int,
    navController: NavController,
    passwordEntries: List<PasswordEntry>,
    viewModel: HomePageViewModel,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (currentIndex) {
            HOME_TAB -> HomePage(
                navController = navController,
                passwordEntries = passwordEntries,
                viewModel = viewModel,
                onCategoryManagementClick = {
                    navController.navigate(RouteCategoryManagement)
                },
            )

            TOTP_TAB -> TotpPage(navController = navController)
            SETTINGS_TAB -> SettingsPage(navController = navController)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopAppBar(
    selectedTab: Int,
    onSearchClick: () -> Unit,
    onHelpClick: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = when (selectedTab) {
                    HOME_TAB -> stringResource(R.string.app_name)
                    TOTP_TAB -> stringResource(R.string.tab_totp)
                    else -> stringResource(R.string.settings)
                },
                style = MaterialTheme.typography.titleLarge,
            )
        },
        actions = {
            when (selectedTab) {
                HOME_TAB -> IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = stringResource(R.string.search_vault),
                    )
                }

                SETTINGS_TAB -> IconButton(onClick = onHelpClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = stringResource(R.string.qa_title),
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddItemSheet(
    isTotpTab: Boolean,
    onDismiss: () -> Unit,
    onAddPassword: () -> Unit,
    onAddNote: () -> Unit,
    onAddPaymentCard: () -> Unit,
    onScanTotp: () -> Unit,
    onAddTotpManually: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = AppSpacing.md)
                .navigationBarsPadding(),
        ) {
            Text(
                text = if (isTotpTab) {
                    stringResource(R.string.totp_add_entry)
                } else {
                    stringResource(R.string.select_type)
                },
                modifier = Modifier.padding(
                    horizontal = AppSpacing.xs,
                    vertical = AppSpacing.sm,
                ),
                style = MaterialTheme.typography.titleLarge,
            )

            AppSectionSurface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                if (isTotpTab) {
                    AppListRow(
                        headline = stringResource(R.string.scan_qr_code),
                        onClick = onScanTotp,
                        leading = {
                            AddSheetIcon(imageVector = Icons.Outlined.QrCodeScanner)
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 68.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    AppListRow(
                        headline = stringResource(R.string.totp_enter_manually),
                        onClick = onAddTotpManually,
                        leading = {
                            AddSheetIcon(imageVector = Icons.Outlined.Edit)
                        },
                    )
                } else {
                    AppListRow(
                        headline = stringResource(R.string.add_new_password),
                        onClick = onAddPassword,
                        leading = {
                            AddSheetIcon(imageVector = Icons.Outlined.Key)
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 68.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    AppListRow(
                        headline = stringResource(R.string.add_secure_note),
                        onClick = onAddNote,
                        leading = {
                            AddSheetIcon(
                                imageVector = Icons.Filled.NoteAlt,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 68.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    AppListRow(
                        headline = stringResource(R.string.add_payment_card),
                        onClick = onAddPaymentCard,
                        leading = {
                            AddSheetIcon(
                                imageVector = Icons.Outlined.CreditCard,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.size(AppSpacing.xl))
        }
    }
}

@Composable
private fun AddSheetIcon(
    imageVector: ImageVector,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    AppIconTile(
        imageVector = imageVector,
        contentDescription = null,
        containerColor = Color.Transparent,
        contentColor = color,
        border = BorderStroke(
            width = 1.dp,
            color = color.copy(alpha = 0.32f),
        ),
    )
}

private const val HOME_TAB = 0
private const val TOTP_TAB = 1
private const val SETTINGS_TAB = 2
