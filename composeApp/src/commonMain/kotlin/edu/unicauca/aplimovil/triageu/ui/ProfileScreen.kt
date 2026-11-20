@file:OptIn(ExperimentalMaterial3Api::class)

package edu.unicauca.aplimovil.triageu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.unicauca.aplimovil.triageu.TriageStore
import edu.unicauca.aplimovil.triageu.data.remote.SupabaseConfig
import edu.unicauca.aplimovil.triageu.model.nowInstant
import edu.unicauca.aplimovil.triageu.platform.appVersionLabel
import edu.unicauca.aplimovil.triageu.platform.openAppNotificationSettings
import edu.unicauca.aplimovil.triageu.platform.openExternalUrl
import edu.unicauca.aplimovil.triageu.platform.queryNotificationsEnabled
import edu.unicauca.aplimovil.triageu.platform.sharePlainText
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.datetime.LocalTime

@Composable
fun ProfileScreen(
    store: TriageStore,
    onAboutTap: () -> Unit,
    onLoginTap: () -> Unit,
    onOpenAssistant: () -> Unit = {}
) {
    val session by store.auth.session.collectAsState()
    val subjects by store.subjects.collectAsState()
    val activities by store.activities.collectAsState()
    val pending by store.pendingChanges.collectAsState()
    val online by store.isOnline.collectAsState()
    val hourBefore by store.reminders.hourBefore.collectAsState()
    val hourDay by store.reminders.hourDay.collectAsState()
    val remindersOn by store.reminders.enabled.collectAsState()
    val infoMessage by store.auth.infoMessage.collectAsState()
    val errorMessage by store.auth.errorMessage.collectAsState()
    val authLoading by store.auth.isLoading.collectAsState()

    val scope = rememberCoroutineScope()
    var showSubjects by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var unsynced by remember { mutableStateOf(0) }
    var syncing by remember { mutableStateOf(false) }
    var syncError by remember { mutableStateOf<String?>(null) }
    var lastSync by remember { mutableStateOf(store.lastSyncAtMillis()) }
    var notifOk by remember { mutableStateOf(true) }
    var exportBusy by remember { mutableStateOf(false) }
    var deleteStep by remember { mutableStateOf(0) }
    var deleteConfirmText by remember { mutableStateOf("") }
    var deleteBusy by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var showFaq by remember { mutableStateOf(false) }
    var resetBusy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        notifOk = queryNotificationsEnabled()
    }
    LaunchedEffect(authLoading) {
        if (!authLoading) resetBusy = false
    }

    val total = activities.size
    val completed = activities.count { it.completed }
    val email = session?.email ?: "Estudiante TriageU"
    val initials = emailInitials(email)

    val statusLabel: String
    val statusColor: Color
    when {
        !online -> { statusLabel = "Sin conexión"; statusColor = TextSecondary }
        pending > 0 -> { statusLabel = "$pending cambios pendientes"; statusColor = Color(0xFFB45309) }
        session != null -> { statusLabel = "Sincronizado"; statusColor = TriageGreen }
        else -> { statusLabel = "Modo local"; statusColor = TextSecondary }
    }

    fun formatSync(ms: Long): String {
        if (ms <= 0L) return "Nunca"
        val dt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
        return "${dt.date.dayOfMonth}/${dt.date.monthNumber}/${dt.date.year} ${dt.time.hour.toString().padStart(2, '0')}:${dt.time.minute.toString().padStart(2, '0')}"
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        ProfileHeader(initials, email, statusLabel, statusColor, total, completed, subjects.size)

        if (session == null && SupabaseConfig.isConfigured) {
            SettingsCard("Cuenta") {
                SettingsRow(Icons.Filled.Sync, "Iniciar sesión", "Sincroniza en la nube", onClick = onLoginTap)
            }
        }

        if (session != null) {
            SettingsCard("Cuenta") {
                SettingsRow(Icons.Filled.Email, "Correo", email, enabled = false, showChevron = false)
                SettingsRow(
                    Icons.Filled.Lock,
                    "Cambiar contraseña",
                    "Te enviamos un enlace a tu correo",
                    enabled = !resetBusy && !authLoading,
                    trailing = {
                        if (resetBusy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = TriageGreen)
                    }
                ) {
                    resetBusy = true
                    store.resetPassword(email)
                }
                val msg = infoMessage ?: errorMessage
                msg?.let { Text(it, fontSize = 13.sp, color = if (errorMessage != null) ErrorRed else TriageGreen, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                DestructiveRow(Icons.AutoMirrored.Filled.Logout, "Cerrar sesión") {
                    scope.launch {
                        val left = store.flushAndCountPending()
                        if (left > 0) { unsynced = left; confirmSignOut = true } else store.signOut()
                    }
                }
            }
        }

        SettingsCard("Materias") {
            subjects.take(5).forEach { s ->
                val n = activities.count { it.subject == s.name }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(colorFromHex(s.colorHex)))
                    Spacer(Modifier.width(10.dp))
                    Text(s.name, Modifier.weight(1f), fontSize = 16.sp)
                    Text("$n", fontSize = 14.sp, color = TextSecondary)
                }
            }
            if (subjects.size > 5) {
                Text("+ ${subjects.size - 5} más", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 16.dp))
            }
            SettingsRow(Icons.Filled.Palette, "Administrar materias", "${subjects.size} en total", onClick = { showSubjects = true })
        }

        SettingsCard("Recordatorios") {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Recordatorios activos", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("Avisos de entrega", fontSize = 13.sp, color = TextSecondary)
                }
                Switch(
                    checked = remindersOn,
                    onCheckedChange = { store.setRemindersEnabled(it) },
                    modifier = Modifier.semantics { contentDescription = "Activar recordatorios" }
                )
            }
            ReminderTimeRow("Día anterior", hourBefore) { store.setReminderHourBefore(it) }
            ReminderTimeRow("Día de entrega", hourDay) { store.setReminderHourDay(it) }
            val notifSubtitle = if (notifOk) "Permitidas" else "Denegadas: activa las notificaciones"
            SettingsRow(Icons.Filled.Notifications, "Notificaciones del sistema", notifSubtitle, onClick = {
                if (!notifOk) openAppNotificationSettings()
                scope.launch { notifOk = queryNotificationsEnabled() }
            })
            if (!notifOk) {
                TextButton(
                    onClick = { openAppNotificationSettings() },
                    modifier = Modifier.padding(start = 8.dp).heightIn(min = 48.dp)
                ) { Text("Abrir ajustes", color = TriageGreenDark) }
            }
        }

        SettingsCard("Sincronización") {
            SettingsRow(Icons.Filled.Cloud, "Conexión", if (online) "En línea" else "Sin internet", showChevron = false)
            SettingsRow(Icons.Filled.Sync, "Última sincronización", formatSync(lastSync), showChevron = false)
            SettingsRow(Icons.Filled.Sync, "Cambios pendientes", "$pending", showChevron = false)
            SettingsRow(
                Icons.Filled.Sync,
                "Sincronizar ahora",
                syncError ?: "Sube y descarga tus datos",
                enabled = !syncing,
                trailing = { if (syncing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = TriageGreen) }
            ) {
                scope.launch {
                    syncing = true
                    syncError = null
                    val ok = store.checkConnection()
                    lastSync = store.lastSyncAtMillis()
                    syncing = false
                    if (!ok) syncError = "No se pudo sincronizar. Revisa tu conexión."
                }
            }
            syncError?.let { Text(it, fontSize = 13.sp, color = ErrorRed, modifier = Modifier.padding(horizontal = 16.dp)) }
        }

        SettingsCard("Ayuda y soporte") {
            SettingsRow(Icons.Filled.SmartToy, "Abrir asistente Tri", "Pregúntale a Tri", onClick = onOpenAssistant)
            SettingsRow(Icons.AutoMirrored.Filled.Help, "Preguntas frecuentes", null, onClick = { showFaq = true })
            SettingsRow(Icons.Filled.Email, "Reportar un problema", null) { openExternalUrl(TriageULinks.REPORT_EMAIL) }
        }

        SettingsCard("Legal y datos") {
            SettingsRow(Icons.Filled.Info, "Política de privacidad", null) { openExternalUrl(TriageULinks.PRIVACY) }
            SettingsRow(Icons.Filled.Info, "Términos de uso", null) { openExternalUrl(TriageULinks.TERMS) }
            SettingsRow(
                Icons.Filled.Share,
                "Exportar mis datos",
                "JSON con actividades y materias",
                enabled = !exportBusy,
                trailing = { if (exportBusy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = TriageGreen) }
            ) {
                exportBusy = true
                val json = buildExportJson(activities, subjects)
                sharePlainText(json, "triageu_export.json")
                exportBusy = false
            }
            if (session != null) {
                DestructiveRow(Icons.Filled.DeleteForever, "Eliminar mi cuenta") {
                    deleteStep = 1
                    deleteConfirmText = ""
                    deleteError = null
                }
            }
        }

        SettingsCard("Acerca de") {
            SettingsRow(Icons.Filled.Info, "Versión", appVersionLabel(), showChevron = false)
            SettingsRow(Icons.Filled.Info, "Acerca de TriageU", "Descripción y créditos", onClick = onAboutTap)
        }
    }

    if (showSubjects) SubjectsDialog(store) { showSubjects = false }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Hay cambios sin sincronizar") },
            text = { Text("Sin conexión no se pudieron subir $unsynced cambios. Si cierras sesión ahora se perderán.") },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; store.discardPendingAndSignOut() }) {
                    Text("Cerrar sesión y descartarlos", color = ErrorRed)
                }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancelar") } }
        )
    }

    if (showFaq) {
        AlertDialog(
            onDismissRequest = { showFaq = false },
            title = { Text("Preguntas frecuentes") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    faqLines().forEach { Text(it, fontSize = 14.sp) }
                }
            },
            confirmButton = { TextButton(onClick = { showFaq = false }) { Text("Cerrar") } }
        )
    }

    when (deleteStep) {
        1 -> AlertDialog(
            onDismissRequest = { deleteStep = 0 },
            title = { Text("¿Eliminar cuenta?") },
            text = { Text("Se borrarán tu cuenta y todos tus datos en la nube y en este dispositivo. No se puede deshacer.") },
            confirmButton = { TextButton(onClick = { deleteStep = 2 }) { Text("Continuar", color = ErrorRed) } },
            dismissButton = { TextButton(onClick = { deleteStep = 0 }) { Text("Cancelar") } }
        )
        2 -> AlertDialog(
            onDismissRequest = { if (!deleteBusy) deleteStep = 0 },
            title = { Text("Confirmar eliminación") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Escribe ELIMINAR para confirmar.")
                    OutlinedTextField(
                        value = deleteConfirmText,
                        onValueChange = { deleteConfirmText = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                    )
                    deleteError?.let { Text(it, color = ErrorRed, fontSize = 13.sp) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = deleteConfirmText.trim().equals("ELIMINAR", ignoreCase = true) && !deleteBusy,
                    onClick = {
                        scope.launch {
                            deleteBusy = true
                            deleteError = null
                            val err = store.deleteAccount()
                            deleteBusy = false
                            if (err == null) deleteStep = 0 else deleteError = err
                        }
                    }
                ) {
                    if (deleteBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Eliminar", color = ErrorRed)
                }
            },
            dismissButton = { TextButton(enabled = !deleteBusy, onClick = { deleteStep = 0 }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun ProfileHeader(
    initials: String,
    email: String,
    status: String,
    statusColor: Color,
    total: Int,
    done: Int,
    subjects: Int
) {
    Column(
        Modifier.fillMaxWidth().triageCard().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(TriageGreen),
            contentAlignment = Alignment.Center
        ) {
            Text(initials, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Text(email, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(status, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = statusColor)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MiniStat("$total", "Actividades")
            MiniStat("$done", "Completadas")
            MiniStat("$subjects", "Materias")
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TriageGreenDark)
        Text(label, fontSize = 12.sp, color = TextSecondary)
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().triageCard().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        content()
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    enabled: Boolean = true,
    showChevron: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val mod = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
        .then(if (enabled && onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 16.dp, vertical = 10.dp)
    Row(mod, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = TriageGreen, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = if (enabled) Color(0xFF101828) else TextSecondary)
            subtitle?.let { Text(it, fontSize = 13.sp, color = TextSecondary, maxLines = 2) }
        }
        trailing?.invoke()
        if (showChevron && onClick != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

@Composable
private fun DestructiveRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ErrorRed, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = ErrorRed.copy(alpha = 0.6f))
    }
}

@Composable
private fun ReminderTimeRow(label: String, hour: Int, onChange: (Int) -> Unit) {
    var time by remember(hour) { mutableStateOf(LocalTime(hour, 0)) }
    LaunchedEffect(hour) { time = LocalTime(hour, 0) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 16.sp)
        Box(Modifier.width(120.dp)) {
            TimeField(time) {
                time = it
                onChange(it.hour)
            }
        }
    }
}

private fun emailInitials(email: String): String {
    val local = email.substringBefore("@").trim()
    if (local.isEmpty()) return "TU"
  val parts = local.split('.', '_', '-', ' ').filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
        local.length >= 2 -> local.take(2).uppercase()
        else -> local.uppercase().padEnd(2, 'T')
    }
}

private fun faqLines(): List<String> = listOf(
    "• Inicio: actividades del día con filtros y prioridad.",
    "• Agenda: entregas por semana y calendario.",
    "• Progreso: cumplimiento por periodo y por materia.",
    "• Los recordatorios necesitan permiso de notificaciones.",
    "• Inicia sesión para guardar en la nube y sincronizar."
)

@Serializable
private data class ExportBundle(
    val exportedAt: Long,
    val activities: List<ExportActivity>,
    val subjects: List<ExportSubject>
)

@Serializable
private data class ExportActivity(
    val id: String,
    val title: String,
    val subject: String,
    val type: String,
    val dueDateMillis: Long,
    val completed: Boolean,
    val priority: String
)

@Serializable
private data class ExportSubject(val id: String, val name: String, val colorHex: String)

private val exportJson = Json { prettyPrint = true }

private fun buildExportJson(
    activities: List<edu.unicauca.aplimovil.triageu.model.Activity>,
    subjects: List<edu.unicauca.aplimovil.triageu.model.Subject>
): String = exportJson.encodeToString(
    ExportBundle(
        exportedAt = nowInstant().toEpochMilliseconds(),
        activities = activities.map {
            ExportActivity(
                it.id, it.title, it.subject, it.type,
                it.dueDate.toEpochMilliseconds(), it.completed, it.priority
            )
        },
        subjects = subjects.map { ExportSubject(it.id, it.name, it.colorHex) }
    )
)