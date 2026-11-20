package edu.unicauca.aplimovil.triageu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.unicauca.aplimovil.triageu.TriageStore
import edu.unicauca.aplimovil.triageu.model.ActivityTypes
import edu.unicauca.aplimovil.triageu.model.dueInstant
import edu.unicauca.aplimovil.triageu.model.localTime
import edu.unicauca.aplimovil.triageu.model.nowInstant
import edu.unicauca.aplimovil.triageu.model.today
import kotlinx.datetime.LocalTime

/** Estado del formulario (equivale a los `newActivity*` de `TriageViewModel`). */
class ActivityFormState {
    var title by mutableStateOf("")
    var subject by mutableStateOf("")
    var type by mutableStateOf("Parcial")
    var date by mutableStateOf(today())
    var hasTime by mutableStateOf(false)
    var time by mutableStateOf(defaultTime())
    var weight by mutableStateOf("")
    var preparation by mutableStateOf(3)
    var hours by mutableStateOf(4)
    var message by mutableStateOf<String?>(null)

    fun reset() {
        title = ""
        type = "Parcial"
        date = today()
        hasTime = false
        time = defaultTime()
        weight = ""
        preparation = 3
        hours = 4
    }

    private fun defaultTime(): LocalTime = nowInstant().localTime().let { LocalTime(it.hour, it.minute) }
}

@Composable
fun NewActivityScreen(store: TriageStore, form: ActivityFormState) {
    val subjects by store.subjects.collectAsState()
    val names = subjects.map { it.name }

    // Igual que `.onAppear`: si la materia elegida ya no existe, usa la primera.
    LaunchedEffect(names) {
        if (form.subject !in names) form.subject = names.firstOrNull() ?: ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        ScreenTitle("Nueva actividad")

        Text("Información básica", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            InputTitle("Materia")
            DropdownField(
                text = form.subject,
                options = names,
                label = { it },
                onSelect = { form.subject = it }
            )

            InputTitle("Actividad")
            TriageTextField(form.title, { form.title = it }, placeholder = "Ej. Parcial 1")

            InputTitle("Tipo")
            DropdownField(
                text = form.type,
                options = ActivityTypes.all,
                label = { it },
                onSelect = { form.type = it }
            )

            InputTitle("Fecha")
            DateField(form.date) { form.date = it }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Definir hora de entrega", fontSize = 17.sp)
                Switch(
                    checked = form.hasTime,
                    onCheckedChange = { form.hasTime = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = TriageGreen)
                )
            }
            if (form.hasTime) {
                TimeField(form.time) { form.time = it }
            }
        }

        Text("Ayúdanos a priorizarla", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            InputTitle("Peso en la materia (Opcional)")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TriageTextField(
                    value = form.weight,
                    onValueChange = { v -> form.weight = v.filter { it.isDigit() }.take(3) },
                    modifier = Modifier.weight(1f),
                    placeholder = "Ej. 30",
                    keyboardType = KeyboardType.Number
                )
                Text("%", color = TextSecondary)
            }

            Text("¿Qué tan preparado/a te sientes?", fontSize = 17.sp)
            PreparationSelector(form.preparation) { form.preparation = it }

            InputTitle("Tiempo estimado")
            HoursStepper(form.hours, { form.hours = it })
        }

        PrimaryButton("Guardar actividad", onClick = {
            val cleanTitle = form.title.trim()
            when {
                cleanTitle.isEmpty() -> form.message = "Escribe el nombre de la actividad."
                form.subject.isEmpty() -> form.message = "Crea una materia primero (Perfil > Mis materias)."
                else -> {
                    store.addActivity(
                        title = cleanTitle,
                        subject = form.subject,
                        type = form.type,
                        due = dueInstant(form.date, form.hasTime, form.time),
                        hasDueTime = form.hasTime,
                        weight = form.weight.toIntOrNull(),
                        preparation = form.preparation,
                        hours = form.hours
                    )
                    form.reset()
                    form.message = "Actividad guardada correctamente."
                }
            }
        })

        form.message?.let {
            Text(it, fontSize = 14.sp, color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}
