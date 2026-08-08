package dev.opencode.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.opencode.android.data.prefs.EmbeddedDefaults
import dev.opencode.android.server.OpenCodeServerManager
import dev.opencode.android.ui.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
) {
    val vm: SettingsViewModel = viewModel()
    val connection by vm.connection.collectAsState()
    val models by vm.models.collectAsState()
    val status by vm.status.collectAsState()
    val embedded by vm.embedded.collectAsState()
    val serverStatus by vm.serverStatus.collectAsState()

    var embeddedOn by remember { mutableStateOf(embedded.enabled) }
    var embeddedModel by remember { mutableStateOf(embedded.model) }
    var embeddedApiKey by remember { mutableStateOf(embedded.apiKey) }
    var showAddProvider by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Bağlantı")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Cihaz içi sunucu (gömülü)", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "opencode binary'si cihazda çalışır. Ücretsiz Zen modelleri varsayılan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = embeddedOn, onCheckedChange = { embeddedOn = it })
            }

            statusText(serverStatus)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            if (embeddedOn) {
                OutlinedTextField(
                    value = embeddedModel,
                    onValueChange = { embeddedModel = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Model") },
                    singleLine = true,
                )
                Text(
                    "Ücretsiz Zen modelleri (zorunlu anahtar yok):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EmbeddedDefaults.FREE_MODELS.forEach { m ->
                        OutlinedButton(
                            onClick = { embeddedModel = m },
                            modifier = Modifier.padding(bottom = 0.dp),
                        ) {
                            Text(m.removePrefix("opencode/"))
                        }
                    }
                }
                OutlinedTextField(
                    value = embeddedApiKey,
                    onValueChange = { embeddedApiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Zen API anahtarı (isteğe bağlı)") },
                    placeholder = { Text("oc_...") },
                    singleLine = true,
                )
                Button(
                    onClick = { vm.saveEmbedded(embeddedOn, embeddedModel, embeddedApiKey) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Uygula ve yeniden başlat")
                }
                OutlinedButton(onClick = { vm.stopEmbeddedServer() }) {
                    Text("Sunucuyu durdur")
                }
            } else {
                OutlinedButton(
                    onClick = { vm.saveEmbedded(false, embeddedModel, embeddedApiKey, restart = false) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Uzak sunucuya bağlan")
                }
            }

            HorizontalDivider()

            Text(
                text = connection.serverUrl,
                style = MaterialTheme.typography.bodyLarge,
            )
            OutlinedButton(
                onClick = vm::logout,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Kayıtlı sunucu bağlantısını sil")
            }

            HorizontalDivider()

            SectionTitle("Modeller (sunucudan)")
            if (models.isEmpty()) {
                Text(
                    "Sunucu tarafında model/provider yapılandırılmamış görünüyor.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                models.take(8).forEach { model ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(model.id, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            model.providerID ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (models.size > 8) {
                    Text(
                        "+${models.size - 8} daha",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            SectionTitle("Model / provider ekle")
            Text(
                "OpenAI uyumlu (OpenAI, OpenRouter, Ollama…) özel provider'ı sunucu config'ine ekler.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { showAddProvider = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.AddLink, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Özel provider ekle")
            }

            status?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }

    if (showAddProvider) {
        AddProviderDialog(
            onApply = vm::addProvider,
            onDismiss = { showAddProvider = false },
        )
    }
}

@Composable
private fun statusText(status: OpenCodeServerManager.Status): String? = when (status) {
    is OpenCodeServerManager.Status.Running -> "Gömülü sunucu çalışıyor: 127.0.0.1:${status.port}"
    is OpenCodeServerManager.Status.Starting -> "Gömülü sunucu başlatılıyor…"
    is OpenCodeServerManager.Status.Stopped -> "Gömülü sunucu durdu"
    is OpenCodeServerManager.Status.Failed -> "Hata: ${status.message}"
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun AddProviderDialog(
    onApply: (providerId: String, name: String, baseUrl: String, apiKey: String, modelsText: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var providerId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var modelsText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("OpenAI-uyumlu provider") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = providerId,
                    onValueChange = { providerId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Provider ID") },
                    placeholder = { Text("myprovider") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Görünen ad") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.openai.com/v1") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = modelsText,
                    onValueChange = { modelsText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Modeller (virgülle ayır)") },
                    placeholder = { Text("gpt-4o, gpt-4o-mini") },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(providerId, name, baseUrl, apiKey, modelsText) },
                enabled = providerId.isNotBlank() && name.isNotBlank() && baseUrl.isNotBlank(),
            ) {
                Text("Ekle")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        },
    )
}