package com.turisla.hellopocket

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.turisla.hellopocket.router.RouteChangePassword
import com.turisla.hellopocket.router.RouteCategoryManagement
import com.turisla.hellopocket.router.RouteDetail
import com.turisla.hellopocket.router.RouteHistory
import com.turisla.hellopocket.router.RouteMainPage
import com.turisla.hellopocket.router.RouteSearch
import com.turisla.hellopocket.router.RouteSetup
import com.turisla.hellopocket.router.RouteUnlock
import com.turisla.hellopocket.router.RouteAddPassword
import com.turisla.hellopocket.router.RoutePasswordGenerator
import com.turisla.hellopocket.router.RouteAbout
import com.turisla.hellopocket.router.RoutePrivacyPolicy
import com.turisla.hellopocket.router.RouteOpenSourceLicenses
import com.turisla.hellopocket.router.RouteAddSecureNote
import com.turisla.hellopocket.router.RouteAppearanceSettings
import com.turisla.hellopocket.router.RouteDataSettings
import com.turisla.hellopocket.router.RouteOtherSettings
import com.turisla.hellopocket.router.RouteQA
import com.turisla.hellopocket.router.RouteSecuritySettings
import com.turisla.hellopocket.router.RouteAddTotpManual
import com.turisla.hellopocket.router.RouteEditTotp
import com.turisla.hellopocket.router.RouteTotpScanner
import com.turisla.hellopocket.ui.feature.addPassword.AddPasswordScreen
import com.turisla.hellopocket.ui.feature.addPassword.AddPasswordDraftViewModel
import com.turisla.hellopocket.ui.feature.addSecureNote.AddSecureNoteDraftViewModel
import com.turisla.hellopocket.ui.feature.addSecureNote.AddSecureNoteScreen
import com.turisla.hellopocket.ui.feature.auth.SetupScreen
import com.turisla.hellopocket.ui.feature.auth.UnlockScreen
import com.turisla.hellopocket.ui.feature.category.CategoryManagementScreen
import com.turisla.hellopocket.ui.feature.common.LoadingOverlay
import com.turisla.hellopocket.ui.feature.common.ImportMasterPasswordDialog
import com.turisla.hellopocket.ui.feature.detail.DetailScreen
import com.turisla.hellopocket.ui.feature.detail.DetailEditDraftViewModel
import com.turisla.hellopocket.ui.feature.mainPage.MainPage
import com.turisla.hellopocket.ui.feature.passwordGenerator.PasswordGeneratorScreen
import com.turisla.hellopocket.ui.feature.search.SearchScreen
import com.turisla.hellopocket.ui.feature.settings.AboutScreen
import com.turisla.hellopocket.ui.feature.settings.AppearanceSettingsScreen
import com.turisla.hellopocket.ui.feature.settings.ChangePasswordScreen
import com.turisla.hellopocket.ui.feature.settings.DataSettingsScreen
import com.turisla.hellopocket.ui.feature.settings.HistoryScreen
import com.turisla.hellopocket.ui.feature.settings.OtherSettingsScreen
import com.turisla.hellopocket.ui.feature.settings.QAScreen
import com.turisla.hellopocket.ui.feature.settings.PrivacyPolicyScreen
import com.turisla.hellopocket.ui.feature.settings.OpenSourceLicensesScreen
import com.turisla.hellopocket.ui.feature.totp.ManualTotpScreen
import com.turisla.hellopocket.ui.feature.totp.ScannerScreen
import com.turisla.hellopocket.ui.feature.settings.SecuritySettingsScreen
import com.turisla.hellopocket.ui.theme.HelloPocketTheme
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.android.ext.android.inject
import com.turisla.hellopocket.data.UserPreferencesRepository
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import javax.crypto.Cipher

class MainActivity : AppCompatActivity() {

