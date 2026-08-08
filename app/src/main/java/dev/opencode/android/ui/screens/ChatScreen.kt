package dev.opencode.android.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.opencode.android.OpenCodeApp
import dev.opencode.android.ui.Attachment
import dev.opencode.android.ui.ChatViewModel
import dev.opencode.android.ui.components.MessageBubble
import java.io.ByteArrayOutputStream
import java.io.IOException

private const val MAX_ATTACHMENT_BYTES = 512 * 1024

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    sessionId: String,
    onBack: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as OpenCodeApp
    val context = LocalContext.current
    val vm: ChatViewModel = viewModel(
        key = "chat_$sessionId",
        factory = ChatViewModel.Factory(app, sessionId),
    )
    val messages by vm.messages.collectAsState()

    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val title by vm.title.collectAsState()
    val activeModel by vm.model.collectAsState()
    val models by vm.models.collectAsState()
    val question by vm.question.collectAsState()

    var input by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf<List<Attachment>>(emptyList()) }
    val listState = rememberLazyListState()

    val lastMessageId = messages.lastOrNull()?.id

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { readFile(context, uri) }
            .onSuccess { file ->
                attachments = attachments + file
            }
    }

    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex.coerceAtLeast(0))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title ?: "Sohbet", maxLines = 1)
                        Text(
                            text = activeModel ?: "model yükleniyor",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    ModelMenu(
                        current = activeModel,
                        models = models,
                        onSelect = vm::switchModel,
                    )
                    IconButton(onClick = { attachments = emptyList() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Eklentileri temizle", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
            )
        },
        bottomBar = {
            ErrorBanner(error = error, onDismiss = vm::dismissError)
            Attachments(
                attachments = attachments,
                onRemove = { removed -> attachments = attachments - removed },
            )
            Composer(
                input = input,
                onInputChange = { input = it },
                busy = busy,
                onAttach = { filePicker.launch(arrayOf("*/*")) },
                onSend = { text ->
                    vm.send(text, attachments)
                    input = ""
                    attachments = emptyList()
                },
                onStop = vm::abort,
            )
        },
    ) { innerPadding ->
        when {
            messages.isEmpty() && !busy -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Henüz mesaj yok.\nBir görev söyle, opencode hall etsin.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    items(messages, key = { it.id }) { entity ->
                        MessageBubble(
                            entity = entity,
                            isStreaming = busy && entity.id == lastMessageId && entity.role != "user",
                            modifier = Modifier.animateItem(),
                        )
                    }

                    if (busy && messages.isNotEmpty() && messages.last().role == "user") {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.Start,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    question?.let { q ->
        QuestionDialog(
            question = q,
            onAnswer = { answer -> vm.replyQuestion(q, answer) },
            onDismiss = { vm.replyQuestion(q, "no") },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelMenu(
    current: String?,
    models: List<dev.opencode.android.data.network.Model>,
    onSelect: (dev.opencode.android.data.network.Model) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
        ) {
            Text("Model")
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(model.id, maxLines = 1)
                            model.name?.let {
                                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    },
                    trailingIcon = if (model.id == current) {
                        { Text("✓") }
                    } else {
                        null
                    },
                    onClick = {
                        onSelect(model)
                        expanded = false
                    },
                )
            }
            if (models.isEmpty()) {
                Text(
                    "Modeller listelenemedi.\nSunucuda opencode config ile provider ekleyebilirsin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun Composer(
    input: String,
    onInputChange: (String) -> Unit,
    busy: Boolean,
    onAttach: () -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onAttach) {
                Icon(
                    Icons.Filled.AttachFile,
                    contentDescription = "Dosya ekle",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Mesaj yaz…") },
                maxLines = 4,
                enabled = !busy,
            )
            if (busy) {
                Button(onClick = onStop) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                }
            } else {
                Button(
                    onClick = { onSend(input) },
                    enabled = input.isNotBlank(),
                ) {
                    Icon(Icons.Filled.Send, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun Attachments(
    attachments: List<Attachment>,
    onRemove: (Attachment) -> Unit,
) {
    if (attachments.isEmpty()) return
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            attachments.forEach { a ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Filled.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = a.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                    IconButton(onClick = { onRemove(a) }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Kaldır",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    error: String?,
    onDismiss: () -> Unit,
) {
    if (error == null) return
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = error, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss) { Text("Tamam") }
        }
    }
}

@Composable
private fun QuestionDialog(
    question: dev.opencode.android.data.network.Question,
    onAnswer: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(question.type?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "İzin isteği") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Devam etmek için bir seçenek seç:")
                question.options.forEach { option ->
                    Button(
                        onClick = { onAnswer(option.title) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            Text(option.title, style = MaterialTheme.typography.labelLarge)
                            option.description?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        },
    )
}

private fun readFile(context: android.content.Context, uri: Uri): Attachment {
    val resolver = context.contentResolver
    val name = resolver.getDisplayName(uri)
    val bytes = resolver.openInputStream(uri)?.use { input ->
        val buffer = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_ATTACHMENT_BYTES) throw IOException("Dosya çok büyük (maks 512 KB)")
            buffer.write(buf, 0, n)
        }
        buffer.toByteArray()
    } ?: throw IOException("Dosya okunamadı")

    val text = String(bytes, Charsets.UTF_8)
    return Attachment(name = name ?: "dosya", content = text)
}

private fun android.content.ContentResolver.getDisplayName(uri: Uri): String? {
    return try {
        query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        }
    } catch (_: Exception) {
        uri.lastPathSegment
    }
}