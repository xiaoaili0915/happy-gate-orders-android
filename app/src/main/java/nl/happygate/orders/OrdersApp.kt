package nl.happygate.orders

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import nl.happygate.orders.data.OrderRepository
import nl.happygate.orders.data.SettingsStore
import nl.happygate.orders.notifications.Notifier
import nl.happygate.orders.printing.PrintManager

/** Process-wide singletons. Small enough that a DI framework would be overkill. */
class OrdersApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var settingsStore: SettingsStore private set
    lateinit var repository: OrderRepository private set
    lateinit var printManager: PrintManager private set
    lateinit var notifier: Notifier private set

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        repository = OrderRepository(settingsStore)
        printManager = PrintManager(settingsStore, repository, appScope)
        notifier = Notifier(this).also { it.ensureChannel() }
    }
}
