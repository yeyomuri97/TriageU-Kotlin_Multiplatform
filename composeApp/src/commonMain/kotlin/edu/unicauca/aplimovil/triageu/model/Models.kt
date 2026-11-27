
package edu.unicauca.aplimovil.triageu.model

import kotlinx.datetime.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Equivalente de TriageActivity (SwiftData). Inmutable: los cambios crean una copia. */
data class Activity(
    val id: String,
    val title: String,
    val subject: String,
    val type: String,
    val dueDate: Instant,
    val hasDueTime: Boolean,
    val weightPercent: Int?,
    val preparation: Int,
    val durationMin: Int,
    val priority: String,
    val completed: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)

/** Equivalente de Subject (SwiftData). */
data class Subject(
    val id: String,
    val name: String,
    val colorHex: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

/** Los IDs se manejan siempre en minúsculas. */
@OptIn(ExperimentalUuidApi::class)
fun newId(): String = Uuid.random().toString().lowercase()

object Priority {
    const val HIGH = "ALTA"
    const val MEDIUM = "MEDIA"
    const val LOW = "BAJA"

    val all = listOf(HIGH, MEDIUM, LOW)

    /** Misma regla que TriageViewModel.calculatePriority. */
    fun calculate(preparation: Int, weight: Int?): String {
        val w = weight ?: 0
        if (preparation <= 2 || w >= 30) return HIGH
        if (preparation == 3 || w >= 15) return MEDIUM
        return LOW
    }

    fun rank(p: String): Int = when (p) {
        HIGH -> 0
        MEDIUM -> 1
        else -> 2
    }

    fun label(p: String): String =
        p.lowercase().replaceFirstChar { it.uppercase() }
}

object ActivityTypes {
    val all = listOf("Parcial", "Tarea", "Proyecto", "Exposición", "Otro")
}

object SubjectDefaults {
    /** IDs fijos para que los dispositivos no dupliquen las materias iniciales. */
    data class Seed(val id: String, val name: String, val colorHex: String)

    val all = listOf(
        Seed("00000000-0000-0000-0000-0000000000a1", "Comunicaciones Móviles", "#4F7A68"),
        Seed("00000000-0000-0000-0000-0000000000a2", "Aplicaciones Móviles", "#3B82F6"),
        Seed("00000000-0000-0000-0000-0000000000a3", "Entornos Inteligentes", "#F59E0B")
    )

    val palette = listOf(
        "#4F7A68", "#3B82F6", "#F59E0B", "#EF4444",
        "#8B5CF6", "#EC4899", "#14B8A6", "#64748B"
    )
}

/**
 * Representa un concepto académico del glosario de TriageU.
 *
 * Cada término tiene un identificador, nombre, definición
 * y una categoría para organizarlo..
 */
data class GlossaryTerm(
    val id: String,
    val title: String,
    val definition: String,
    val category: String,
    val example: String
)

/** Categorías disponibles para clasificar los conceptos del glosario. */
object GlossaryCategories {
    const val STUDY = "Técnicas de estudio"
    const val PLANNING = "Planificación"
    const val PRODUCTIVITY = "Productividad"

    val all = listOf(STUDY, PLANNING, PRODUCTIVITY)
}
