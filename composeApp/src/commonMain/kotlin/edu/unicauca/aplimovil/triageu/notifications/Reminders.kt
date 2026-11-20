package edu.unicauca.aplimovil.triageu.notifications

import com.russhwolf.settings.Settings
import edu.unicauca.aplimovil.triageu.model.Activity
import edu.unicauca.aplimovil.triageu.model.Priority
import edu.unicauca.aplimovil.triageu.model.currentTimeZone
import edu.unicauca.aplimovil.triageu.model.nowInstant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

data class ScheduledReminder(
    val id: String,
    val at: Instant,
    val title: String,
    val body: String
)

/** Lo único que cada plataforma debe implementar: programar/cancelar avisos locales. */
interface ReminderScheduler {
    suspend fun requestPermission()
    fun schedule(items: List<ScheduledReminder>)
    fun cancel(ids: List<String>)
    fun cancelAll()
}

/**
 * Recordatorios locales de entrega (misma lógica que `NotificationService` en Swift).
 * - Sin hora definida: día anterior y día de la entrega, a las horas configuradas en Perfil.
 * - Con hora definida: 24 h y 2 h antes de la hora exacta.
 * - Prioridad ALTA: aviso extra 3 días antes.
 */
object ReminderPlanner {

    private val suffixes = listOf("3d", "1d", "0d", "24h", "2h")

    fun ids(activityId: String): List<String> = suffixes.map { "$activityId-$it" }

    fun plan(
        a: Activity,
        hourBefore: Int,
        hourDay: Int,
        now: Instant,
        tz: TimeZone
    ): List<ScheduledReminder> {
        if (a.completed) return emptyList()
        val fires = mutableListOf<Triple<String, Instant, String>>()
        val title = a.title

        if (a.hasDueTime) {
            val due = a.dueDate
            if (a.priority == Priority.HIGH) {
                fires += Triple("3d", due.minus(3, DateTimeUnit.DAY, tz), "Prioridad alta: $title vence en 3 días")
            }
            fires += Triple("24h", due.minus(24, DateTimeUnit.HOUR), "Vence en 24 horas: $title")
            fires += Triple("2h", due.minus(2, DateTimeUnit.HOUR), "Vence en 2 horas: $title")
        } else {
            val dueDay = a.dueDate.toLocalDateTime(tz).date
            fun at(daysBefore: Int, hour: Int): Instant =
                LocalDateTime(dueDay.minus(daysBefore, DateTimeUnit.DAY), LocalTime(hour, 0)).toInstant(tz)

            if (a.priority == Priority.HIGH) {
                fires += Triple("3d", at(3, hourBefore), "Prioridad alta: $title vence en 3 días")
            }
            fires += Triple("1d", at(1, hourBefore), "Mañana vence: $title")
            fires += Triple("0d", at(0, hourDay), "Hoy vence: $title")
        }

        return fires
            .filter { it.second > now }
            .map { (suffix, date, body) ->
                ScheduledReminder("${a.id}-$suffix", date, a.subject, body)
            }
    }
}

class ReminderService(
    private val scheduler: ReminderScheduler,
    private val settings: Settings
) {
    private val _hourBefore = MutableStateFlow(settings.getInt(KEY_BEFORE, 9))
    private val _hourDay = MutableStateFlow(settings.getInt(KEY_DAY, 8))
    private val _enabled = MutableStateFlow(settings.getBoolean(KEY_ENABLED, true))

    val hourBefore: StateFlow<Int> = _hourBefore.asStateFlow()
    val hourDay: StateFlow<Int> = _hourDay.asStateFlow()
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setHourBefore(h: Int) {
        _hourBefore.value = h
        settings.putInt(KEY_BEFORE, h)
    }

    fun setHourDay(h: Int) {
        _hourDay.value = h
        settings.putInt(KEY_DAY, h)
    }

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        settings.putBoolean(KEY_ENABLED, on)
        if (!on) cancelAll() 
    }

    suspend fun requestPermission() = scheduler.requestPermission()

    fun cancel(activityId: String) = scheduler.cancel(ReminderPlanner.ids(activityId))

    fun cancelAll() = scheduler.cancelAll()

    fun schedule(a: Activity) {
        if (!_enabled.value) return
        cancel(a.id)
        val items = ReminderPlanner.plan(a, _hourBefore.value, _hourDay.value, nowInstant(), currentTimeZone())
        if (items.isNotEmpty()) scheduler.schedule(items)
    }

    fun rescheduleAll(list: List<Activity>) {
        if (!_enabled.value) {
            cancelAll()
            return
        }
        list.forEach { schedule(it) }
    }

    private companion object {
        const val KEY_BEFORE = "reminderHourBefore"
        const val KEY_DAY = "reminderHourDay"
        const val KEY_ENABLED = "remindersEnabled"
    }
}
