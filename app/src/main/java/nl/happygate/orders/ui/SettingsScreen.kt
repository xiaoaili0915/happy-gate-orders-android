package nl.happygate.orders.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: OrdersViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = state.settings ?: return
    var host by rememberSaveable { mutableStateOf(settings.printerHost) }
    var port by rememberSaveable { mutableStateOf(settings.printerPort.toString()) }
    var enabled by rememberSaveable { mutableStateOf(settings.printerEnabled) }
    var testing by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Keep fields in sync if settings load after first composition.
    LaunchedEffect(settings.printerHost, settings.printerPort, settings.printerEnabled) {
        host = settings.printerHost; port = settings.printerPort.toString(); enabled = settings.printerEnabled
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Connection", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(settings.restaurantName.ifBlank { "Restaurant" }, fontWeight = FontWeight.SemiBold)
        Text(settings.baseUrl, color = Grey, style = MaterialTheme.typography.bodySmall)
        Text(
            when (state.connection) {
                Connection.ONLINE -> "Connected"
                Connection.CONNECTING -> "Connecting…"
                Connection.OFFLINE -> "Offline: ${state.lastError}"
            },
            color = if (state.connection == Connection.ONLINE) Green else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { viewModel.logout() }) { Text("Log out") }

        HorizontalDivider(Modifier.padding(vertical = 20.dp))

        Text("Alerts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Ring when a new order arrives while the app is open")
            Switch(checked = settings.alertSound, onCheckedChange = { viewModel.setAlertSound(it) })
        }
        Text(
            "Background alerts come through Android notifications. If they are silent, open the phone's notification " +
                "settings for this app and make sure the \"New orders\" channel is allowed to make sound, and that battery " +
                "optimisation is off for this app.",
            style = MaterialTheme.typography.bodySmall, color = Grey,
        )

        HorizontalDivider(Modifier.padding(vertical = 20.dp))

        Text("Epson printer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Network (LAN/Wi-Fi) Epson TM printers accept tickets on port 9100. The phone must be on the same Wi-Fi as the printer.",
            style = MaterialTheme.typography.bodySmall, color = Grey,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Print ticket when an order is accepted")
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = host, onValueChange = { host = it }, label = { Text("Printer IP") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.weight(2f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = port, onValueChange = { port = it.filter(Char::isDigit).take(5) }, label = { Text("Port") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        val portNum = port.toIntOrNull() ?: 9100
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { viewModel.savePrinter(host, portNum, enabled) }, enabled = !enabled || host.isNotBlank()) { Text("Save") }
            OutlinedButton(
                onClick = {
                    testing = true
                    scope.launch { viewModel.toast(viewModel.testPrint(host.trim(), portNum)); testing = false }
                },
                enabled = host.isNotBlank() && !testing,
            ) { Text(if (testing) "Printing…" else "Test print") }
        }
    }
}
