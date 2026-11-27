package edu.unicauca.aplimovil.triageu.domain

import edu.unicauca.aplimovil.triageu.model.Activity
import edu.unicauca.aplimovil.triageu.model.Priority
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AcademicAnalyzerTest {

    private val now =
        Instant.parse("2026-11-27T12:00:00Z")

    private fun activity(
        id: String,
        title: String,
        subject: String,
        priority: String,
        dueDate: Instant,
        completed: Boolean = false
    ) = Activity(
        id = id,
        title = title,
        subject = subject,
        type = "Tarea",
        dueDate = dueDate,
        hasDueTime = false,
        weightPercent = null,
        preparation = 3,
        durationMin = 60,
        priority = priority,
        completed = completed,
        createdAt = now,
        updatedAt = now
    )

    @Test
    fun calculatesProgressBySubject() {

        val activities = listOf(
            activity(
                id = "1",
                title = "Actividad 1",
                subject = "Aplicaciones Móviles",
                priority = Priority.HIGH,
                dueDate = Instant.parse("2026-11-28T12:00:00Z"),
                completed = true
            ),
            activity(
                id = "2",
                title = "Actividad 2",
                subject = "Aplicaciones Móviles",
                priority = Priority.MEDIUM,
                dueDate = Instant.parse("2026-11-29T12:00:00Z")
            ),
            activity(
                id = "3",
                title = "Actividad 3",
                subject = "Comunicaciones Móviles",
                priority = Priority.LOW,
                dueDate = Instant.parse("2026-11-30T12:00:00Z")
            )
        )

        val result =
            AcademicAnalyzer.subjectProgress(activities)

        val mobile =
            result.first {
                it.name == "Aplicaciones Móviles"
            }

        assertEquals(2, mobile.total)
        assertEquals(1, mobile.done)
        assertEquals(1, mobile.remaining)
        assertEquals(50, mobile.completionPercent)
    }

    @Test
    fun detectsSubjectWithLowestCompletion() {

        val progress = listOf(
            SubjectProgress(
                name = "Aplicaciones Móviles",
                done = 3,
                total = 4
            ),
            SubjectProgress(
                name = "Comunicaciones Móviles",
                done = 1,
                total = 4
            ),
            SubjectProgress(
                name = "Entornos Inteligentes",
                done = 2,
                total = 4
            )
        )

        val result =
            AcademicAnalyzer.subjectToImprove(progress)

        assertEquals(
            "Comunicaciones Móviles",
            result?.name
        )
    }

    @Test
    fun overdueActivityIsRecommendedFirst() {

        val activities = listOf(
            activity(
                id = "1",
                title = "Proyecto futuro",
                subject = "Aplicaciones Móviles",
                priority = Priority.HIGH,
                dueDate =
                    Instant.parse(
                        "2026-12-01T12:00:00Z"
                    )
            ),
            activity(
                id = "2",
                title = "Tarea vencida",
                subject = "Comunicaciones Móviles",
                priority = Priority.LOW,
                dueDate =
                    Instant.parse(
                        "2026-11-20T12:00:00Z"
                    )
            )
        )

        val result =
            AcademicAnalyzer.recommendations(
                activities = activities,
                nowMs = now.toEpochMilliseconds()
            )

        assertEquals(
            "Tarea vencida",
            result.first().activity.title
        )

        assertEquals(
            "VENCIDA",
            result.first().status
        )
    }

    @Test
    fun completedActivitiesAreNotRecommended() {

        val activities = listOf(
            activity(
                id = "1",
                title = "Actividad terminada",
                subject = "Aplicaciones Móviles",
                priority = Priority.HIGH,
                dueDate =
                    Instant.parse(
                        "2026-11-20T12:00:00Z"
                    ),
                completed = true
            )
        )

        val result =
            AcademicAnalyzer.recommendations(
                activities = activities,
                nowMs = now.toEpochMilliseconds()
            )

        assertTrue(result.isEmpty())
    }
}
