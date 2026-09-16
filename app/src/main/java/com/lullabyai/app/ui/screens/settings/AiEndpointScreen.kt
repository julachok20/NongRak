@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.BuildConfig
import com.lullabyai.app.data.remote.MedGemmaClient
import com.lullabyai.app.data.settings.AiEndpointConfig
import com.lullabyai.app.data.settings.AiEndpointSettings
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import kotlinx.coroutines.launch

/**
 * Points the app at a self-hosted model — typically one reached over WireGuard.
 *
 * The token is typed here rather than compiled in: a key inside `BuildConfig` ships in every APK
 * and can be read straight out of it, while a key entered here is stored encrypted on this device
 * only. The test button makes a real call and shows what came back, because the chat path itself
 * falls back to Claude silently and would hide a wrong port or a disconnected tunnel.
 */
@Composable
fun AiEndpointScreen(navController: NavController) {
    val isThai = LocalStrings.current.isThai
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var endpoint by remember { mutableStateOf(AiEndpointConfig.endpoint) }
    var token by remember { mutableStateOf(AiEndpointConfig.token) }
    var model by remember { mutableStateOf(AiEndpointConfig.model) }
    var showToken by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var resultOk by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        LullabyTopBar(
            title = if (isThai) "ตั้งค่า AI Endpoint" else "AI endpoint",
            onBack = { navController.popBackStack() },
            navController = navController
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (isThai) "เซิร์ฟเวอร์โมเดลของคุณเอง" else "Your own model server",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (isThai)
                    "ใส่ที่อยู่และคีย์ของเครื่องที่รันโมเดล (รองรับ API แบบ OpenAI: /v1/chat/completions) " +
                        "ค่าเหล่านี้เก็บแบบเข้ารหัสไว้ในเครื่องนี้เท่านั้น ไม่ถูกฝังในไฟล์แอป"
                else
                    "The address and key of the machine running the model (OpenAI-compatible " +
                        "/v1/chat/completions). Stored encrypted on this device only — never baked into the APK.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it; result = null },
                label = { Text(if (isThai) "ที่อยู่เซิร์ฟเวอร์" else "Endpoint") },
                placeholder = { Text("http://10.66.66.1:8000") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it; result = null },
                label = { Text(if (isThai) "API Key / Token (ถ้ามี)" else "API key / token (optional)") },
                singleLine = true,
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showToken = !showToken }) {
                        Icon(
                            if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (isThai) "แสดง/ซ่อนคีย์" else "Show or hide the key"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it; result = null },
                label = { Text(if (isThai) "ชื่อโมเดล" else "Model name") },
                placeholder = { Text("tgi") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            val isHttp = endpoint.trim().startsWith("http://", ignoreCase = true)
            val isPrivate = AiEndpointConfig.isPrivateHost(endpoint)
            if (isHttp && isPrivate) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (isThai) "ที่อยู่นี้เป็นเครือข่ายส่วนตัว (เช่น ผ่าน WireGuard)"
                            else "This is a private address (e.g. through WireGuard)",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (isThai)
                                "ต้องเปิด VPN ไว้ตอนใช้งาน และใช้ได้กับแอปรุ่น debug เท่านั้น " +
                                    "เพราะรุ่นที่ปล่อยจริงบล็อก HTTP ที่ไม่เข้ารหัส"
                            else
                                "Keep the VPN connected while using it. Debug builds only — the release " +
                                    "build blocks unencrypted HTTP.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (isHttp) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        if (isThai)
                            "ที่อยู่นี้เป็น HTTP บนอินเทอร์เน็ตสาธารณะ คีย์และข้อความของผู้ใช้จะถูกส่งแบบไม่เข้ารหัส ควรใช้ https หรือผ่าน VPN"
                        else
                            "This is plain HTTP on the public internet: the key and the user's messages " +
                                "would travel unencrypted. Use https, or keep it inside the VPN.",
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        AiEndpointSettings.save(context, endpoint, token, model)
                        result = if (isThai) "บันทึกแล้ว" else "Saved"
                        resultOk = true
                    },
                    enabled = !testing
                ) { Text(if (isThai) "บันทึก" else "Save") }

                OutlinedButton(
                    onClick = {
                        // Test what is typed now, so a value can be checked before it is trusted.
                        AiEndpointSettings.save(context, endpoint, token, model)
                        testing = true
                        result = null
                        scope.launch {
                            val outcome = MedGemmaClient.testConnection()
                            testing = false
                            outcome
                                .onSuccess {
                                    resultOk = true
                                    result = (if (isThai) "เชื่อมต่อสำเร็จ ตอบกลับ: " else "Connected. Replied: ") +
                                        it.take(120)
                                }
                                .onFailure {
                                    resultOk = false
                                    result = (if (isThai) "เชื่อมต่อไม่สำเร็จ: " else "Failed: ") +
                                        (it.message ?: it::class.simpleName.orEmpty())
                                }
                        }
                    },
                    enabled = !testing && endpoint.isNotBlank()
                ) { Text(if (isThai) "ทดสอบการเชื่อมต่อ" else "Test connection") }

                TextButton(
                    onClick = {
                        AiEndpointSettings.clear(context)
                        endpoint = ""; token = ""; model = ""
                        result = if (isThai) "ล้างค่าแล้ว" else "Cleared"
                        resultOk = true
                    },
                    enabled = !testing
                ) { Text(if (isThai) "ล้างค่า" else "Clear") }
            }

            if (testing) LinearProgressIndicator(Modifier.fillMaxWidth())

            result?.let {
                Text(
                    it,
                    color = if (resultOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            HorizontalDivider()

            Text(
                if (isThai) "สถานะปัจจุบัน" else "Current state",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                buildString {
                    append(if (isThai) "ที่มาของค่า: " else "Source: ")
                    append(
                        when {
                            AiEndpointConfig.isDeviceConfigured ->
                                if (isThai) "ตั้งค่าในเครื่องนี้" else "set on this device"
                            BuildConfig.MEDGEMMA_ENDPOINT.isNotBlank() ->
                                if (isThai) "จากไฟล์บิลด์ (local.properties)" else "from the build (local.properties)"
                            else -> if (isThai) "ยังไม่ได้ตั้งค่า" else "not configured"
                        }
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (isThai)
                    "เมื่อเซิร์ฟเวอร์นี้ตอบไม่ได้ แอปจะกลับไปใช้ผู้ให้บริการเดิมโดยอัตโนมัติ"
                else
                    "If this server cannot answer, the app falls back to the usual provider automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
