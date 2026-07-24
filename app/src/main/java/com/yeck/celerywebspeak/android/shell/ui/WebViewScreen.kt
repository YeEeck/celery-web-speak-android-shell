package com.yeck.celerywebspeak.android.shell.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    url: String,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    var webViewKey by remember { mutableStateOf(0) }

    BackHandler {
        val wv = webView
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackPressTime < 2000) {
                onExit()
            } else {
                lastBackPressTime = now
                Toast.makeText(context, "再按一次退出", Toast.LENGTH_SHORT).show()
            }
        }
    }

    key(webViewKey) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    // Keep Chromium's percentage-height viewport tied to the AndroidView bounds.
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.setSupportZoom(false)
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            val requestUrl = request.url
                            val baseUrl = Uri.parse(url)
                            return if (requestUrl.host != baseUrl.host) {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, requestUrl)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    ctx.startActivity(intent)
                                } catch (_: Exception) {}
                                true
                            } else {
                                false
                            }
                        }

                        override fun onRenderProcessGone(
                            view: WebView,
                            detail: RenderProcessGoneDetail
                        ): Boolean {
                            webViewKey++
                            return true
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest) {
                            request.grant(request.resources)
                        }
                    }

                    loadUrl(url)
                    webView = this
                }
            },
            onRelease = { wv ->
                wv.stopLoading()
                wv.destroy()
                webView = null
            }
        )
    }
}
