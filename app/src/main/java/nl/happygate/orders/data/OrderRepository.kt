package nl.happygate.orders.data

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Thin wrapper around [RestaurantApi] bound to the current login. The backend is the
 * single source of truth; the app never keeps an authoritative local copy of orders,
 * it simply refetches on launch, resume, push, and on a foreground timer.
 */
class OrderRepository(private val settingsStore: SettingsStore) {

    @Volatile private var cached: Pair<AppSettings, RestaurantApi>? = null

    /** Emits whenever something (a push message, an action) should trigger an immediate refresh. */
    val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    suspend fun api(): RestaurantApi {
        val s = settingsStore.current()
        if (!s.isLoggedIn) throw IllegalStateException("Not logged in")
        cached?.let { (cs, api) -> if (cs.baseUrl == s.baseUrl && cs.token == s.token) return api }
        return buildApi(s.baseUrl, s.token).also { cached = s to it }
    }

    fun invalidate() { cached = null }

    suspend fun testConnection(baseUrl: String, token: String): PingResponse =
        buildApi(baseUrl, token).ping()

    suspend fun pending(): List<Order> = api().orders("pending")
    suspend fun active(): List<Order> = api().orders("active")
    suspend fun history(date: String?, q: String?): List<Order> =
        api().orders("history", date = date, q = q?.takeIf { it.isNotBlank() })
    suspend fun order(id: Int): Order = api().order(id)

    suspend fun accept(id: Int, minutes: Int): Order = api().accept(id, AcceptBody(minutes))
    suspend fun reject(id: Int, reason: String): Order = api().reject(id, RejectBody(reason))
    suspend fun markReady(id: Int): Order = api().ready(id)
    suspend fun complete(id: Int): Order = api().complete(id)

    suspend fun reportPrint(id: Int, status: String, message: String = "") {
        api().printEvent(id, PrintEventBody(status, message, settingsStore.deviceName))
    }

    suspend fun registerDevice(fcmToken: String) {
        api().registerDevice(DeviceBody(fcmToken))
    }
}
