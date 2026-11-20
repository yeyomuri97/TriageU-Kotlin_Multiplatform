package edu.unicauca.aplimovil.triageu

import edu.unicauca.aplimovil.triageu.data.local.LocalStore
import edu.unicauca.aplimovil.triageu.data.local.Op
import edu.unicauca.aplimovil.triageu.data.remote.AuthManager
import edu.unicauca.aplimovil.triageu.data.remote.RemoteApi
import edu.unicauca.aplimovil.triageu.data.remote.SupabaseConfig
import edu.unicauca.aplimovil.triageu.data.remote.Table
import edu.unicauca.aplimovil.triageu.data.remote.createHttpClient
import edu.unicauca.aplimovil.triageu.data.sync.SyncEngine
import edu.unicauca.aplimovil.triageu.data.sync.SyncQueue
import edu.unicauca.aplimovil.triageu.model.Activity
import edu.unicauca.aplimovil.triageu.model.Priority
import edu.unicauca.aplimovil.triageu.model.Subject
import edu.unicauca.aplimovil.triageu.model.newId
import edu.unicauca.aplimovil.triageu.model.nowInstant
import edu.unicauca.aplimovil.triageu.notifications.ReminderService
import edu.unicauca.aplimovil.triageu.platform.PlatformServices
import edu.unicauca.aplimovil.triageu.platform.WidgetData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

