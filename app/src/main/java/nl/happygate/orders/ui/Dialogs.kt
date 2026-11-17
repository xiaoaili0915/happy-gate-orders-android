package nl.happygate.orders.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import nl.happygate.orders.data.Order
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val presets = listOf(15, 30, 45)

@Composable
fun AcceptDialog(order: Order, onDismiss: () -> Unit, onAccept: (Int) -> Unit) {
    var minutes by rememberSaveable { mutableIntStateOf(30) }
    var custom by rememberSaveable { mutableStateOf("") }
    val customMinutes = custom.toIntOrNull()
    val chosen = customMinutes ?: minutes
    val valid = chosen in 1..240
    val readyAt = LocalTime.now().plusMinutes(chosen.toLong().coerceIn(0, 240)).format(DateTimeFormatter.ofPattern("HH:mm"))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Accept order #${order.id}") },
        text = {
            Column {
                Text("How long until it is ready?")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { m ->
                        FilterChip(
                            selected = customMinutes == null && minutes == m,
                            onClick = { minutes = m; custom = "" },
                            label = { Text("$m min", style = MaterialTheme.typography.titleMedium) },
                            modifier = Modifier.height(44.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = custom, onValueChange = { custom = it.filter(Char::isDigit).take(3) },
                    label = { Text("Custom minutes") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = custom.isNotEmpty() && !valid,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Customer will be told: ready at about $readyAt", fontWeight = FontWeight.SemiBold)
            }
        },
        confirmButton = {
            Button(onClick = { onAccept(chosen) }, enabled = valid, colors = ButtonDefaults.buttonColors(containerColor = Green)) {
                Text("Accept · $chosen min")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val quickReasons = listOf("Too busy right now", "Item sold out", "Closing soon", "Cannot reach customer")

@Composable
fun RejectDialog(order: Order, onDismiss: () -> Unit, onReject: (String) -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reject order #${order.id}?") },
        text = {
            Column {
                Text("${order.customerName} will be emailed that the order was declined.")
                Spacer(Modifier.height(12.dp))
                quickReasons.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { r ->
                            FilterChip(selected = reason == r, onClick = { reason = r }, label = { Text(r) }, modifier = Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason, onValueChange = { reason = it }, label = { Text("Reason (internal)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onReject(reason) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("Reject order")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep") } },
    )
}
