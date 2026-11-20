package edu.unicauca.aplimovil.triageu.data.sync

import edu.unicauca.aplimovil.triageu.data.local.LocalStore
import edu.unicauca.aplimovil.triageu.data.local.Op
import edu.unicauca.aplimovil.triageu.data.local.QueueItem
import edu.unicauca.aplimovil.triageu.data.remote.AuthManager
import edu.unicauca.aplimovil.triageu.data.remote.RemoteApi
import edu.unicauca.aplimovil.triageu.data.remote.SupabaseConfig
import edu.unicauca.aplimovil.triageu.data.remote.SupabaseHttpException
import edu.unicauca.aplimovil.triageu.data.remote.Table
import edu.unicauca.aplimovil.triageu.data.remote.toDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.datetime.Clock

/**
 * Cola persistente de cambios pendientes de subir a Supabase.
 * Se guarda en SQLite y se reintenta al volver la red, al abrir la app
 * y cada 30 s mientras haya pendientes.
 */
class SyncQueue(
    private val local: LocalStore,
    private val api: RemoteApi,
    private val auth: AuthManager,
    private val scope: CoroutineScope
) {
    private val flushMutex = Mutex()
    private var retryJob: Job? = null

    fun enqueue(table: Table, id: String, op: Op) {
        if (!SupabaseConfig.isConfigured) return
        val uid = auth.session.value?.userId ?: return
        // La clave primaria (tbl, id, userId) hace que el cambio más nuevo reemplace al anterior.
        local.enqueue(
            QueueItem(
                table = table,
                id = id,
                op = op,
                userId = uid,
                attempts = 0,
                queuedAt = Clock.System.now().toEpochMilliseconds()
            )
        )
        scope.launch { flush() }
    }

    fun hasPendingDelete(table: Table, id: String): Boolean = local.hasPendingDelete(table, id)

    fun clear() = local.clearQueue()

    suspend fun flush() {
        if (!SupabaseConfig.isConfigured) return
        val uid = auth.session.value?.userId ?: return
        if (!flushMutex.tryLock()) return
        try {
            for (item in local.queueItems(uid)) {
                try {
                    process(uid, item)
                    local.removeQueueItem(item)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SupabaseHttpException) {
                    if (e.code == 401) break                              // sesión por renovar
                    if (e.code in 400..499 && e.code != 408 && e.code != 429) {
                        local.removeQueueItem(item)                       // dato inválido: no reintentar
                        continue
                    }
                    local.bumpQueueItem(item)
                } catch (e: Exception) {
                    break                                                 // sin red
                }
            }
        } finally {
            flushMutex.unlock()
        }
        scheduleRetryIfNeeded(uid)
    }

    private suspend fun process(uid: String, item: QueueItem) {
        when (item.op) {
            Op.DELETE -> {
                api.delete(item.table, item.id)
                local.markSynced(uid, item.table, item.id, synced = false)
            }

            Op.UPSERT -> {
                when (item.table) {
                    Table.ACTIVITIES -> {
                        val a = local.activity(item.id) ?: return
                        api.pushActivity(a.toDto())
                    }

                    Table.SUBJECTS -> {
                        val s = local.subject(item.id) ?: return
                        api.pushSubject(s.toDto())
                    }
                }
                local.markSynced(uid, item.table, item.id, synced = true)
            }
        }
    }

    private fun scheduleRetryIfNeeded(uid: String) {
        if (local.pendingCountNow(uid) == 0 || retryJob?.isActive == true) return
        retryJob = scope.launch {
            delay(30_000)
            retryJob = null
            flush()
        }
    }
}
