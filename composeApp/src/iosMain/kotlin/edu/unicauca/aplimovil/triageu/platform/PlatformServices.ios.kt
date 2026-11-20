package edu.unicauca.aplimovil.triageu.platform

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.NSUserDefaultsSettings
import edu.unicauca.aplimovil.triageu.db.TriageDatabase
import edu.unicauca.aplimovil.triageu.notifications.IosReminderScheduler
import platform.Foundation.NSUserDefaults

/** Puentes que el host Swift puede registrar (WidgetKit solo existe en Swift). */
object IosHooks {
    /** Swift asigna aquí `{ WidgetCenter.shared.reloadAllTimelines() }`. */
    var reloadWidgets: (() -> Unit)? = null
}

/** Mismo App Group que el widget original. */
internal const val APP_GROUP = "group.edu.unicauca.aplimovil.TriageU"
internal const val WIDGET_KEY = "widgetItems"

@OptIn(ExperimentalSettingsImplementation::class)
actual fun createPlatformServices(): PlatformServices = PlatformServices(
    driver = NativeSqliteDriver(TriageDatabase.Schema, "triageu.db"),
    // Keychain: igual que `Keychain.swift` (servicio "com.triageu.session").
    secureSettings = KeychainSettings(service = "com.triageu.session"),
    settings = NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults),
    scheduler = IosReminderScheduler(),
    widget = IosWidgetPublisher(),
    connectivity = IosConnectivityMonitor(),
    backgroundSync = BackgroundSyncScheduler {}
)

/** Escribe el JSON en el App Group; el widget Swift lo lee y se recarga vía [IosHooks]. */
private class IosWidgetPublisher : WidgetPublisher {
    private val defaults get() = NSUserDefaults(suiteName = APP_GROUP)

    override fun publish(json: String) {
        defaults.setObject(json, forKey = WIDGET_KEY)
        IosHooks.reloadWidgets?.invoke()
    }

    override fun clear() {
        defaults.removeObjectForKey(WIDGET_KEY)
        IosHooks.reloadWidgets?.invoke()
    }
}

/**
 * En iOS la cola se reintenta al abrir la app y cada 30 s mientras haya pendientes.
 * (El `NWPathMonitor` de la versión Swift no tiene todavía equivalente aquí.)
 */
private class IosConnectivityMonitor : ConnectivityMonitor {
    override val online: kotlinx.coroutines.flow.StateFlow<Boolean> =
        kotlinx.coroutines.flow.MutableStateFlow(true)

    override fun start(onAvailable: () -> Unit) {}
}
