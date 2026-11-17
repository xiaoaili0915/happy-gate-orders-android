package nl.happygate.orders.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.happygate.orders.data.Order
import nl.happygate.orders.data.asLocalDateTime
import nl.happygate.orders.data.asLocalTime
import nl.happygate.orders.data.euro
import nl.happygate.orders.data.statusLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(viewModel: OrdersViewModel, orderId: Int, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val jobs by viewModel.printJobs.collectAsStateWithLifecycle()
    var fetched by remember { mutableStateOf<Order?>(null) }
    // Live lists win (they refresh every 5s); fall back to a one-off fetch for history/notification opens.
    val order = viewModel.findOrder(orderId) ?: fetched
    LaunchedEffect(orderId, state.pending, state.active) {
        if (viewModel.findOrder(orderId) == null) fetched = viewModel.fetchOrder(orderId)
    }
    var showAccept by remember { mutableStateOf(false) }
    var showReject by remember { mutableStateOf(false) }
    val now = rememberNow()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Order #$orderId") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        if (order == null) {
            EmptyState("Loading…", "")
            return@Scaffold
        }
        val busy = order.id in state.busyOrderIds
        val printerOn = state.settings?.printerConfigured == true
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatusChip(order.statusLabel(), when {
                    order.isPending -> Amber
                    order.isActive -> Green
                    order.isRejected -> MaterialTheme.colorScheme.error
                    else -> Grey
                })
                if (order.isPending) Text(countdown(order.expiresAt, now), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(12.dp))
            Text(order.customerName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(order.customerPhone, style = MaterialTheme.typography.bodyLarge)
            Text(order.customerEmail, style = MaterialTheme.typography.bodyMedium, color = Grey)
            Spacer(Modifier.height(8.dp))
            Text("Ordered ${order.createdAt.asLocalDateTime()}", color = Grey)
            if (order.estimatedReadyAt != null) {
                Text("Ready at ${order.estimatedReadyAt.asLocalTime()}" + (order.preparationMinutes?.let { " ($it min)" } ?: ""), fontWeight = FontWeight.Bold)
            }
            if (order.rejectionReason.isNotBlank()) Text("Reason: ${order.rejectionReason}", color = MaterialTheme.colorScheme.error)
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            OrderItemsSummary(order)
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(order.subtotalCents.euro(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (order.pickupNotes.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text("Order notes", fontWeight = FontWeight.Bold)
                Text(order.pickupNotes, color = Red)
            }
            Spacer(Modifier.height(20.dp))

            when {
                order.isPending -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { viewModel.stopAlert(); showAccept = true }, enabled = !busy,
                        modifier = Modifier.weight(1f).height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Green),
                    ) { Text("Accept", style = MaterialTheme.typography.titleMedium) }
                    OutlinedButton(onClick = { viewModel.stopAlert(); showReject = true }, enabled = !busy, modifier = Modifier.weight(1f).height(54.dp)) {
                        Text("Reject", style = MaterialTheme.typography.titleMedium)
                    }
                }
                order.isApproved -> Button(
                    onClick = { viewModel.markReady(order) }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Green),
                ) { Text("Mark ready", style = MaterialTheme.typography.titleMedium) }
                order.isReady -> Button(onClick = { viewModel.complete(order) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Text("Picked up", style = MaterialTheme.typography.titleMedium)
                }
            }

            if (!order.isPending && !order.isRejected) {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrintChip(jobs[order.id], order.lastPrintStatus)
                    if (printerOn) OutlinedButton(onClick = { viewModel.print(order, reprint = true) }) { Text("Reprint ticket") }
                }
                if (!printerOn) Text("Printer is not enabled in Settings.", style = MaterialTheme.typography.bodySmall, color = Grey)
            }

            if (order.printEvents.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Print history", fontWeight = FontWeight.Bold)
                order.printEvents.asReversed().forEach { e ->
                    Text(
                        "${e.createdAt.asLocalDateTime()}  ${e.status}" + (e.message.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") +
                            (e.device.takeIf { it.isNotBlank() }?.let { "  ($it)" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = Grey,
                    )
                }
            }
        }
    }

    if (showAccept && order != null) AcceptDialog(order, onDismiss = { showAccept = false }, onAccept = { viewModel.accept(order, it); showAccept = false; onBack() })
    if (showReject && order != null) RejectDialog(order, onDismiss = { showReject = false }, onReject = { viewModel.reject(order, it); showReject = false; onBack() })
}
