package br.com.meutaxi.app

import org.junit.Assert.*
import org.junit.Test

/** Pure metadata checks; UI spotlights require a real Android device to validate. */
class PrimoTourStepsTest {
    @Test fun appGuideCoversAllDailyTasksInOrder() {
        val steps=PrimoTour.APP
        assertEquals(11,steps.size)
        assertNull(steps.first().key)
        assertNull(steps.last().key)
        val keys=steps.mapNotNull { it.key }
        assertEquals(keys.size,keys.distinct().size)
        assertEquals(
            listOf("home.ride","home.fuel","home.repair","nav.goals",
                "goals.edit","nav.history","nav.more","more.closing","more.backup"),
            keys
        )
        assertEquals(0,steps[1].tab)
        assertEquals(2,steps[5].tab)
        assertEquals(1,steps[7].tab)
        assertEquals(3,steps[8].tab)
    }
    @Test fun bubbleTutorialUsesDemonstrationOnly() {
        val steps=PrimoTour.BUBBLE
        assertEquals(6,steps.size)
        assertEquals(listOf("bubble.plus","bubble.menu","bubble.goal","bubble.plus"),
            steps.mapNotNull { it.key })
        assertTrue(steps.first().speech.contains("Nenhuma corrida"))
        assertTrue(steps.last().speech.contains("widget"))
    }
    @Test fun allGuideInstructionsAreReadableAndConcise() {
        for (step in PrimoTour.APP + PrimoTour.BUBBLE) {
            assertTrue(step.title.isNotBlank())
            assertTrue(step.speech.isNotBlank())
            assertTrue(step.speech.length < 160)
        }
    }
}
