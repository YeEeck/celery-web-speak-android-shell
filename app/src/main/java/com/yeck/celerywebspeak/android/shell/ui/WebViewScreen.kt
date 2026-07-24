package com.yeck.celerywebspeak.android.shell.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

/** Matches the web app's --rail dark color so the system-bars inset area blends in. */
private val RailColor = Color(0xFF1E1F22)

/**
 * Marker injected into the WebView so the web app can detect that it is
 * running inside the Android shell and report itself as the "android" client.
 * Exposed via addJavascriptInterface so it exists before any page script
 * runs, avoiding the injection races that evaluateJavascript is subject to.
 */
private class CeleryShellBridge(private val context: Context) {
    @JavascriptInterface
    fun platform(): String = "android"

    @JavascriptInterface
    fun version(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (_: Exception) {
        ""
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    url: String,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    var webViewKey by remember { mutableStateOf(0) }
    val imeBottom = WindowInsets.ime.getBottom(density)

    // When keyboard starts appearing, scroll focused input into view concurrently
    // with the keyboard animation (50ms is just enough for the first layout frame).
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0) {
            delay(50)
            webView?.evaluateJavascript(
                """(function(){
                    var el=document.activeElement;
                    if(el&&(el.tagName==='INPUT'||el.tagName==='TEXTAREA'||el.isContentEditable)){
                        el.scrollIntoView({block:'center',behavior:'smooth'});
                    }
                })()""".trimIndent(),
                null
            )
        }
    }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RailColor)
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding()
    ) {
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
                        // Same dark base as the inset area — no white flash while loading
                        setBackgroundColor(RailColor.toArgb())
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.setSupportZoom(false)
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true

                        addJavascriptInterface(CeleryShellBridge(ctx), "celeryShell")

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
                    CookieManager.getInstance().flush()
                    wv.stopLoading()
                    wv.destroy()
                    webView = null
                }
            )
        }
    }
}
