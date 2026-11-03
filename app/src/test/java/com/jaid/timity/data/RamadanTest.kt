package com.jaid.timity.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RamadanTest {
    private fun h(date: String, name: String) = Holiday(
        epochDay = LocalDate.parse(date).toEpochDay(), name = name, type = "Public Holiday",
        source = Holiday.SOURCE_API, enabled = true,
    )

    private val synced = listOf(
        h("2026-02-18", "Ramadan Start (Provisional)"),
        h("2026-03-20", "Eid al-Fitr (Provisional)"),
        h("2026-03-21", "Eid al-Fitr Holiday"),
        h("2027-02-08", "Ramadan Start (Provisional)"),
        h("2027-03-10", "Eid al-Fitr (Provisional)"),
        h("2026-11-03", "Victory Day"),
    )

    @Test
    fun `picks the current or next ramadan and ends the day before eid`() {
        val r = Ramadan.upcoming(synced, today = LocalDate.of(2026, 3, 1))
        assertEquals(Ramadan.Range(LocalDate.of(2026, 2, 18), LocalDate.of(2026, 3, 19)), r)
    }

    @Test
    fun `after this year's ramadan the next one is chosen`() {
        val r = Ramadan.upcoming(synced, today = LocalDate.of(2026, 11, 3))
        assertEquals(Ramadan.Range(LocalDate.of(2027, 2, 8), LocalDate.of(2027, 3, 9)), r)
    }

    @Test
    fun `falls back to 29 days when eid is not synced`() {
        val r = Ramadan.upcoming(listOf(h("2026-02-18", "Ramadan Start")), today = LocalDate.of(2026, 1, 1))
        assertEquals(Ramadan.Range(LocalDate.of(2026, 2, 18), LocalDate.of(2026, 3, 19)), r)
    }

    @Test
    fun `returns null with nothing synced`() {
        assertNull(Ramadan.upcoming(listOf(h("2026-11-03", "Victory Day"))))
    }
}
