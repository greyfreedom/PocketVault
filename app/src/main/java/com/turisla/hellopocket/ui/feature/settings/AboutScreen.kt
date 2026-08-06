package com.turisla.hellopocket.ui.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.turisla.hellopocket.R
import com.turisla.hellopocket.router.RouteOpenSourceLicenses
import com.turisla.hellopocket.router.RoutePrivacyPolicy
import com.turisla.hellopocket.router.RouteQA
import com.turisla.hellopocket.ui.feature.common.AppLogo
import com.turisla.hellopocket.ui.theme.AppSpacing
import com.turisla.hellopocket.utils.AppConstants
import com.turisla.hellopocket.utils.AppUtils
import com.turisla.hellopocket.utils.ext.getAppVersionCode
import com.turisla.hellopocket.utils.ext.getAppVersionName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navController: NavController) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.about_us),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(AppSpacing.xl))
            AppLogo(size = 88.dp)
            Spacer(modifier = Modifier.height(AppSpacing.md))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(
                    R.string.version_format,
                    context.getAppVersionName(),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(AppSpacing.xxl))

            SettingsListItem(
                title = stringResource(R.string.contact_us),
                description = AppConstants.CONTACT_EMAIL,
                icon = Icons.Outlined.Email,
                onClick = { openFeedbackEmail(context) },
                trailingContent = {
                    IconButton(onClick = { copyContactEmail(context) }) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(R.string.copy),
                        )
                    }
                },
            )
            SettingsDivider()
            SettingsGroup {
                SettingsListItem(
                    title = stringResource(R.string.privacy_policy),
                    icon = Icons.Outlined.Policy,
                    onClick = { navController.navigate(RoutePrivacyPolicy) },
                    inGroup = true,
                )
                SettingsGroupDivider()
                SettingsListItem(
                    title = stringResource(R.string.open_source_licenses),
                    icon = Icons.Outlined.Code,
                    onClick = { navController.navigate(RouteOpenSourceLicenses) },
                    inGroup = true,
                )
                SettingsGroupDivider()
                SettingsListItem(
                    title = stringResource(R.string.qa_title),
                    icon = Icons.AutoMirrored.Outlined.HelpOutline,
                    onClick = { navController.navigate(RouteQA) },
                    inGroup = true,
                )
            }
            SettingsDivider()
        }
    }
}

private fun copyContactEmail(context: Context) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("email", AppConstants.CONTACT_EMAIL))
    Toast.makeText(context, R.string.email_copied, Toast.LENGTH_SHORT).show()
}

private fun openFeedbackEmail(context: Context) {
    val body = "\n\n--------------------------------\n" +
        "App Version: ${context.getAppVersionName()} (${context.getAppVersionCode()})\n" +
        "Device: ${AppUtils.getDeviceInfo()}\n" +
        "--------------------------------"
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = "mailto:".toUri()
        putExtra(Intent.EXTRA_EMAIL, arrayOf(AppConstants.CONTACT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, "PocketVault Feedback")
        putExtra(Intent.EXTRA_TEXT, body)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, R.string.no_email_client, Toast.LENGTH_SHORT).show()
    }
}
