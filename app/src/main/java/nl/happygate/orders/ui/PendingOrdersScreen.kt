package nl.happygate.orders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.happygate.orders.data.Order

@Composable
fun PendingOrdersScreen(viewModel: OrdersViewModel, onOpen: (Order) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now = rememberNow()
    var acceptFor by remember { mutableStateOf<Order?>(null) }
    var rejectFor by remember { mutableStateOf<Order?>(null) }

    Column(Modifier.fillMaxSize()) {
        ConnectionBar(state)
        if (state.pending.isEmpty()) {
            EmptyState("No new orders", "New orders appear here automatically")
        } else {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.pending, key = { it.id }) { order ->
                    val busy = order.id in state.busyOrderIds
                    OrderCard(
                        order = order,
                        headerRight = {
                            Text(
                                countdown(order.expiresAt, now),
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium,
                            )
                        },
                        onClick = { onOpen(order) },
                        actions = {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { viewModel.stopAlert(); acceptFor = order }, enabled = !busy,
                                    modifier = Modifier.weight(1f).height(50.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Green),
                                ) { Text("Accept", style = MaterialTheme.typography.titleMedium) }
                                OutlinedButton(
                                    onClick = { viewModel.stopAlert(); rejectFor = order }, enabled = !busy,
                                    modifier = Modifier.weight(1f).height(50.dp),
                                ) { Text("Reject", style = MaterialTheme.typography.titleMedium) }
                            }
                        },
                    )
                }
            }
        }
    }

    acceptFor?.let { order ->
        AcceptDialog(order, onDismiss = { acceptFor = null }, onAccept = { minutes -> viewModel.accept(order, minutes); acceptFor = null })
    }
    rejectFor?.let { order ->
        RejectDialog(order, onDismiss = { rejectFor = null }, onReject = { reason -> viewModel.reject(order, reason); rejectFor = null })
    }
}

@Composable
fun ConnectionBar(state: UiState) {
    val (text, color) = when (state.connection) {
        Connection.ONLINE -> null to Green
        Connection.CONNECTING -> "Connecting…" to Grey
        Connection.OFFLINE -> "Offline: ${state.lastError ?: "no connection"}. Showing last known orders." to MaterialTheme.colorScheme.error
    }
    if (text != null) {
        Text(
            text, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().background(color).padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
