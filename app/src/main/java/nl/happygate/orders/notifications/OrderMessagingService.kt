package nl.happygate.orders.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import nl.happygate.orders.OrdersApp
import nl.happygate.orders.data.OrderRepository
import nl.happygate.orders.data.SettingsStore

/**
 * Receives FCM pushes. A push is only a hint: the app always refetches the order from
 * the backend, so duplicate or delayed pushes cannot create duplicate orders.
 */
class OrderMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        val app = application as OrdersApp
        val data = message.data
        if (data["type"] == "new_order") {
            val orderId = data["order_id"]?.toIntOrNull() ?: return
            val name = data["customer_name"] ?: ""
            val total = data["total_cents"]?.toIntOrNull()?.let { "€%.2f".format(it / 100.0) } ?: ""
            app.notifier.showNewOrder(orderId, "New order #$orderId", listOf(name, total).filter { it.isNotBlank() }.joinToString(" · "))
            app.repository.refreshRequests.tryEmit(Unit)
        }
    }

    override fun onNewToken(token: String) {
        val app = application as OrdersApp
        scope.launch { registerToken(app.settingsStore, app.repository, token) }
    }

    companion object {
        private const val TAG = "OrderMessaging"

        /** Fetch the current FCM token (if Firebase is configured) and register it with the backend. */
        suspend fun registerCurrentToken(settingsStore: SettingsStore, repository: OrderRepository) {
            val token = try {
                FirebaseMessaging.getInstance().token.await()
            } catch (e: Exception) {
                Log.i(TAG, "FCM unavailable (no google-services.json?): ${e.message}")
                return
            }
            registerToken(settingsStore, repository, token)
        }

        private suspend fun registerToken(settingsStore: SettingsStore, repository: OrderRepository, token: String) {
            if (!settingsStore.current().isLoggedIn) return
            try {
                repository.registerDevice(token)
                Log.i(TAG, "Device token registered")
            } catch (e: Exception) {
                Log.w(TAG, "Device registration failed: ${e.message}")
            }
        }
    }
}
