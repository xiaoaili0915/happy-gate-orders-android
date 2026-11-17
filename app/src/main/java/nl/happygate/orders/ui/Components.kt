package nl.happygate.orders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import nl.happygate.orders.data.Order
import nl.happygate.orders.data.asLocalTime
import nl.happygate.orders.data.euro
import nl.happygate.orders.data.toInstantOrNull
import nl.happygate.orders.printing.PrintJobStatus
import nl.happygate.orders.printing.PrintState

/** Ticks once a second so countdowns re-render. */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    return now
}

fun countdown(expiresAt: String?, nowMs: Long): String {
    val end = expiresAt?.toInstantOrNull()?.toEpochMilli() ?: return ""
    val secs = ((end - nowMs) / 1000).coerceAtLeast(0)
    return if (secs == 0L) "Expiring…" else "%d:%02d".format(secs / 60, secs % 60)
}

fun minutesUntil(iso: String?, nowMs: Long): Long? =
    iso?.toInstantOrNull()?.toEpochMilli()?.let { (it - nowMs) / 60_000 }

@Composable
fun StatusChip(text: String, color: Color) {
    Box(
        Modifier.background(color, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
    ) { Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
}

@Composable
fun PrintChip(status: PrintJobStatus?, serverStatus: String?) {
    // Prefer this device's live job state; fall back to what the backend recorded (e.g. after restart).
    val (label, color) = when (status?.state ?: PrintState.NOT_QUEUED) {
        PrintState.QUEUED -> "Print queued" to Grey
        PrintState.SENDING -> "Printing…" to Grey
        PrintState.PRINTED -> "Printed" to Green
        PrintState.FAILED -> "Print FAILED" to MaterialTheme.colorScheme.error
        PrintState.UNCERTAIN -> "Print uncertain" to Amber
        PrintState.NOT_QUEUED -> when (serverStatus) {
            "printed" -> "Printed" to Green
            "failed" -> "Print FAILED" to MaterialTheme.colorScheme.error
            "uncertain" -> "Print uncertain" to Amber
            null -> return
            else -> "Print: $serverStatus" to Grey
        }
    }
    StatusChip(label, color)
}

@Composable
fun OrderItemsSummary(order: Order, compact: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        order.items.forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${line.quantity}× ${line.name}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (!compact) Text(line.lineTotalCents.euro())
            }
            line.modifiers.forEach { m -> Text("   + ${m.name}", style = MaterialTheme.typography.bodySmall, color = Grey) }
            if (line.specialNotes.isNotBlank()) {
                Text("   Note: ${line.specialNotes}", style = MaterialTheme.typography.bodySmall, color = Red, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun OrderCard(
    order: Order,
    headerRight: @Composable () -> Unit,
    onClick: () -> Unit,
    actions: @Composable () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("#${order.id}  ${order.customerName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Ordered ${order.createdAt.asLocalTime()} · ${order.subtotalCents.euro()}", style = MaterialTheme.typography.bodySmall, color = Grey)
                }
                headerRight()
            }
            Spacer(Modifier.height(10.dp))
            OrderItemsSummary(order, compact = true)
            if (order.pickupNotes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("Notes: ${order.pickupNotes}", style = MaterialTheme.typography.bodySmall, color = Red)
            }
            Spacer(Modifier.height(12.dp))
            actions()
        }
    }
}

@Composable
fun EmptyState(title: String, subtitle: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Grey)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Grey)
        }
    }
}
