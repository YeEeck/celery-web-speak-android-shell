package com.yeck.celerywebspeak.android.shell.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

sealed interface ValidationResult {
    data object Success : ValidationResult
    data class Failure(val message: String, val canForce: Boolean) : ValidationResult
}

@Composable
fun SetupScreen(
    savedUrl: String,
    onEnter: (String) -> Unit
) {
    var url by remember { mutableStateOf(savedUrl) }
    var status by remember { mutableStateOf<ValidationResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var lastFailedUrl by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Celery Web Speak",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Android 客户端",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "连接服务器",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = url,
            onValueChange = {
                url = it
                status = null
                lastFailedUrl = ""
            },
            label = { Text("服务器地址") },
            placeholder = { Text("https://voice.example.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(
                onGo = { if (!busy) validateAndEnter(url, false, scope) { s, r -> status = r; busy = s; lastFailedUrl = if (r is ValidationResult.Failure) url else ""; if (r is ValidationResult.Success) onEnter(normalizeUrl(url)) } }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            enabled = !busy
        )

        // Status message
        when (val result = status) {
            is ValidationResult.Failure -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = result.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            else -> {}
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            val showForce = (status as? ValidationResult.Failure)?.canForce == true
                    && url.trim() == lastFailedUrl
            if (showForce) {
                TextButton(
                    onClick = {
                        scope.launch { onEnter(normalizeUrl(url)) }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("仍然进入")
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Button(
                onClick = {
                    if (busy) return@Button
                    val currentUrl = url.trim()
                    if (showForce && currentUrl == lastFailedUrl) {
                        onEnter(normalizeUrl(currentUrl))
                        return@Button
                    }
                    scope.launch {
                        busy = true
                        status = null
                        val result = validateServer(currentUrl)
                        status = result
                        lastFailedUrl = if (result is ValidationResult.Failure) currentUrl else ""
                        busy = false
                        if (result is ValidationResult.Success) {
                            onEnter(normalizeUrl(currentUrl))
                        }
                    }
                },
                enabled = !busy
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp).width(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("正在验证")
                } else {
                    Text("验证并进入")
                }
            }
        }
    }
}

private fun validateAndEnter(
    url: String,
    force: Boolean,
    scope: kotlinx.coroutines.CoroutineScope,
    onUpdate: (Boolean, ValidationResult?) -> Unit
) {
    scope.launch {
        onUpdate(true, null)
        val result = validateServer(url)
        onUpdate(false, result)
    }
}

private fun normalizeUrl(input: String): String {
    val trimmed = input.trim()
    return try {
        val parsed = URL(trimmed)
        "${parsed.protocol}://${parsed.authority}"
    } catch (_: Exception) {
        trimmed
    }
}

private suspend fun validateServer(serverUrl: String): ValidationResult {
    val trimmed = serverUrl.trim()
    if (trimmed.isEmpty()) {
        return ValidationResult.Failure("请输入服务器地址", canForce = false)
    }

    val origin = try {
        val parsed = URL(trimmed)
        if (parsed.protocol != "http" && parsed.protocol != "https") {
            return ValidationResult.Failure("服务器地址必须使用 http:// 或 https://", canForce = false)
        }
        "${parsed.protocol}://${parsed.authority}"
    } catch (_: Exception) {
        return ValidationResult.Failure("服务器地址格式无效", canForce = false)
    }

    return withContext(Dispatchers.IO) {
        try {
            val connection = URL("$origin/api/health").openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.instanceFollowRedirects = false

            val code = connection.responseCode
            if (code != 200) {
                return@withContext ValidationResult.Failure(
                    "健康检查返回 HTTP $code",
                    canForce = true
                )
            }

            val body = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            if (body.contains("\"status\"") && body.contains("\"ok\"")) {
                ValidationResult.Success
            } else {
                ValidationResult.Failure("健康检查响应格式不正确", canForce = true)
            }
        } catch (e: java.net.SocketTimeoutException) {
            ValidationResult.Failure("连接服务器超时", canForce = true)
        } catch (_: Exception) {
            ValidationResult.Failure("无法连接服务器", canForce = true)
        }
    }
}
