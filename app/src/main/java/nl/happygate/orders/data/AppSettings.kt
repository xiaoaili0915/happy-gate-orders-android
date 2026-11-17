package nl.happygate.orders.data

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val baseUrl: String = "",
    val token: String = "",
    val restaurantName: String = "",
    val printerHost: String = "",
    val printerPort: Int = 9100,
    val printerEnabled: Boolean = false,
    val alertSound: Boolean = true,
) {
    val isLoggedIn: Boolean get() = baseUrl.isNotBlank() && token.isNotBlank()
    val printerConfigured: Boolean get() = printerEnabled && printerHost.isNotBlank()
}

class SettingsStore(private val context: Context) {
    private object Keys {
        val baseUrl = stringPreferencesKey("base_url")
        val token = stringPreferencesKey("token")
        val restaurantName = stringPreferencesKey("restaurant_name")
        val printerHost = stringPreferencesKey("printer_host")
        val printerPort = intPreferencesKey("printer_port")
        val printerEnabled = booleanPreferencesKey("printer_enabled")
        val alertSound = booleanPreferencesKey("alert_sound")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            baseUrl = p[Keys.baseUrl] ?: "",
            token = p[Keys.token] ?: "",
            restaurantName = p[Keys.restaurantName] ?: "",
            printerHost = p[Keys.printerHost] ?: "",
            printerPort = p[Keys.printerPort] ?: 9100,
            printerEnabled = p[Keys.printerEnabled] ?: false,
            alertSound = p[Keys.alertSound] ?: true,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun saveLogin(baseUrl: String, token: String, restaurantName: String) {
        context.dataStore.edit {
            it[Keys.baseUrl] = normaliseBaseUrl(baseUrl)
            it[Keys.token] = token.trim()
            it[Keys.restaurantName] = restaurantName
        }
    }

    suspend fun clearLogin() {
        context.dataStore.edit {
            it.remove(Keys.baseUrl)
            it.remove(Keys.token)
            it.remove(Keys.restaurantName)
        }
    }

    suspend fun savePrinter(host: String, port: Int, enabled: Boolean) {
        context.dataStore.edit {
            it[Keys.printerHost] = host.trim()
            it[Keys.printerPort] = port
            it[Keys.printerEnabled] = enabled
        }
    }

    suspend fun setAlertSound(enabled: Boolean) {
        context.dataStore.edit { it[Keys.alertSound] = enabled }
    }

    val deviceName: String get() = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
}
