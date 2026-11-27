
package edu.unicauca.aplimovil.triageu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.unicauca.aplimovil.triageu.model.GlossaryCategories
import edu.unicauca.aplimovil.triageu.model.GlossaryTerm

@Composable
fun GlossaryScreen(
    onBack: () -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Todas") }

    val terms = remember {
        listOf(
            GlossaryTerm(
                id = "pomodoro",
                title = "Técnica Pomodoro",
                definition = "Método de organización del tiempo que consiste en estudiar durante periodos cortos de concentración, separados por descansos.",
                category = GlossaryCategories.STUDY,
                example = "Estudiar 25 minutos y descansar 5 minutos. Después de cuatro ciclos, tomar un descanso más largo."
            ),
            GlossaryTerm(
                id = "active-recall",
                title = "Recuerdo activo",
                definition = "Técnica de aprendizaje que consiste en intentar recordar información sin consultar el material de estudio.",
                category = GlossaryCategories.STUDY,
                example = "Cerrar el libro e intentar explicar con tus propias palabras lo que acabas de leer."
            ),
            GlossaryTerm(
                id = "spaced-repetition",
                title = "Repetición espaciada",
                definition = "Estrategia que distribuye los repasos a lo largo del tiempo para favorecer la retención de la información.",
                category = GlossaryCategories.STUDY,
                example = "Repasar un tema el mismo día, luego a los dos días y posteriormente una semana después."
            ),
            GlossaryTerm(
                id = "time-blocking",
                title = "Bloques de tiempo",
                definition = "Método de planificación que asigna espacios específicos del horario a tareas concretas.",
                category = GlossaryCategories.PLANNING,
                example = "Reservar de 8:00 a 9:00 a. m. para estudiar una materia y de 9:00 a 9:30 para realizar ejercicios."
            ),
            GlossaryTerm(
                id = "prioritization",
                title = "Priorización",
                definition = "Proceso de ordenar las actividades según su importancia, urgencia y fecha límite.",
                category = GlossaryCategories.PLANNING,
                example = "Realizar primero una actividad que vence mañana antes que una tarea que se entrega la próxima semana."
            ),
            GlossaryTerm(
                id = "procrastination",
                title = "Procrastinación",
                definition = "Tendencia a aplazar actividades importantes y reemplazarlas por otras de menor prioridad.",
                category = GlossaryCategories.PRODUCTIVITY,
                example = "Posponer la preparación de un examen para revisar repetidamente las redes sociales."
            ),
            GlossaryTerm(
                id = "multitasking",
                title = "Multitarea",
                definition = "Realización o alternancia de varias actividades en un mismo periodo de tiempo, lo que puede dividir la atención.",
                category = GlossaryCategories.PRODUCTIVITY,
                example = "Intentar estudiar mientras se responde constantemente a mensajes."
            )
        )
    }

    val filteredTerms = terms.filter { term ->
        val matchesSearch =
            term.title.contains(searchText, ignoreCase = true) ||
                    term.definition.contains(searchText, ignoreCase = true) ||
                    term.example.contains(searchText, ignoreCase = true)

        val matchesCategory =
            selectedCategory == "Todas" || term.category == selectedCategory

        matchesSearch && matchesCategory
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "← Volver al perfil",
            color = TriageGreenDark,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { onBack() }
        )

        Text(
            text = "Glosario académico",
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Encuentra conceptos y estrategias que pueden ayudarte en tu vida universitaria.",
            fontSize = 14.sp,
            color = TextSecondary
        )

        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Buscar un concepto") },
            placeholder = { Text("Ej. Pomodoro, planificación...") }
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val categories = listOf("Todas") + GlossaryCategories.all

            categories.forEach { category ->
                val selected = selectedCategory == category

                Text(
                    text = category,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (selected) TriageGreen else TriageGreen.copy(alpha = 0.10f)
                        )
                        .clickable { selectedCategory = category }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    color = if (selected) androidx.compose.ui.graphics.Color.White
                    else TriageGreenDark,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Text(
            text = "${filteredTerms.size} conceptos encontrados",
            fontSize = 13.sp,
            color = TextSecondary
        )

        if (filteredTerms.isEmpty()) {
            Text(
                text = "No encontramos conceptos con esa búsqueda. Intenta con otra palabra.",
                modifier = Modifier
                    .fillMaxWidth()
                    .triageCard()
                    .padding(18.dp),
                color = TextSecondary,
                fontSize = 14.sp
            )
        } else {
            filteredTerms.forEach { term ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .triageCard()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = term.category,
                        color = TriageGreenDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = term.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = term.definition,
                        fontSize = 14.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Ejemplo",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TriageGreenDark
                    )

                    Text(
                        text = term.example,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
