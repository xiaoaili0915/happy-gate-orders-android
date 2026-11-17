package nl.happygate.orders.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nl.happygate.orders.data.Order
import nl.happygate.orders.data.asLocalDateTime
import nl.happygate.orders.data.statusLabel
import java.time.LocalDate

@Composable
fun HistoryScreen(viewModel: OrdersViewModel, onOpen: (Order) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var q by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun search() = scope.launch { viewModel.loadHistory(date.takeIf { it.isNotBlank() && q.isBlank() }, q) }

    LaunchedEffect(Unit) { search() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = q, onValueChange = { q = it }, label = { Text("Order #") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = date, onValueChange = { date = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true,
                modifier = Modifier.weight(1.4f), enabled = q.isBlank(),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { search() }) { Text("Find") }
        }
        if (state.history.isEmpty()) {
            EmptyState("No orders found", "Completed and rejected orders are listed here")
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.history, key = { it.id }) { order ->
                    OrderCard(
                        order = order,
                        headerRight = {
                            StatusChip(order.statusLabel(), if (order.isRejected) MaterialTheme.colorScheme.error else Grey)
                        },
                        onClick = { onOpen(order) },
                        actions = {
                            val whenText = when {
                                order.completedAt != null -> "Picked up ${order.completedAt.asLocalDateTime()}"
                                order.rejectedAt != null -> "Rejected ${order.rejectedAt.asLocalDateTime()}" +
                                    (order.rejectionReason.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
                                else -> ""
                            }
                            Text(whenText, style = MaterialTheme.typography.bodySmall, color = Grey)
                            order.lastPrintStatus?.let { Text("Last print: $it", style = MaterialTheme.typography.bodySmall, color = Grey) }
                        },
                    )
                }
            }
        }
    }
}
