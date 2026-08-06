package com.turisla.hellopocket.ui.feature.settings

import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.turisla.hellopocket.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(navController: NavController) {
    LocalHtmlDocumentScreen(
        navController = navController,
        title = stringResource(id = R.string.privacy_policy),
        assetName = "privacy_policy.html",
    )
}

@Composable
fun OpenSourceLicensesScreen(navController: NavController) {
    LocalHtmlDocumentScreen(
        navController = navController,
        title = stringResource(id = R.string.open_source_licenses),
        assetName = "third_party_notices.html",
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalHtmlDocumentScreen(
    navController: NavController,
    title: String,
    assetName: String,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
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
        LocalHtmlDocumentContent(
            assetName = assetName,
            modifier = Modifier.padding(paddingValues),
        )
    }
}

@Composable
private fun LocalHtmlDocumentContent(
    assetName: String,
    modifier: Modifier = Modifier,
) {
    AndroidView(factory = {
        WebView(it).apply {
            // 本地静态文档不需要脚本能力，关闭以缩小 WebView 攻击面。
            settings.javaScriptEnabled = false
            settings.allowContentAccess = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    if (request.url.scheme == "file" &&
                        request.url.path?.startsWith("/android_asset/") == true
                    ) {
                        return false
                    }
                    if (request.url.scheme == "mailto") {
                        // 邮件链接是明确的用户操作，只交给本地邮件客户端，不给 WebView 网络能力。
                        runCatching {
                            view.context.startActivity(
                                Intent(Intent.ACTION_SENDTO, request.url),
                            )
                        }
                    }
                    return true
                }
            }
            loadUrl("file:///android_asset/$assetName")
        }
    }, modifier = modifier)
}
