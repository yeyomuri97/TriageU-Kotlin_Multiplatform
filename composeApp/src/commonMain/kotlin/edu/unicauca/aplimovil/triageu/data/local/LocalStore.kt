package edu.unicauca.aplimovil.triageu.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.db.SqlDriver
import edu.unicauca.aplimovil.triageu.data.remote.Table
import edu.unicauca.aplimovil.triageu.db.ActivityEntity
import edu.unicauca.aplimovil.triageu.db.SubjectEntity
import edu.unicauca.aplimovil.triageu.db.SyncItem
import edu.unicauca.aplimovil.triageu.db.TriageDatabase
import edu.unicauca.aplimovil.triageu.model.Activity
import edu.unicauca.aplimovil.triageu.model.Subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

enum class Op(val wire: String) {
    UPSERT("upsert"), DELETE("delete");

    companion object {
        fun from(s: String) = if (s == DELETE.wire) DELETE else UPSERT
    }
}

data class QueueItem(
    val table: Table,
    val id: String,
    val op: Op,
    val userId: String,
    val attempts: Int,
    val queuedAt: Long
)

/** Persistencia local (SQLDelight). Reemplaza a SwiftData + UserDefaults de la versión iOS. */
class LocalStore(driver: SqlDriver) {

    private val db = TriageDatabase(driver)
    private val q = db.triageQueries

    // ---- Actividades ----

    val activities: Flow<List<Activity>> =
        q.selectActivities().asFlow().mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    fun activitiesNow(): List<Activity> = q.selectActivities().executeAsList().map { it.toDomain() }

    fun activity(id: String): Activity? = q.selectActivity(id).executeAsOneOrNull()?.toDomain()

    fun upsert(a: Activity) = q.upsertActivity(a.toRow())

    fun deleteActivity(id: String) = q.deleteActivity(id)

    // ---- Materias ----

    val subjects: Flow<List<Subject>> =
        q.selectSubjects().asFlow().mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() }.sortedBy { it.name.lowercase() } }

    fun subjectsNow(): List<Subject> =
        q.selectSubjects().executeAsList().map { it.toDomain() }.sortedBy { it.name.lowercase() }

    fun subject(id: String): Subject? = q.selectSubject(id).executeAsOneOrNull()?.toDomain()

    fun upsert(s: Subject) = q.upsertSubject(s.toRow())

    fun deleteSubject(id: String) = q.deleteSubject(id)

    /** Borra todo lo local (al cerrar sesión). */
    fun wipe() {
        db.transaction {
            q.deleteAllActivities()
            q.deleteAllSubjects()
        }
    }

    // ---- Cola de sincronización ----

    fun enqueue(item: QueueItem) = q.upsertQueue(item.toRow())

    fun queueItems(userId: String): List<QueueItem> =
        q.selectQueue(userId).executeAsList().map { it.toDomain() }

    fun removeQueueItem(i: QueueItem) = q.deleteQueueItem(i.table.path, i.id, i.userId, i.queuedAt)

    fun bumpQueueItem(i: QueueItem) = q.bumpQueueItem(i.table.path, i.id, i.userId, i.queuedAt)

    fun clearQueue() = q.deleteAllQueue()

    fun hasPendingDelete(table: Table, id: String): Boolean =
        q.countPendingDelete(table.path, id).executeAsOne() > 0

    fun pendingCount(userId: String): Flow<Int> =
        q.countQueue(userId).asFlow().mapToOne(Dispatchers.Default).map { it.toInt() }

    fun pendingCountNow(userId: String): Int = q.countQueue(userId).executeAsOne().toInt()

    // ---- IDs ya sincronizados ----

    fun syncedIds(userId: String, table: Table): Set<String> =
        q.selectSynced(userId, table.path).executeAsList().toSet()

    fun markSynced(userId: String, table: Table, id: String, synced: Boolean) {
        if (synced) q.insertSynced(userId, table.path, id) else q.deleteSynced(userId, table.path, id)
    }

    fun replaceSynced(userId: String, table: Table, ids: Set<String>) {
        db.transaction {
            q.clearSynced(userId, table.path)
            ids.forEach { q.insertSynced(userId, table.path, it) }
        }
    }
}

// ---- Mapeos fila <-> dominio ----

private fun Instant.ms() = toEpochMilliseconds()
private fun Boolean.l() = if (this) 1L else 0L

private fun ActivityEntity.toDomain() = Activity(
    id = id,
    title = title,
    subject = subject,
    type = type,
    dueDate = Instant.fromEpochMilliseconds(dueDate),
    hasDueTime = hasDueTime != 0L,
    weightPercent = weightPercent?.toInt(),
    preparation = preparation.toInt(),
    durationMin = durationMin.toInt(),
    priority = priority,
    completed = completed != 0L,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = Instant.fromEpochMilliseconds(updatedAt)
)

private fun Activity.toRow() = ActivityEntity(
    id = id,
    title = title,
    subject = subject,
    type = type,
    dueDate = dueDate.ms(),
    hasDueTime = hasDueTime.l(),
    weightPercent = weightPercent?.toLong(),
    preparation = preparation.toLong(),
    durationMin = durationMin.toLong(),
    priority = priority,
    completed = completed.l(),
    createdAt = createdAt.ms(),
    updatedAt = updatedAt.ms()
)

private fun SubjectEntity.toDomain() = Subject(
    id = id,
    name = name,
    colorHex = colorHex,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = Instant.fromEpochMilliseconds(updatedAt)
)

private fun Subject.toRow() = SubjectEntity(
    id = id,
    name = name,
    colorHex = colorHex,
    createdAt = createdAt.ms(),
    updatedAt = updatedAt.ms()
)

private fun SyncItem.toDomain() = QueueItem(
    table = if (tbl == Table.SUBJECTS.path) Table.SUBJECTS else Table.ACTIVITIES,
    id = id,
    op = Op.from(op),
    userId = userId,
    attempts = attempts.toInt(),
    queuedAt = queuedAt
)

private fun QueueItem.toRow() = SyncItem(
    tbl = table.path,
    id = id,
    userId = userId,
    op = op.wire,
    attempts = attempts.toLong(),
    queuedAt = queuedAt
)