/**
 * Punto único de acceso para la UI. Reúne lo que en iOS repartían
 * `TriageViewModel`, `SupabaseService`, `SyncRoot` (TriageUApp.swift) y los @Query de SwiftData.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TriageStore(private val platform: PlatformServices) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val local = LocalStore(platform.driver)
    private val http = createHttpClient()
    /** Cliente HTTP compartido (lo usa el asistente de ayuda). */
    val httpClient get() = http
    val isOnline: StateFlow<Boolean> get() = platform.connectivity.online
    val auth = AuthManager(http, platform.secureSettings)
    private val api = RemoteApi(http, auth)
    private val queue = SyncQueue(local, api, auth, scope)
    val reminders = ReminderService(platform.scheduler, platform.settings)
    private val syncEngine = SyncEngine(local, api, auth, queue, reminders, platform.settings)

    val activities: StateFlow<List<Activity>> =
        local.activities.stateIn(scope, SharingStarted.Eagerly, local.activitiesNow())

    val subjects: StateFlow<List<Subject>> =
        local.subjects.stateIn(scope, SharingStarted.Eagerly, local.subjectsNow())

    val pendingChanges: StateFlow<Int> =
        auth.session
            .flatMapLatest { s -> if (s == null) flowOf(0) else local.pendingCount(s.userId) }
            .stateIn(scope, SharingStarted.Eagerly, 0)

    init {
        platform.connectivity.start { platform.backgroundSync.scheduleSync() }

        // Equivale a `.task(id: auth.session?.userId)` de SyncRoot. Al cerrar sesión se limpia
        // el caché local y después se vuelve a modo invitado (todo en el mismo orden).
        scope.launch {
            var previous = auth.session.value?.userId
            auth.session.map { it?.userId }.distinctUntilChanged().collect { uid ->
                if (previous != null && uid == null && SupabaseConfig.isConfigured) wipeOnSignOut()
                previous = uid
                onUserChanged()
            }
        }

        // Equivale a `.onChange(of: widgetSignature)`.
        scope.launch {
            combine(local.activities, local.subjects) { a, s -> WidgetData.build(a, s) }
                .distinctUntilChanged()
                .collect { platform.widget.publish(it) }
        }
    }

    private suspend fun onUserChanged() {
        // Sin sesión la app funciona en modo invitado: datos locales, sin sincronizar
        // (syncEngine.sync() devuelve false). Al iniciar sesión se fusionan con la nube.
        reminders.requestPermission()
        val reachable = syncEngine.sync()
        syncEngine.seedSubjectsIfNeeded(reachable)
        reminders.rescheduleAll(local.activitiesNow())
    }

    private fun wipeOnSignOut() {
        reminders.cancelAll()
        queue.clear()
        local.wipe()
        platform.widget.clear()
        // Permite volver a crear las materias iniciales en modo invitado.
        platform.settings.remove("triageu.subjectsSeeded.local")
    }

    /** Llamar cuando la app vuelve a primer plano. */
    fun onForeground() {
        scope.launch { syncEngine.sync() }
    }

    // ---- Actividades ----

    fun addActivity(
        title: String,
        subject: String,
        type: String,
        due: Instant,
        hasDueTime: Boolean,
        weight: Int?,
        preparation: Int,
        hours: Int
    ) {
        val now = nowInstant()
        val a = Activity(
            id = newId(),
            title = title,
            subject = subject,
            type = type,
            dueDate = due,
            hasDueTime = hasDueTime,
            weightPercent = weight,
            preparation = preparation,
            durationMin = hours * 60,
            priority = Priority.calculate(preparation, weight),
            completed = false,
            createdAt = now,
            updatedAt = now
        )
        local.upsert(a)
        queue.enqueue(Table.ACTIVITIES, a.id, Op.UPSERT)
        reminders.schedule(a)
    }

    /** Guarda cambios de edición; recalcula la prioridad y marca la modificación. */
    fun updateActivity(a: Activity) {
        val updated = a.copy(
            priority = Priority.calculate(a.preparation, a.weightPercent),
            updatedAt = nowInstant()
        )
        local.upsert(updated)
        queue.enqueue(Table.ACTIVITIES, updated.id, Op.UPSERT)
        reminders.schedule(updated)
    }

    fun toggleCompleted(a: Activity) {
        val updated = a.copy(completed = !a.completed, updatedAt = nowInstant())
        local.upsert(updated)
        queue.enqueue(Table.ACTIVITIES, updated.id, Op.UPSERT)
        reminders.schedule(updated)
    }

    fun deleteActivity(id: String) {
        reminders.cancel(id)
        local.deleteActivity(id)
        queue.enqueue(Table.ACTIVITIES, id, Op.DELETE)
    }

    // ---- Materias ----

    fun addSubject(name: String, colorHex: String) {
        val now = nowInstant()
        val s = Subject(newId(), name, colorHex, now, now)
        local.upsert(s)
        queue.enqueue(Table.SUBJECTS, s.id, Op.UPSERT)
    }

    /** Las actividades guardan la materia por nombre: al renombrar se actualizan también. */
    fun updateSubject(subject: Subject, newName: String, colorHex: String) {
        val now = nowInstant()
        if (subject.name != newName) {
            for (a in local.activitiesNow().filter { it.subject == subject.name }) {
                local.upsert(a.copy(subject = newName, updatedAt = now))
                queue.enqueue(Table.ACTIVITIES, a.id, Op.UPSERT)
            }
        }
        local.upsert(subject.copy(name = newName, colorHex = colorHex, updatedAt = now))
        queue.enqueue(Table.SUBJECTS, subject.id, Op.UPSERT)
    }

    fun deleteSubject(id: String) {
        local.deleteSubject(id)
        queue.enqueue(Table.SUBJECTS, id, Op.DELETE)
    }

    // ---- Sesión ----

    fun signIn(email: String, password: String) {
        scope.launch { auth.signIn(email, password) }
    }

    fun signUp(email: String, password: String) {
        scope.launch { auth.signUp(email, password) }
    }

    fun resetPassword(email: String) {
        scope.launch { auth.resetPassword(email) }
    }

    fun signOut() = auth.signOut()

    /**
     * Intenta subir lo pendiente antes de cerrar sesión.
     * Devuelve cuántos cambios quedaron sin subir (0 = se puede cerrar sin perder nada).
     */
    suspend fun flushAndCountPending(): Int {
        queue.flush()
        val uid = auth.session.value?.userId ?: return 0
        return local.pendingCountNow(uid)
    }

    fun discardPendingAndSignOut() {
        queue.clear()
        auth.signOut()
    }

    fun lastSyncAtMillis(): Long = syncEngine.lastSyncAtMillis()

    /** Fuerza sincronización con la nube; devuelve true si hubo contacto con el servidor. */
    suspend fun checkConnection(): Boolean = syncEngine.sync()

    suspend fun syncNow(): Boolean = syncEngine.sync()

    /** JSON actual del widget (actividades pendientes). */
    fun snapshotForWidget(): String = WidgetData.build(local.activitiesNow(), local.subjectsNow())

    suspend fun deleteAccount(): String? {
        if (!SupabaseConfig.isConfigured || !auth.isSignedIn) {
            return "Inicia sesión para eliminar la cuenta."
        }
        val token = try {
            auth.validToken()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return "No hay sesión activa."
        return try {
            val resp = http.post("${SupabaseConfig.url}/functions/v1/delete-account") {
                header("apikey", SupabaseConfig.anonKey)
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (resp.status.value != 200) {
                return when (resp.status.value) {
                    401 -> "Sesión expirada. Vuelve a iniciar sesión."
                    else -> "No se pudo eliminar la cuenta. Inténtalo más tarde."
                }
            }
            wipeOnSignOut()
            auth.signOut()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "No se pudo conectar. Revisa tu conexión."
        }
    }

    fun setRemindersEnabled(on: Boolean) {
        reminders.setEnabled(on)
        reminders.rescheduleAll(local.activitiesNow())
    }

    // ---- Recordatorios ----

    fun setReminderHourBefore(h: Int) {
        reminders.setHourBefore(h)
        reminders.rescheduleAll(local.activitiesNow())
    }

    fun setReminderHourDay(h: Int) {
        reminders.setHourDay(h)
        reminders.rescheduleAll(local.activitiesNow())
    }
}
