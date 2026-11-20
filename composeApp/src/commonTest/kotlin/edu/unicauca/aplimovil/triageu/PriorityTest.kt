package edu.unicauca.aplimovil.triageu

import edu.unicauca.aplimovil.triageu.model.Priority
import kotlin.test.Test
import kotlin.test.assertEquals

class PriorityTest {

    @Test
    fun lowPreparationIsHigh() {
        assertEquals("ALTA", Priority.calculate(preparation = 1, weight = null))
        assertEquals("ALTA", Priority.calculate(preparation = 2, weight = 5))
    }

    @Test
    fun heavyWeightIsHighEvenIfPrepared() {
        assertEquals("ALTA", Priority.calculate(preparation = 5, weight = 30))
        assertEquals("ALTA", Priority.calculate(preparation = 4, weight = 50))
    }

    @Test
    fun mediumRules() {
        assertEquals("MEDIA", Priority.calculate(preparation = 3, weight = null))
        assertEquals("MEDIA", Priority.calculate(preparation = 4, weight = 15))
        assertEquals("MEDIA", Priority.calculate(preparation = 5, weight = 29))
    }

    @Test
    fun lowRules() {
        assertEquals("BAJA", Priority.calculate(preparation = 4, weight = null))
        assertEquals("BAJA", Priority.calculate(preparation = 5, weight = 14))
    }

    @Test
    fun rankOrdersHighFirst() {
        assertEquals(listOf("ALTA", "MEDIA", "BAJA"), listOf("BAJA", "ALTA", "MEDIA").sortedBy { Priority.rank(it) })
    }
}
