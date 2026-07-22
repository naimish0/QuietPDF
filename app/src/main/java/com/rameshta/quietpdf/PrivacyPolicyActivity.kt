package com.rameshta.quietpdf

import android.os.Bundle
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import com.rameshta.quietpdf.ui.theme.QuietPDFTheme
import com.rameshta.quietpdf.ui.theme.AppThemeMode
import com.rameshta.quietpdf.ui.theme.AppThemePreferences

class PrivacyPolicyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val systemFallback = if (
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        ) AppThemeMode.Dark else AppThemeMode.Light
        val themeMode = AppThemePreferences(this).read(systemFallback)
        val systemBarStyle = if (themeMode == AppThemeMode.Dark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = systemBarStyle, navigationBarStyle = systemBarStyle)
        setContent {
            QuietPDFTheme(darkTheme = themeMode == AppThemeMode.Dark) {
                PrivacyPolicyScreen(onClose = ::finish)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun PrivacyPolicyScreen(onClose: () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    val backDescription = androidx.compose.ui.res.stringResource(R.string.settings_back)

    BackHandler {
        webView?.takeIf(WebView::canGoBack)?.goBack() ?: onClose()
    }
    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = androidx.compose.ui.res.stringResource(
                            R.string.settings_privacy_policy,
                        ),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Text(
                            text = "←",
                            modifier = Modifier.semantics {
                                contentDescription = backDescription
                            },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = false
                    settings.domStorageEnabled = false
                    settings.allowContentAccess = false
                    settings.allowFileAccess = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    webViewClient = WebViewClient()
                    loadUrl(PRIVACY_POLICY_ASSET_URL)
                    webView = this
                }
            },
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
    }
}

private const val PRIVACY_POLICY_ASSET_URL = "file:///android_asset/index.html"
