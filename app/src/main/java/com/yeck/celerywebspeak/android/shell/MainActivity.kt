package com.yeck.celerywebspeak.android.shell

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yeck.celerywebspeak.android.shell.ui.AppTheme
import com.yeck.celerywebspeak.android.shell.ui.SetupScreen
import com.yeck.celerywebspeak.android.shell.ui.WebViewScreen

private const val PREFS_NAME = "app_prefs"
private const val KEY_SERVER_URL = "server_url"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.systemBars)
                    ) {
                        var screen by remember { mutableStateOf<Screen>(Screen.CheckingPermission) }
                        var serverUrl by remember { mutableStateOf("") }
                        var resumeCount by remember { mutableIntStateOf(0) }

                        // Observe lifecycle to re-check permission when returning from settings
                        val lifecycleOwner = LocalLifecycleOwner.current
                        DisposableEffect(lifecycleOwner) {
                            val observer = LifecycleEventObserver { _, event ->
                                if (event == Lifecycle.Event.ON_RESUME) {
                                    resumeCount++
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose {
                                lifecycleOwner.lifecycle.removeObserver(observer)
                            }
                        }

                        val permissionLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.RequestPermission()
                        ) { granted ->
                            if (granted) {
                                screen = Screen.Setup
                            } else {
                                screen = Screen.PermissionDenied
                            }
                        }

                        LaunchedEffect(Unit) {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasPermission) {
                                screen = Screen.Setup
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }

                        // Re-check permission on resume (e.g. returning from settings)
                        LaunchedEffect(resumeCount) {
                            if (resumeCount > 0 && screen == Screen.PermissionDenied) {
                                val granted = ContextCompat.checkSelfPermission(
                                    this@MainActivity, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    screen = Screen.Setup
                                }
                            }
                        }

                        when (screen) {
                            Screen.CheckingPermission -> {
                                PermissionCheckScreen()
                            }

                            Screen.PermissionDenied -> {
                                PermissionDeniedScreen(
                                    onGoToSettings = {
                                        val intent = Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", packageName, null)
                                        )
                                        startActivity(intent)
                                    }
                                )
                            }

                            Screen.Setup -> {
                                val savedUrl = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                                    .getString(KEY_SERVER_URL, "") ?: ""
                                SetupScreen(savedUrl = savedUrl) { url ->
                                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                                        .edit()
                                        .putString(KEY_SERVER_URL, url)
                                        .apply()
                                    serverUrl = url
                                    screen = Screen.WebView
                                }
                            }

                            Screen.WebView -> {
                                WebViewScreen(
                                    url = serverUrl,
                                    onExit = { finish() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class Screen {
    CheckingPermission,
    PermissionDenied,
    Setup,
    WebView
}

@androidx.compose.runtime.Composable
private fun PermissionCheckScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "正在检查麦克风权限",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@androidx.compose.runtime.Composable
private fun PermissionDeniedScreen(onGoToSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "需要麦克风权限",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "本应用需要录音权限才能正常使用语音功能。请在系统设置中授予麦克风权限。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onGoToSettings) {
            Text("前往设置")
        }
    }
}
