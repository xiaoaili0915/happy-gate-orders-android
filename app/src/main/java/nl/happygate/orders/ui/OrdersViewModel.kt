package nl.happygate.orders.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nl.happygate.orders.OrdersApp
import nl.happygate.orders.data.AppSettings
import nl.happygate.orders.data.Order
import nl.happygate.orders.data.userMessage
import nl.happygate.orders.notifications.OrderMessagingService
import nl.happygate.orders.printing.PrintJobStatus
import nl.happygate.orders.printing.PrintOutcome

enum class Connection { CONNECTING, ONLINE, OFFLINE }

data class UiState(
    val settings: AppSettings? = null, // null until loaded from disk
    val pending: List<Order> = emptyList(),
    val active: List<Order> = emptyList(),
    val history: List<Order> = emptyList(),
    val connection: Connection = Connection.CONNECTING,
    val lastError: String? = null,
    val busyOrderIds: Set<Int> = emptySet(),
    val message: String? = null, // one-shot snackbar text
)

class OrdersViewModel(app: Application) : AndroidViewModel(app) {

    private val container = app as OrdersApp
    private val repo = container.repository
    private val settingsStore = container.settingsStore
    private val notifier = container.notifier
    val printManager = container.printManager

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state
    val printJobs: StateFlow<Map<Int, PrintJobStatus>> = printManager.jobs

    /** Orders staff have already been alerted about in this process. */
    private val alertedIds = mutableSetOf<Int>()
    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { s ->
                val wasLoggedIn = _state.value.settings?.isLoggedIn == true
                _state.update { it.copy(settings = s) }
                if (s.isLoggedIn && !wasLoggedIn) onLoggedIn()
                if (!s.isLoggedIn) stopPolling()
            }
        }
        viewModelScope.launch { repo.refreshRequests.collect { refresh() } }
    }

    private fun onLoggedIn() {
        viewModelScope.launch { OrderMessagingService.registerCurrentToken(settingsStore, repo) }
    }

    // ---- polling while the UI is started (driven from MainActivity's lifecycle) ----

    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                if (_state.value.settings?.isLoggedIn == true) refresh()
                delay(5_000)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    suspend fun refresh() {
        if (_state.value.settings?.isLoggedIn != true) return
        try {
            val pending = repo.pending()
            val active = repo.active()
            _state.update { it.copy(pending = pending, active = active, connection = Connection.ONLINE, lastError = null) }
            alertForNew(pending)
        } catch (e: Exception) {
            _state.update { it.copy(connection = Connection.OFFLINE, lastError = e.userMessage()) }
        }
    }

    private fun alertForNew(pending: List<Order>) {
        val fresh = pending.filter { it.id !in alertedIds }
        if (fresh.isEmpty()) return
        alertedIds += fresh.map { it.id }
        if (_state.value.settings?.alertSound != false) notifier.playForegroundAlert()
    }

    fun stopAlert() = notifier.stopAlert()

    // ---- login ----

    suspend fun login(baseUrl: String, token: String): String? = try {
        val ping = repo.testConnection(baseUrl, token)
        settingsStore.saveLogin(baseUrl, token, ping.restaurantName)
        repo.invalidate()
        null
    } catch (e: Exception) {
        e.userMessage()
    }

    fun logout() {
        viewModelScope.launch {
            stopPolling()
            settingsStore.clearLogin()
            repo.invalidate()
            _state.update { UiState(settings = it.settings?.copy(baseUrl = "", token = "")) }
        }
    }

    // ---- order actions (all idempotent on the server) ----

    fun accept(order: Order, minutes: Int) = action(order.id) {
        val updated = repo.accept(order.id, minutes)
        notifier.cancel(order.id)
        if (settingsStore.current().printerConfigured) printManager.enqueue(updated)
        "Order #${order.id} accepted, ready ${updated.expectedPickupTime ?: ""}"
    }

    fun reject(order: Order, reason: String) = action(order.id) {
        repo.reject(order.id, reason)
        notifier.cancel(order.id)
        "Order #${order.id} rejected"
    }

    fun markReady(order: Order) = action(order.id) {
        repo.markReady(order.id)
        "Order #${order.id} marked ready"
    }

    fun complete(order: Order) = action(order.id) {
        repo.complete(order.id)
        "Order #${order.id} picked up"
    }

    fun print(order: Order, reprint: Boolean) {
        val queued = printManager.enqueue(order, reprint = reprint)
        toast(if (queued) "Ticket queued" else "Ticket already printed or printing")
    }

    private fun action(orderId: Int, block: suspend () -> String) {
        if (orderId in _state.value.busyOrderIds) return // guard against double taps
        viewModelScope.launch {
            _state.update { it.copy(busyOrderIds = it.busyOrderIds + orderId) }
            try {
                toast(block())
                refresh()
            } catch (e: Exception) {
                toast(e.userMessage())
                refresh() // another device may have acted; reconcile
            } finally {
                _state.update { it.copy(busyOrderIds = it.busyOrderIds - orderId) }
            }
        }
    }

    // ---- history / detail ----

    suspend fun loadHistory(date: String?, q: String?) {
        try {
            _state.update { it.copy(history = repo.history(date, q)) }
        } catch (e: Exception) {
            toast(e.userMessage())
        }
    }

    suspend fun fetchOrder(id: Int): Order? = try { repo.order(id) } catch (e: Exception) { toast(e.userMessage()); null }

    fun findOrder(id: Int): Order? = _state.value.run { (pending + active + history).firstOrNull { it.id == id } }

    // ---- settings ----

    fun savePrinter(host: String, port: Int, enabled: Boolean) {
        viewModelScope.launch { settingsStore.savePrinter(host, port, enabled); toast("Printer settings saved") }
    }

    fun setAlertSound(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAlertSound(enabled) }
    }

    suspend fun testPrint(host: String, port: Int): String = when (val r = printManager.testPrint(host, port)) {
        PrintOutcome.Sent -> "Test ticket sent to $host:$port"
        is PrintOutcome.Failed -> "Print failed: ${r.reason}"
        is PrintOutcome.Uncertain -> "Connection dropped mid-print: ${r.reason}"
    }

    fun toast(text: String) = _state.update { it.copy(message = text) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    suspend fun awaitSettings(): AppSettings = settingsStore.settings.first()
}