    private val mainViewModel: MainViewModel by viewModel()
    private val userPreferencesRepository: UserPreferencesRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 密码库的所有页面都可能展示敏感数据，统一禁止截屏、录屏和最近任务缩略图。
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        userPreferencesRepository.applyTheme() // Apply user theme preference
        enableEdgeToEdge()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                mainViewModel.biometricUnlockEvent.collect { cipher ->
                    cipher?.let {
                        mainViewModel.consumeBiometricUnlockEvent(it)
                        showBiometricPrompt(it)
                    }
                }
            }
        }

        // 配置 Coil ImageLoader 以支持视频缩略图
        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
        Coil.setImageLoader(imageLoader)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.infoEvent.collect { event ->
                    event ?: return@collect
                    mainViewModel.consumeInfoEvent(event)
                    when (event) {
                        is MainPageInfo.ImportDataFailed -> {
                            Toast.makeText(this@MainActivity, getString(R.string.import_failed_registration), Toast.LENGTH_SHORT).show()
                        }
                        is MainPageInfo.ImportUpgradeFailed -> {
                            Toast.makeText(this@MainActivity, getString(R.string.vault_upgrade_failed), Toast.LENGTH_LONG).show()
                        }
                        is MainPageInfo.VaultSetupFailed -> {
                            Toast.makeText(this@MainActivity, getString(R.string.save_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        setContent {
            HelloPocketTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(mainViewModel)
                    // 合并所有加载状态：常规加载 或 附件解密加载
                    if (mainViewModel.isLoading.collectAsStateWithLifecycle().value) {
                        LoadingOverlay()
                    } else if (mainViewModel.isLoadingAttachment.collectAsStateWithLifecycle().value) {
                        LoadingOverlay(showBackground = false)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun showBiometricPrompt(cipher: Cipher) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder().setTitle(getString(R.string.fingerprint_unlock_title)).setSubtitle(getString(R.string.fingerprint_unlock_subtitle)).setNegativeButtonText(getString(R.string.use_master_password)).build()

        val biometricPrompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Toast.makeText(this@MainActivity, errString, Toast.LENGTH_SHORT).show()
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                result.cryptoObject?.cipher?.let {
                    mainViewModel.onBiometricUnlockSucceeded(it)
                }
            }
        })

        biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }
}

/**
 * 在内存中把生成结果直接交还给发起生成请求的草稿。
 * 不保存密码字符串，也不使用 SavedStateHandle，避免结果跨新增/编辑页面串用。
 */
internal class GeneratedPasswordRequest {
    private var consumer: ((String) -> Unit)? = null

    fun begin(consumer: (String) -> Unit) {
        this.consumer = consumer
    }

    fun deliver(password: String) {
        val currentConsumer = consumer
        consumer = null
        currentConsumer?.invoke(password)
    }

    fun clear() {
        consumer = null
    }
}

@Composable
private fun AppNavigation(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val generatedPasswordRequest = remember { GeneratedPasswordRequest() }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(), onResult = { uri ->
            pendingImportUri = uri
        })

    pendingImportUri?.let { uri ->
        ImportMasterPasswordDialog(
            onDismiss = { pendingImportUri = null },
            onConfirm = { masterPassword ->
                pendingImportUri = null
                viewModel.importVault(uri, masterPassword)
            }
        )
    }

    val activity = LocalActivity.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    BackHandler(enabled = currentDestination?.route == RouteMainPage::class.qualifiedName) {
        activity?.finish()
    }

    LaunchedEffect(uiState) {
        if (uiState !is UiState.Unlocked) {
            generatedPasswordRequest.clear()
        }
        if (uiState is UiState.Unlocked) {
            navController.navigate(RouteMainPage) {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
            }
        } else if (uiState is UiState.Locked) {
            if (navController.currentBackStackEntry?.destination?.route != RouteUnlock::class.qualifiedName) {
                // 弹出所有页面直到导航图的根，确保清空返回栈
                navController.navigate(RouteUnlock) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }
        }
    }

    val startDestination: Any = if (viewModel.isVaultInitialized) RouteUnlock else RouteSetup

    NavHost(navController = navController, startDestination = startDestination) {
        composable<RouteSetup> {
            SetupScreen(onSetupComplete = { password, hint -> viewModel.setupVault(password, hint) }, onImportRequest = { importLauncher.launch(arrayOf("*/*")) })
        }
        composable<RouteUnlock> {
            val vaultErrorMessage = when (val state = uiState) {
                UiState.FileCorrupted -> stringResource(R.string.vault_file_corrupted)
                UiState.IntegrityCheckFailed -> stringResource(R.string.vault_integrity_failed)
                UiState.UpgradeFailed -> stringResource(R.string.vault_upgrade_failed)
                UiState.UpgradeRequiresMasterPassword -> {
                    stringResource(R.string.vault_upgrade_requires_master_password)
                }
                is UiState.UnsupportedVersion -> stringResource(R.string.vault_version_unsupported, state.version)
                else -> null
            }
            UnlockScreen(
                onUnlockAttempt = { viewModel.unlockVault(it) },
                onBiometricUnlockRequest = { viewModel.requestBiometricUnlock() },
                showInvalidPasswordError = uiState is UiState.InvalidPassword,
                vaultErrorMessage = vaultErrorMessage,
                isBiometricUnlockEnabled = viewModel.isBiometricUnlockEnabled, // 传递状态
                passwordHint = viewModel.passwordHint
            )
        }
        composable<RouteMainPage> {
            MainPage(navController = navController)
        }
        composable<RouteSearch> {
            SearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onResultClick = { entryId ->
                    navController.navigate(RouteDetail(id = entryId))
                },
            )
        }
        composable<RouteDetail> { backStackEntry ->
            // 编辑草稿绑定到当前详情页返回栈，进入生成器后仍保留编辑模式和表单内容。
            val editDraftViewModel = koinViewModel<DetailEditDraftViewModel>(
                viewModelStoreOwner = backStackEntry
            )
            DetailScreen(
                onBack = { navController.popBackStack() },
                onNavigateToGenerator = {
                    generatedPasswordRequest.begin(editDraftViewModel::updatePassword)
                    navController.navigate(RoutePasswordGenerator)
                },
                onNavigateToCategoryManagement = { navController.navigate(RouteCategoryManagement) },
                onLoading = { viewModel.setAttachmentLoading(it) },
                editDraftViewModel = editDraftViewModel,
            )
        }
        composable<RouteChangePassword> {
            ChangePasswordScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable<RouteHistory> {
            HistoryScreen(navController = navController)
        }
        composable<RouteAddPassword> { backStackEntry ->
            // 草稿明确绑定到添加页的返回栈条目：进入生成器不会丢失，退出添加流程会自动销毁。
            val draftViewModel = koinViewModel<AddPasswordDraftViewModel>(
                viewModelStoreOwner = backStackEntry
            )
            AddPasswordScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToGenerator = {
                    generatedPasswordRequest.begin(draftViewModel::updatePassword)
                    navController.navigate(RoutePasswordGenerator)
                },
                onLoading = { viewModel.setAttachmentLoading(it) },
                draftViewModel = draftViewModel,
            )
        }
        composable<RoutePasswordGenerator> {
            DisposableEffect(Unit) {
                onDispose {
                    // 包括系统返回手势在内，只要离开生成器就释放调用方引用。
                    generatedPasswordRequest.clear()
                }
            }
            PasswordGeneratorScreen(
                onNavigateBack = {
                    generatedPasswordRequest.clear()
                    navController.popBackStack()
                },
                onUsePassword = { password ->
                    generatedPasswordRequest.deliver(password)
                    navController.popBackStack()
                }
            )
        }
        composable<RouteCategoryManagement> {
            CategoryManagementScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<RouteAbout> {
            AboutScreen(navController = navController)
        }
        composable<RoutePrivacyPolicy> {
            PrivacyPolicyScreen(navController = navController)
        }
        composable<RouteOpenSourceLicenses> {
            OpenSourceLicensesScreen(navController = navController)
        }
        composable<RouteAddSecureNote> { backStackEntry ->
            // 安全笔记草稿仅绑定到当前返回栈条目；退出或锁库清栈时立即释放。
            val draftViewModel = koinViewModel<AddSecureNoteDraftViewModel>(
                viewModelStoreOwner = backStackEntry
            )
            AddSecureNoteScreen(
                onNavigateBack = { navController.popBackStack() },
                onLoading = { viewModel.setAttachmentLoading(it) },
                draftViewModel = draftViewModel,
            )
        }
        composable<RouteTotpScanner> {
            ScannerScreen(navController = navController)
        }
        composable<RouteAddTotpManual> {
            ManualTotpScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable<RouteEditTotp> { backStackEntry ->
            val route = backStackEntry.toRoute<RouteEditTotp>()
            ManualTotpScreen(
                onNavigateBack = { navController.popBackStack() },
                entryId = route.id,
            )
        }
        // 设置子页面
        composable<RouteAppearanceSettings>(
            enterTransition = { slideInHorizontally(animationSpec = tween(200)) { it } },
            exitTransition = { slideOutHorizontally(animationSpec = tween(200)) { -it } },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(200)) { -it } },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(200)) { it } }
        ) {
            AppearanceSettingsScreen(navController = navController)
        }
        composable<RouteSecuritySettings> {
            SecuritySettingsScreen(navController = navController)
        }
        composable<RouteDataSettings> {
            DataSettingsScreen(navController = navController)
        }
        composable<RouteOtherSettings> {
            OtherSettingsScreen(navController = navController)
        }
        composable<RouteQA> {
            QAScreen(navController = navController)
        }
    }
}
