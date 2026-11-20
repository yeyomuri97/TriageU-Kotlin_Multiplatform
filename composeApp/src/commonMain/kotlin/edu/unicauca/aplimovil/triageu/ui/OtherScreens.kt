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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import edu.unicauca.aplimovil.triageu.data.remote.SupabaseConfig
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.unicauca.aplimovil.triageu.TriageStore
import edu.unicauca.aplimovil.triageu.model.Priority
import edu.unicauca.aplimovil.triageu.model.Subject
import edu.unicauca.aplimovil.triageu.model.SubjectDefaults
import edu.unicauca.aplimovil.triageu.model.localDate
import edu.unicauca.aplimovil.triageu.model.shortLabel
import kotlinx.coroutines.launch

// ===================== MATERIAS =====================

@Composable
fun SubjectsDialog(store: TriageStore, onDismiss: () -> Unit) {
    val subjects by store.subjects.collectAsState()
    val activities by store.activities.collectAsState()
    var editing by remember { mutableStateOf<Subject?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Subject?>(null) }

    FullScreenSheet(
        title = "Mis materias",
        onCancel = onDismiss,
        cancelLabel = "Cerrar",
        action = { TextButton(onClick = { showNew = true }) { Text("+ Nueva", color = TriageGreen, fontWeight = FontWeight.SemiBold) } }
    ) {
        if (subjects.isEmpty()) {
            Text("No tienes materias. Agrega una con el botón +.", color = TextSecondary)
        }
        subjects.forEach { s ->
            Row(
                Modifier.fillMaxWidth().triageCard(12.dp).clickable { editing = s }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(colorFromHex(s.colorHex)))
                Spacer(Modifier.width(12.dp))
                Text(s.name, Modifier.weight(1f))
                Text("${activities.count { it.subject == s.name }}", color = TextSecondary)
                Spacer(Modifier.width(12.dp))
                Text("Eliminar", color = ErrorRed, fontSize = 14.sp, modifier = Modifier.clickable { toDelete = s })
            }
        }
    }

    if (showNew) SubjectEditorDialog(store, null) { showNew = false }
    editing?.let { s -> SubjectEditorDialog(store, s) { editing = null } }

    toDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Eliminar materia") },
            text = { Text("¿Eliminar \"${s.name}\"? Las actividades que ya la usan conservarán su nombre.") },
            confirmButton = {
                TextButton(onClick = {
                    store.deleteSubject(s.id)
                    toDelete = null
                }) { Text("Eliminar", color = ErrorRed) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun SubjectEditorDialog(store: TriageStore, subject: Subject?, onDismiss: () -> Unit) {
    val all by store.subjects.collectAsState()
    var name by remember { mutableStateOf(subject?.name ?: "") }
    var colorHex by remember { mutableStateOf(subject?.colorHex ?: SubjectDefaults.palette[0]) }

    val clean = name.trim()
    val duplicate = all.any { it.id != subject?.id && it.name.lowercase() == clean.lowercase() }

    FullScreenSheet(
        title = if (subject == null) "Nueva materia" else "Editar materia",
        onCancel = onDismiss,
        action = {
            TextButton(
                enabled = clean.isNotEmpty() && !duplicate,
                onClick = {
                    if (subject != null) store.updateSubject(subject, clean, colorHex) else store.addSubject(clean, colorHex)
                    onDismiss()
                }
            ) { Text("Guardar", color = TriageGreen, fontWeight = FontWeight.SemiBold) }
        }
    ) {
        Text("Nombre", fontSize = 15.sp, color = TextSecondary)
        TriageTextField(name, { name = it }, placeholder = "Ej. Bases de Datos")
        if (duplicate) Text("Ya existe una materia con ese nombre.", fontSize = 13.sp, color = ErrorRed)

        Text("Color", fontSize = 15.sp, color = TextSecondary)
        SubjectDefaults.palette.chunked(4).forEach { rowColors ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                rowColors.forEach { hex ->
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(colorFromHex(hex)).clickable { colorHex = hex },
                        contentAlignment = Alignment.Center
                    ) {
                        if (hex == colorHex) Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ===================== ACERCA DE =====================

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 25.dp, bottom = 35.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
            Text("Acerca de TriageU", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("TriageU", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = TriageGreen)
            Text("Prioriza tu tiempo. Avanza a tu ritmo.", color = TextSecondary)
        }

        AboutCard(
            "¿Qué es TriageU?",
            "TriageU es una aplicación orientada a estudiantes universitarios. Permite registrar actividades académicas y organizarlas teniendo en cuenta la materia, la fecha, el peso en la nota, el nivel de preparación y el tiempo estimado."
        )
        AboutCard(
            "Funcionamiento",
            "El estudiante registra sus actividades académicas y TriageU calcula una prioridad según la preparación y el peso de la actividad. Las actividades se almacenan localmente y se sincronizan con la nube para conservar la información entre dispositivos."
        )
        AboutCard(
            "Tecnologías utilizadas",
            "• Kotlin Multiplatform\n• Compose Multiplatform\n• SQLDelight\n• Ktor\n• Supabase (Auth + API REST)\n• Widget y notificaciones locales"
        )
        AboutCard(
            "Créditos",
            "Aurelio José Muñoz Rios\nIsabella Plaza Díaz\nIsabella Perez Hoyos"
        )

        Text(
            "TriageU · Desarrollo de Aplicaciones Móviles",
            fontSize = 12.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        )
    }
}

@Composable
private fun AboutCard(title: String, text: String) {
    Column(
        Modifier.fillMaxWidth().triageCard().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(text, fontSize = 16.sp, color = TextSecondary)
    }
}

// ===================== LOGIN =====================

@Composable
fun AuthScreen(store: TriageStore, onBack: () -> Unit) {
    val auth = store.auth
    val isLoading by auth.isLoading.collectAsState()
    val error by auth.errorMessage.collectAsState()
    val info by auth.infoMessage.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var registering by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Spacer(Modifier.size(38.dp))
        Icon(Icons.AutoMirrored.Filled.List, null, tint = TriageGreen, modifier = Modifier.size(54.dp))
        Text("TriageU", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(
            if (registering) "Crea tu cuenta" else "Inicia sesión para sincronizar tus actividades",
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TriageTextField(email, { email = it }, placeholder = "Correo", keyboardType = KeyboardType.Email)
            TriageTextField(password, { password = it }, placeholder = "Contraseña (mín. 6 caracteres)", password = true)
        }

        error?.let { Text(it, fontSize = 14.sp, color = ErrorRed, textAlign = TextAlign.Center) }
        info?.let { Text(it, fontSize = 14.sp, color = TriageGreenDark, textAlign = TextAlign.Center) }

        PrimaryButton(
            text = if (registering) "Crear cuenta" else "Iniciar sesión",
            enabled = !isLoading,
            onClick = {
                if (registering) store.signUp(email, password) else store.signIn(email, password)
            },
            content = if (isLoading) {
                { CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp) }
            } else null
        )

        if (!registering) {
            Text(
                "¿Olvidaste tu contraseña?",
                fontSize = 15.sp,
                color = TextSecondary,
                modifier = Modifier.clickable(enabled = !isLoading) { store.resetPassword(email) }
            )
        }

        Text(
            if (registering) "¿Ya tienes cuenta? Inicia sesión" else "¿No tienes cuenta? Regístrate",
            fontSize = 16.sp,
            color = TriageGreenDark,
            modifier = Modifier.clickable {
                registering = !registering
                auth.clearMessages()
            }
        )

        Text(
            "Ahora no",
            fontSize = 15.sp,
            color = TextSecondary,
            modifier = Modifier.clickable {
                auth.clearMessages()
                onBack()
            }
        )
    }
}
