package edu.unicauca.aplimovil.triageu.data.remote

import edu.unicauca.aplimovil.triageu.model.Activity
import edu.unicauca.aplimovil.triageu.model.Subject
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Tablas remotas (PostgREST). */
enum class Table(val path: String) { ACTIVITIES("activities"), SUBJECTS("subjects") }

internal val SupabaseJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = true   // weight_percent = null debe viajar para poder borrar el valor
}

@Serializable
data class ActivityDto(
    val id: String,
    val title: String,
    val subject: String,
    val type: String,
    @SerialName("due_date") val dueDate: String,
    @SerialName("has_due_time") val hasDueTime: Boolean = false,
    @SerialName("weight_percent") val weightPercent: Int? = null,
    val preparation: Int,
    @SerialName("duration_min") val durationMin: Int,
    val priority: String,
    val completed: Boolean = false,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
) {
    fun toDomain() = Activity(
        id = id.lowercase(),
        title = title,
        subject = subject,
        type = type,
        dueDate = Instant.parse(dueDate),
        hasDueTime = hasDueTime,
        weightPercent = weightPercent,
        preparation = preparation,
        durationMin = durationMin,
        priority = priority,
        completed = completed,
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt)
    )
}

@Serializable
data class SubjectDto(
    val id: String,
    val name: String,
    @SerialName("color_hex") val colorHex: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
) {
    fun toDomain() = Subject(
        id = id.lowercase(),
        name = name,
        colorHex = colorHex,
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt)
    )
}

fun Activity.toDto() = ActivityDto(
    id = id,
    title = title,
    subject = subject,
    type = type,
    dueDate = dueDate.toString(),
    hasDueTime = hasDueTime,
    weightPercent = weightPercent,
    preparation = preparation,
    durationMin = durationMin,
    priority = priority,
    completed = completed,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString()
)

fun Subject.toDto() = SubjectDto(
    id = id,
    name = name,
    colorHex = colorHex,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString()
)
