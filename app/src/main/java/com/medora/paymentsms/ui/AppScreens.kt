package com.medora.paymentsms.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medora.paymentsms.data.db.PaymentEventEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class TabItem(val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabItem("Connect", Icons.Filled.Link),
    TabItem("Permissions", Icons.Filled.Lock),
    TabItem("Status", Icons.Filled.CheckCircle),
    TabItem("Events", Icons.Filled.List),
    TabItem("Help", Icons.Filled.Info),
    TabItem("Disconnect", Icons.Filled.PowerSettingsNew),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: MainViewModel, onRequestPermission: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Medora Payment Capture") }) },
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        Surface(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> ConnectScreen(ui, vm)
                1 -> PermissionsScreen(ui, onRequestPermission)
                2 -> StatusScreen(ui, vm)
                3 -> EventsScreen(ui)
                4 -> TroubleshootingScreen(ui, vm)
                5 -> DisconnectScreen(ui, vm)
            }
        }
    }
}

@Composable
private fun ConnectScreen(ui: UiState, vm: MainViewModel) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Connect Device", style = MaterialTheme.typography.headlineSmall)
        Text("Enter the one-time 8-digit code from the admin dashboard. It expires after 5 minutes.")
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter { c -> c.isDigit() }.take(8) },
            label = { Text("Pairing code") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(120) },
            label = { Text("Device name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { vm.pair(code, name.ifBlank { "Pilot handset" }) },
            enabled = !ui.busy && !ui.paired,
        ) {
            Text(if (ui.busy) "Pairing..." else "Pair device")
        }
        ui.pairMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        if (ui.paired) {
            Text("Connected as ${ui.deviceName ?: "unknown device"}", fontWeight = FontWeight.Bold)
            Text("The token is stored encrypted. Revoke it any time from the dashboard.")
        }
    }
}

@Composable
private fun PermissionsScreen(ui: UiState, onRequestPermission: () -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Permissions & Privacy", style = MaterialTheme.typography.headlineSmall)
        Text("This pilot requests RECEIVE_SMS only, so it can see newly arriving payment messages.")
        Text("It does NOT request READ_SMS, read your inbox, read contacts, or collect OTPs and personal conversations.")
        Text("Only allow-listed senders are inspected. Raw message bodies are never stored or logged; only masked metadata is queued.")
        Text(
            if (ui.smsPermissionGranted) "SMS permission: granted" else "SMS permission: not granted — listening is disabled.",
            fontWeight = FontWeight.Bold,
        )
        if (!ui.smsPermissionGranted) {
            Button(onClick = onRequestPermission) { Text("Grant SMS permission") }
        }
    }
}

@Composable
private fun StatusScreen(ui: UiState, vm: MainViewModel) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Listening Status", style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Listening for payment SMS", modifier = Modifier.weight(1f))
            Switch(
                checked = ui.listening,
                onCheckedChange = { vm.setListening(it) },
                enabled = ui.paired && ui.smsPermissionGranted && !ui.authRevoked,
            )
        }
        Text("Device: ${ui.deviceName ?: "not paired"}")
        Text("Sync health: ${ui.healthLabel}")
        Text("Pending events: ${ui.pendingCount}    Sent: ${ui.sentCount}")
        Text(
            if (ui.lastSyncAt > 0) "Last successful sync: ${formatTime(ui.lastSyncAt)}"
            else "Last successful sync: never",
        )
        Text(
            if (ui.lastHeartbeatAt > 0) "Last heartbeat: ${formatTime(ui.lastHeartbeatAt)}"
            else "Last heartbeat: never",
            style = MaterialTheme.typography.bodySmall,
        )
        ui.lastError?.let { Text("Notice: $it", color = MaterialTheme.colorScheme.error) }
        Button(onClick = { vm.syncNow() }, enabled = ui.paired && !ui.authRevoked) {
            Text("Sync now")
        }
        Text("Upload requires a network connection and is retried automatically with backoff.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EventsScreen(ui: UiState) {
    Column(Modifier.padding(16.dp)) {
        Text("Events (redacted)", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        if (ui.events.isEmpty()) {
            Text("No events captured yet.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(ui.events, key = { it.eventUuid }) { event -> EventRow(event) }
            }
        }
    }
}

@Composable
private fun EventRow(event: PaymentEventEntity) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("${"%.2f".format(Locale.getDefault(), event.amountMinor / 100.0)} EGP", fontWeight = FontWeight.Bold)
            Text("From: ${event.senderHint}")
            Text("Status: ${event.status}    Ref: ${event.providerReference ?: "—"}")
            Text("Received: ${formatTime(event.occurredAtMs)}")
        }
    }
}

@Composable
private fun TroubleshootingScreen(ui: UiState, vm: MainViewModel) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Troubleshooting", style = MaterialTheme.typography.headlineSmall)
        Checklist("Device paired", ui.paired)
        Checklist("SMS permission granted", ui.smsPermissionGranted)
        Checklist("Listening enabled", ui.listening)
        Checklist("No revocation notice", !ui.authRevoked)
        Text(
            "If messages arrive while offline, they stay queued locally and are sent once. " +
                "Battery savers and manufacturer ROMs may delay delivery; disable battery " +
                "optimisation for this app on the pilot handset.",
        )
        ui.lastError?.let { Text("Last issue: $it", color = MaterialTheme.colorScheme.error) }
        Button(onClick = { vm.syncNow(); vm.refresh() }, enabled = ui.paired && !ui.authRevoked) {
            Text("Retry sync")
        }
    }
}

@Composable
private fun Checklist(label: String, ok: Boolean) {
    Text("${if (ok) "✓" else "✗"}  $label", color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
}

@Composable
private fun DisconnectScreen(ui: UiState, vm: MainViewModel) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Disconnect", style = MaterialTheme.typography.headlineSmall)
        Text("This clears the encrypted device token, stops listening and stops background sync.")
        Text("Local event evidence is kept for review; nothing is silently deleted.")
        Button(
            onClick = { vm.disconnect() },
            enabled = ui.paired,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
            Text("Disconnect this device")
        }
        if (!ui.paired) Text("This device is not paired.")
    }
}

private fun formatTime(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))
