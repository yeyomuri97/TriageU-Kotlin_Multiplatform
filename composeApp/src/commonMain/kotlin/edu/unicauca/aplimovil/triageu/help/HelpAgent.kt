package edu.unicauca.aplimovil.triageu.help

/** Respuesta del asistente. [topicId] enlaza con un tema de [HelpTopics]; [notice] es un aviso breve. */
data class HelpAnswer(
    val text: String,
    val topicId: String? = null,
    val notice: String? = null
)

/** Contrato entre la interfaz del asistente y quien responde (local, IA o híbrido). */
interface HelpAgent {
    suspend fun ask(question: String, online: Boolean, pending: Int): HelpAnswer
}

class HelpTopic(val id: String, val title: String, val keywords: List<String>, val steps: List<String>)

object HelpTopics {
    val all: List<HelpTopic> = listOf(
        HelpTopic(
            "new", "Crear una actividad",
            listOf(
                "crear actividad", "crear una actividad", "nueva actividad", "agregar actividad",
                "agregar una actividad", "anadir actividad", "anadir una actividad", "nueva tarea",
                "crear tarea", "registrar actividad"
            ),
            listOf(
                "Toca el botón «+» de la barra inferior.",
                "Escribe el título y elige la materia y el tipo de actividad.",
                "Elige la fecha de entrega (y la hora si la necesitas).",
                "Guarda: la actividad aparecerá en «Inicio»."
            )
        ),
        HelpTopic(
            "priority", "Prioridad y orden",
            listOf("prioridad", "urgente", "ordenar", "orden de"),
            listOf(
                "La prioridad se calcula con el tipo de actividad y qué tan cerca está la entrega.",
                "Lo más urgente aparece arriba en «Inicio».",
                "Puedes cambiar el orden con el filtro «Orden».",
                "Al completar una actividad, sale de la lista de pendientes."
            )
        ),
        HelpTopic(
            "subjects", "Materias",
            listOf("materia", "materias", "asignatura", "curso", "color"),
            listOf(
                "Ve a Perfil → Materias.",
                "Toca para añadir una materia nueva.",
                "Para renombrarla o cambiar su color, tócala y edítala."
            )
        ),
        HelpTopic(
            "reminders", "Recordatorios",
            listOf("recordatorio", "recordatorios", "notificacion", "notificaciones", "alarma", "aviso", "avisa", "llegan"),
            listOf(
                "Ajusta las horas de aviso en Perfil.",
                "Permite las notificaciones cuando la app lo pida.",
                "En Android, concede también el permiso de alarmas exactas.",
                "Si no llegan, revisa que el ahorro de batería no bloquee la app."
            )
        ),
        HelpTopic(
            "sync", "Sincronización y cuenta",
            listOf("sincroniz", "nube", "conexion", "internet", "cuenta", "contrasena", "sesion", "pendientes", "offline"),
            listOf(
                "Tus cambios se guardan en el teléfono y se suben solos cuando hay internet.",
                "Toca «Revisar conexión» en Perfil para forzar la sincronización.",
                "Al cerrar sesión, la app intenta subir lo pendiente y avisa si algo falla.",
                "Si olvidaste la contraseña, usa «Restablecer contraseña» en la pantalla de acceso."
            )
        ),
        HelpTopic(
            "widget", "Widget de inicio",
            listOf("widget", "pantalla de inicio"),
            listOf(
                "Android: mantén pulsada la pantalla de inicio → Widgets → TriageU.",
                "iOS: mantén pulsada la pantalla de inicio → «+» → TriageU.",
                "Abre la app una vez para que el widget se actualice."
            )
        )
    )

    fun byId(id: String?): HelpTopic? = all.firstOrNull { it.id == id }
}
