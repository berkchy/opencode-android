package dev.opencode.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.opencode.android.ui.ConnectViewModel

@Composable
fun ConnectScreen(
    onConnected: () -> Unit,
) {
    val vm: ConnectViewModel = viewModel()
    val testing by vm.testing.collectAsState()
    val status by vm.status.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(
                    Icons.Filled.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Text(
            text = "opencode",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = "Açık kaynak kodlama ajanı — sunucuna bağlan",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 28.dp),
        )

        OutlinedTextField(
            value = vm.serverUrl.collectAsState().value,
            onValueChange = { vm.serverUrl.value = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Sunucu adresi") },
            placeholder = { Text("http://192.168.1.10:3587") },
            leadingIcon = { Icon(Icons.Filled.Public, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(
            value = vm.username.collectAsState().value,
            onValueChange = { vm.username.value = it },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            label = { Text("Kullanıcı adı") },
            placeholder = { Text("opencode") },
            singleLine = true,
        )
        OutlinedTextField(
            value = vm.password.collectAsState().value,
            onValueChange = { vm.password.value = it },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            label = { Text("Şifre (varsa)") },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
        )

        Button(
            onClick = { vm.testOrSave { onConnected() } },
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            enabled = !testing,
        ) {
            if (testing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Bağlan")
            }
        }

        status?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = if (it.contains("kuruldu", ignoreCase = true)) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Text(
            text = "Sunucuda:  opencode serve --port 3587\n(şifreli: OPENCODE_SERVER_PASSWORD=... opencode serve)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}