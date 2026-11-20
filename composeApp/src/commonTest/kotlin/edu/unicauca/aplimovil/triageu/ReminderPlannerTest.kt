package edu.unicauca.aplimovil.triageu

import edu.unicauca.aplimovil.triageu.model.Activity
import edu.unicauca.aplimovil.triageu.notifications.ReminderPlanner
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderPlannerTest {

    private val tz = TimeZone.UTC
    private val now = Instant.parse("2026-11-01T00:00:00Z")

    private fun activity(
        priority: String,
        hasTime: Boolean,
        due: Instant,
        completed: Boolean = false
    ) = Activity(
        id = "abc",
        title = "Parcial 1",
        subject = "Aplicaciones Móviles",
        type = "Parcial",
        dueDate = due,
        hasDueTime = hasTime,
        weightPercent = null,
        preparation = 3,
        durationMin = 240,
        priority = priority,
        completed = completed,
        createdAt = now,
        updatedAt = now
    )

    @Test
    fun noTimeHighPriorityGivesThreeReminders() {
        val due = LocalDate(2026, 11, 20).atStartOfDayIn(tz)
        val plan = ReminderPlanner.plan(activity("ALTA", false, due), hourBefore = 9, hourDay = 8, now = now, tz = tz)
        assertEquals(listOf("abc-3d", "abc-1d", "abc-0d"), plan.map { it.id })
        assertEquals(Instant.parse("2026-11-17T09:00:00Z"), plan[0].at)
        assertEquals(Instant.parse("2026-11-19T09:00:00Z"), plan[1].at)
        assertEquals(Instant.parse("2026-11-20T08:00:00Z"), plan[2].at)
    }

    @Test
    fun noTimeLowPriorityHasNoThreeDayReminder() {
        val due = LocalDate(2026, 11, 20).atStartOfDayIn(tz)
        val plan = ReminderPlanner.plan(activity("BAJA", false, due), 9, 8, now, tz)
        assertEquals(listOf("abc-1d", "abc-0d"), plan.map { it.id })
    }

    @Test
    fun withTimeUses24hAnd2h() {
        val due = Instant.parse("2026-11-20T15:30:00Z")
        val plan = ReminderPlanner.plan(activity("MEDIA", true, due), 9, 8, now, tz)
        assertEquals(listOf("abc-24h", "abc-2h"), plan.map { it.id })
        assertEquals(Instant.parse("2026-11-19T15:30:00Z"), plan[0].at)
        assertEquals(Instant.parse("2026-11-20T13:30:00Z"), plan[1].at)
    }

    @Test
    fun pastRemindersAreDropped() {
        val due = LocalDate(2026, 11, 1).atStartOfDayIn(tz)   // vence "hoy" a las 00:00
        val plan = ReminderPlanner.plan(activity("ALTA", false, due), 9, 8, now, tz)
        assertEquals(listOf("abc-0d"), plan.map { it.id })
    }

    @Test
    fun completedActivityHasNoReminders() {
        val due = LocalDate(2026, 11, 20).atStartOfDayIn(tz)
        assertTrue(ReminderPlanner.plan(activity("ALTA", false, due, completed = true), 9, 8, now, tz).isEmpty())
    }
}
