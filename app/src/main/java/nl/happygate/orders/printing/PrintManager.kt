package nl.happygate.orders.printing

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.happygate.orders.data.Order
import nl.happygate.orders.data.OrderRepository
import nl.happygate.orders.data.SettingsStore

enum class PrintState { NOT_QUEUED, QUEUED, SENDING, PRINTED, FAILED, UNCERTAIN }

data class PrintJobStatus(val state: PrintState, val message: String = "", val attempts: Int = 0)

/**
 * Serial print queue. Print state is tracked separately from order state: an accepted
 * order stays accepted even if the ticket fails, and the failure is surfaced for a
 * manual reprint rather than retried forever. Every outcome is reported to the backend
 * as a print event so the history survives app restarts.
 */
class PrintManager(
    private val settingsStore: SettingsStore,
    private val repository: OrderRepository,
    private val scope: CoroutineScope,
) {
    private data class Job(val order: Order, val reprint: Boolean)

    private val queue = Channel<Job>(Channel.UNLIMITED)
    private val _jobs = MutableStateFlow<Map<Int, PrintJobStatus>>(emptyMap())
    val jobs: StateFlow<Map<Int, PrintJobStatus>> = _jobs

    init {
        scope.launch { for (job in queue) run(job) }
    }

    fun statusOf(orderId: Int): PrintJobStatus = _jobs.value[orderId] ?: PrintJobStatus(PrintState.NOT_QUEUED)

    /**
     * Queue a kitchen ticket. Unless [reprint] is set, an order that is already queued,
     * printing, or printed is ignored, so duplicate accept taps or pushes cannot
     * produce extra tickets.
     */
    fun enqueue(order: Order, reprint: Boolean = false): Boolean {
        val current = statusOf(order.id).state
        if (!reprint && current in setOf(PrintState.QUEUED, PrintState.SENDING, PrintState.PRINTED)) return false
        if (current == PrintState.SENDING) return false
        set(order.id) { it.copy(state = PrintState.QUEUED, message = "") }
        queue.trySend(Job(order, reprint))
        return true
    }

    private suspend fun run(job: Job) {
        val order = job.order
        val settings = settingsStore.current()
        if (!settings.printerConfigured) {
            set(order.id) { it.copy(state = PrintState.FAILED, message = "Printer not configured") }
            report(order.id, "failed", "Printer not configured")
            return
        }
        set(order.id) { it.copy(state = PrintState.SENDING, attempts = it.attempts + 1) }
        report(order.id, "sending", if (job.reprint) "reprint" else "")

        val bytes = EscPosTicket().build(order, settings.restaurantName, reprint = job.reprint)
        val outcome = NetworkPrinterTransport(settings.printerHost, settings.printerPort).send(bytes)
        when (outcome) {
            PrintOutcome.Sent -> {
                set(order.id) { it.copy(state = PrintState.PRINTED, message = "") }
                report(order.id, "printed")
            }
            is PrintOutcome.Failed -> {
                set(order.id) { it.copy(state = PrintState.FAILED, message = outcome.reason) }
                report(order.id, "failed", outcome.reason)
            }
            is PrintOutcome.Uncertain -> {
                set(order.id) { it.copy(state = PrintState.UNCERTAIN, message = outcome.reason) }
                report(order.id, "uncertain", outcome.reason)
            }
        }
    }

    suspend fun testPrint(host: String, port: Int): PrintOutcome {
        val name = settingsStore.current().restaurantName
        return NetworkPrinterTransport(host, port).send(EscPosTicket().buildTest(name, "$host:$port"))
    }

    private fun set(orderId: Int, f: (PrintJobStatus) -> PrintJobStatus) {
        _jobs.update { it + (orderId to f(it[orderId] ?: PrintJobStatus(PrintState.NOT_QUEUED))) }
    }

    private suspend fun report(orderId: Int, status: String, message: String = "") {
        try {
            repository.reportPrint(orderId, status, message)
        } catch (e: Exception) {
            Log.w("PrintManager", "Could not report print event for #$orderId: ${e.message}")
        }
    }
}
