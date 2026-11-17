package nl.happygate.orders.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import nl.happygate.orders.notifications.Notifier

class MainActivity : ComponentActivity() {

    private val viewModel: OrdersViewModel by viewModels()

    /** Order id to open, set when launched from a notification tap. */
    private val requestedOrderId = mutableStateOf<Int?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result not needed */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        readOrderIdFrom(intent)

        // Poll only while the UI is visible; FCM covers the background.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.startPolling()
                try {
                    kotlinx.coroutines.awaitCancellation()
                } finally {
                    viewModel.stopPolling()
                }
            }
        }

        setContent {
            HappyGateTheme {
                AppRoot(
                    viewModel = viewModel,
                    requestedOrderId = requestedOrderId.value,
                    onRequestedOrderConsumed = { requestedOrderId.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readOrderIdFrom(intent)
    }

    private fun readOrderIdFrom(intent: Intent?) {
        val id = intent?.getIntExtra(Notifier.EXTRA_ORDER_ID, -1) ?: -1
        if (id > 0) requestedOrderId.value = id
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
