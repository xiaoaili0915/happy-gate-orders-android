package nl.happygate.orders.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.happygate.orders.data.Order
import nl.happygate.orders.data.asLocalTime
import nl.happygate.orders.printing.PrintState

@Composable
fun ActiveOrdersScreen(viewModel: OrdersViewModel, onOpen: (Order) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val jobs by viewModel.printJobs.collectAsStateWithLifecycle()
    val now = rememberNow()
    val printerOn = state.settings?.printerConfigured == true

    Column(Modifier.fillMaxSize()) {
        ConnectionBar(state)
        if (state.active.isEmpty()) {
            EmptyState("Nothing in progress", "Accepted orders show here until picked up")
        } else {
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.active, key = { it.id }) { order ->
                    val busy = order.id in state.busyOrderIds
                    val job = jobs[order.id]
                    val mins = minutesUntil(order.estimatedReadyAt, now)
                    OrderCard(
                        order = order,
                        headerRight = {
                            Column(horizontalAlignment = Alignment.End) {
                                StatusChip(if (order.isReady) "READY" else "Ready ${order.estimatedReadyAt.asLocalTime()}", if (order.isReady) Green else Red)
                                if (!order.isReady && mins != null) {
                                    Text(
                                        if (mins >= 0) "in $mins min" else "${-mins} min late",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (mins < 0) MaterialTheme.colorScheme.error else Grey, fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        },
                        onClick = { onOpen(order) },
                        actions = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (printerOn || job != null || order.lastPrintStatus != null) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        PrintChip(job, order.lastPrintStatus)
                                        val failed = job?.state == PrintState.FAILED || job?.state == PrintState.UNCERTAIN ||
                                            (job == null && (order.lastPrintStatus == "failed" || order.lastPrintStatus == "uncertain"))
                                        if (printerOn && job?.state != PrintState.SENDING) {
                                            OutlinedButton(onClick = { viewModel.print(order, reprint = true) }) {
                                                Text(if (failed) "Retry print" else "Reprint")
                                            }
                                        }
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (order.isApproved) {
                                        Button(
                                            onClick = { viewModel.markReady(order) }, enabled = !busy,
                                            modifier = Modifier.weight(1f).height(50.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Green),
                                        ) { Text("Mark ready", style = MaterialTheme.typography.titleMedium) }
                                    } else {
                                        Button(
                                            onClick = { viewModel.complete(order) }, enabled = !busy,
                                            modifier = Modifier.weight(1f).height(50.dp),
                                        ) { Text("Picked up", style = MaterialTheme.typography.titleMedium) }
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
