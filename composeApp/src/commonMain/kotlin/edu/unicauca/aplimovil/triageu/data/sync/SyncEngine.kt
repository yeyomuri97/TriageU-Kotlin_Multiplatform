package edu.unicauca.aplimovil.triageu.data.sync

import com.russhwolf.settings.Settings
import edu.unicauca.aplimovil.triageu.data.local.LocalStore
import edu.unicauca.aplimovil.triageu.data.local.Op
import edu.unicauca.aplimovil.triageu.data.remote.ActivityDto
import edu.unicauca.aplimovil.triageu.data.remote.AuthManager
import edu.unicauca.aplimovil.triageu.data.remote.RemoteApi
import edu.unicauca.aplimovil.triageu.data.remote.SubjectDto
import edu.unicauca.aplimovil.triageu.data.remote.SupabaseConfig
import edu.unicauca.aplimovil.triageu.data.remote.Table
import edu.unicauca.aplimovil.triageu.model.Activity
import edu.unicauca.aplimovil.triageu.model.Subject
import edu.unicauca.aplimovil.triageu.model.SubjectDefaults
import edu.unicauca.aplimovil.triageu.notifications.ReminderService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/** Sincronización completa: la última escritura gana (`updated_at`). */
class SyncEngine(
    private val local: LocalStore,
    private val api: RemoteApi,
    private val auth: AuthManager,
    private val queue: SyncQueue,
    private val reminders: ReminderService,
    private val settings: Settings
) {
    private val syncMutex = Mutex()

    /** La nube guarda otra precisión de fecha. */
    private val toleranceMs = 1000L

    /** Devuelve false si no se pudo contactar al servidor. */
    suspend fun sync(): Boolean = syncMutex.withLock {
        if (!SupabaseConfig.isConfigured || !auth.isSignedIn) return false
        queue.flush()

        val remoteSubjects: List<SubjectDto>
        val remoteActivities: List<ActivityDto>
        try {
            remoteSubjects = api.fetchSubjects()
            remoteActivities = api.fetchActivities()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        }
        val uid = auth.session.value?.userId ?: return false

        syncSubjects(uid, remoteSubjects)
        syncActivities(uid, remoteActivities)
        queue.flush()
        settings.putLong(KEY_LAST_SYNC, Clock.System.now().toEpochMilliseconds())
        true
    }

    fun lastSyncAtMillis(): Long = settings.getLong(KEY_LAST_SYNC, 0L)

    private companion object {
        const val KEY_LAST_SYNC = "triageu.lastSyncAt"
    }

    private fun syncActivities(uid: String, remote: List<ActivityDto>) {
        val localList = local.activitiesNow()
        val localById = localList.associateBy { it.id }
        val remoteIds = remote.map { it.id.lowercase() }.toSet()
        val known = local.syncedIds(uid, Table.ACTIVITIES)
        val changed = mutableListOf<Activity>()

        for (dto in remote) {
            val id = dto.id.lowercase()
            if (queue.hasPendingDelete(Table.ACTIVITIES, id)) continue   // no resucitar
            val existing = localById[id]
            if (existing != null) {
                val delta = dto.toDomain().updatedAt.toEpochMilliseconds() - existing.updatedAt.toEpochMilliseconds()
                if (delta > toleranceMs) {
                    val updated = dto.toDomain()
                    local.upsert(updated)
                    changed += updated
                } else if (delta < -toleranceMs) {
                    queue.enqueue(Table.ACTIVITIES, existing.id, Op.UPSERT)
                }
            } else {
                val created = dto.toDomain()
                local.upsert(created)
                changed += created
            }
        }

        for (a in localList) {
            if (a.id in remoteIds) continue
            if (a.id in known) {
                reminders.cancel(a.id)
                local.deleteActivity(a.id)
            } else {
                queue.enqueue(Table.ACTIVITIES, a.id, Op.UPSERT)
            }
        }

        local.replaceSynced(uid, Table.ACTIVITIES, remoteIds)
        changed.forEach { reminders.schedule(it) }
    }

    private fun syncSubjects(uid: String, remote: List<SubjectDto>) {
        val localList = local.subjectsNow()
        val localById = localList.associateBy { it.id }
        val remoteIds = remote.map { it.id.lowercase() }.toSet()
        val known = local.syncedIds(uid, Table.SUBJECTS)

        for (dto in remote) {
            val id = dto.id.lowercase()
            if (queue.hasPendingDelete(Table.SUBJECTS, id)) continue
            val existing = localById[id]
            if (existing != null) {
                val r = dto.toDomain()
                val delta = r.updatedAt.toEpochMilliseconds() - existing.updatedAt.toEpochMilliseconds()
                if (delta > toleranceMs) {
                    local.upsert(existing.copy(name = r.name, colorHex = r.colorHex, updatedAt = r.updatedAt))
                } else if (delta < -toleranceMs) {
                    queue.enqueue(Table.SUBJECTS, existing.id, Op.UPSERT)
                }
            } else {
                local.upsert(dto.toDomain())
            }
        }

        for (s in localList) {
            if (s.id in remoteIds) continue
            if (s.id in known) local.deleteSubject(s.id) else queue.enqueue(Table.SUBJECTS, s.id, Op.UPSERT)
        }
        local.replaceSynced(uid, Table.SUBJECTS, remoteIds)
    }

    /** Crea las materias iniciales una sola vez por usuario (o en modo local). */
    fun seedSubjectsIfNeeded(serverReachable: Boolean) {
        val userKey = auth.session.value?.userId ?: "local"
        val flag = "triageu.subjectsSeeded.$userKey"
        if (SupabaseConfig.isConfigured && auth.isSignedIn && !serverReachable) return
        if (settings.getBoolean(flag, false)) return

        if (local.subjectsNow().isEmpty()) {
            val now = Clock.System.now()
            for (d in SubjectDefaults.all) {
                local.upsert(Subject(d.id, d.name, d.colorHex, now, now))
                queue.enqueue(Table.SUBJECTS, d.id, Op.UPSERT)
            }
        }
        settings.putBoolean(flag, true)
    }
}
