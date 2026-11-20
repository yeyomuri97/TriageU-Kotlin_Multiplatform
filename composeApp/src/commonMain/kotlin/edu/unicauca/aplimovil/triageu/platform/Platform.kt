package edu.unicauca.aplimovil.triageu.platform

import app.cash.sqldelight.db.SqlDriver
import com.russhwolf.settings.Settings
import edu.unicauca.aplimovil.triageu.model.Activity
import edu.unicauca.aplimovil.triageu.model.Subject
import edu.unicauca.aplimovil.triageu.notifications.ReminderScheduler
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Publica un resumen de las actividades pendientes para el widget de cada plataforma. */
interface WidgetPublisher {
    fun publish(json: String)
    fun clear()
}

/** Android: encola WorkManager; iOS: sin operación. */
fun interface BackgroundSyncScheduler {
    fun scheduleSync()
}

/** Avisa cuando vuelve la red para reintentar la cola de sincronización. */
interface ConnectivityMonitor {
    /** Estado de red actual (iOS aún no lo detecta: siempre true). */
    val online: kotlinx.coroutines.flow.StateFlow<Boolean>
    fun start(onAvailable: () -> Unit)
}

/** Todo lo que depende de la plataforma, entregado a la lógica común. */
class PlatformServices(
    val driver: SqlDriver,
    /** Almacén seguro (Keychain en iOS, EncryptedSharedPreferences en Android): tokens de sesión. */
    val secureSettings: Settings,
    /** Preferencias normales (horas de recordatorio, banderas). */
    val settings: Settings,
    val scheduler: ReminderScheduler,
    val widget: WidgetPublisher,
    val connectivity: ConnectivityMonitor,
    val backgroundSync: BackgroundSyncScheduler
)

expect fun createPlatformServices(): PlatformServices

@Serializable
data class WidgetItem(
    val id: String,
    val title: String,
    val subject: String,
    val colorHex: String,
    /** Epoch en milisegundos. */
    val dueDate: Long,
    val hasDueTime: Boolean,
    val priority: String
)

object WidgetData {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(WidgetItem.serializer())

    /** Hasta 6 pendientes ordenadas por fecha, igual que `WidgetBridge.update` en Swift. */
    fun build(activities: List<Activity>, subjects: List<Subject>): String {
        val colors = subjects.associate { it.name to it.colorHex }
        val items = activities
            .filter { !it.completed }
            .sortedBy { it.dueDate }
            .take(6)
            .map {
                WidgetItem(
                    id = it.id,
                    title = it.title,
                    subject = it.subject,
                    colorHex = colors[it.subject] ?: "#4F7A68",
                    dueDate = it.dueDate.toEpochMilliseconds(),
                    hasDueTime = it.hasDueTime,
                    priority = it.priority
                )
            }
        return json.encodeToString(serializer, items)
    }

    fun parse(raw: String?): List<WidgetItem> =
        if (raw.isNullOrBlank()) emptyList()
        else try {
            json.decodeFromString(serializer, raw)
        } catch (e: Exception) {
            emptyList()
        }
}
