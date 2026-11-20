package edu.unicauca.aplimovil.triageu.platform

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import edu.unicauca.aplimovil.triageu.db.TriageDatabase

/** Contexto de aplicación disponible para las implementaciones de plataforma. */
object AndroidContext {
    lateinit var app: Context
        private set

    fun init(context: Context) {
        app = context.applicationContext
    }
}

actual fun createPlatformServices(): PlatformServices {
    val ctx = AndroidContext.app
    val plain = SharedPreferencesSettings(ctx.getSharedPreferences("triageu_prefs", Context.MODE_PRIVATE))
    return PlatformServices(
        driver = AndroidSqliteDriver(TriageDatabase.Schema, ctx, "triageu.db"),
        secureSettings = secureSettings(ctx),
        settings = plain,
        scheduler = AndroidReminderScheduler(ctx, plain),
        widget = AndroidWidgetPublisher(ctx),
        connectivity = AndroidConnectivityMonitor(ctx),
        backgroundSync = BackgroundSyncScheduler { SyncScheduler.enqueueNow(ctx) }
    )
}

/** Equivale al Keychain de iOS: los tokens se guardan cifrados (nunca en claro). */
private fun secureSettings(ctx: Context): Settings {
    fun open(): SharedPreferences {
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            ctx,
            "triageu_secure",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    return try {
        SharedPreferencesSettings(open())
    } catch (e: Exception) {
        // Keystore corrupto (p. ej. tras restaurar el dispositivo): se descarta y se reintenta una vez.
        ctx.deleteSharedPreferences("triageu_secure")
        SharedPreferencesSettings(open())   // si falla otra vez no se guarda nada sin cifrar: se propaga el error
    }
}

private class AndroidConnectivityMonitor(private val ctx: Context) : ConnectivityMonitor {
    private val _online = kotlinx.coroutines.flow.MutableStateFlow(true)
    override val online: kotlinx.coroutines.flow.StateFlow<Boolean> = _online

    override fun start(onAvailable: () -> Unit) {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return
        try {
            _online.value = cm.activeNetwork != null
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _online.value = true
                    onAvailable()
                }

                override fun onLost(network: Network) {
                    _online.value = false
                }
            })
        } catch (e: SecurityException) {
            // Sin permiso de estado de red: queda el reintento cada 30 s.
        }
    }
}
