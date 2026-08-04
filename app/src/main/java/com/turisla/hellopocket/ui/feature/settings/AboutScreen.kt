package com.turisla.hellopocket.ui.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.turisla.hellopocket.R
import com.turisla.hellopocket.router.RoutePrivacyPolicy
import com.turisla.hellopocket.router.RouteOpenSourceLicenses
import com.turisla.hellopocket.router.RouteQA
import com.turisla.hellopocket.utils.ext.getAppVersionName
import com.turisla.hellopocket.utils.ext.getAppVersionCode
import com.turisla.hellopocket.utils.AppUtils
import com.turisla.hellopocket.utils.AppConstants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navController: NavController) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.about_us)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            Image(
                painter = painterResource(id = R.drawable.app_icon),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.version_format,
                    context.getAppVersionName(),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(48.dp))

            HorizontalDivider()

            // 联系方式
            ListItem(
                headlineContent = { Text(stringResource(id = R.string.contact_us)) },
                supportingContent = { Text(AppConstants.CONTACT_EMAIL) },
                trailingContent = {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("email", AppConstants.CONTACT_EMAIL)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, R.string.email_copied, Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(R.string.copy),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                modifier = Modifier.clickable {
                    val subject = "PocketVault Feedback"
                    val body = "\n\n--------------------------------\n" +
                            "App Version: ${context.getAppVersionName()} (${context.getAppVersionCode()})\n" +
                            "Device: ${AppUtils.getDeviceInfo()}\n" +
                            "--------------------------------"

                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:")
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(AppConstants.CONTACT_EMAIL))
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                        putExtra(Intent.EXTRA_TEXT, body)
                    }

                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, R.string.no_email_client, Toast.LENGTH_SHORT).show()
                    }
                }
            )

            HorizontalDivider()

            // 隐私协议
            ListItem(
                headlineContent = { Text(stringResource(id = R.string.privacy_policy)) },
                modifier = Modifier.clickable {
                    navController.navigate(RoutePrivacyPolicy)
                }
            )
            HorizontalDivider()

            // 开源许可说明完全保存在本地资产中，不会发起网络请求。
            ListItem(
                headlineContent = { Text(stringResource(id = R.string.open_source_licenses)) },
                modifier = Modifier.clickable {
                    navController.navigate(RouteOpenSourceLicenses)
                }
            )
            HorizontalDivider()

            // Q&A
            ListItem(
                headlineContent = { Text(stringResource(id = R.string.qa_title)) },
                modifier = Modifier.clickable {
                    navController.navigate(RouteQA)
                }
            )
            HorizontalDivider()
        }
    }
}
