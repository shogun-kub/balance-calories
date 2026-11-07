package com.balancecalories.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DayWindowTest {

    private val moscow: ZoneId = ZoneId.of("Europe/Moscow") // UTC+3, no DST since 2014
    private val berlin: ZoneId = ZoneId.of("Europe/Berlin") // DST, for the hard cases

    @Test
    fun `a late dinner belongs to the previous day`() {
        // 2026-11-07 02:30 in Moscow
        val instant = Instant.parse("2026-11-06T23:30:00Z")
        assertEquals(LocalDate.of(2026, 11, 6), DayWindow.dateOf(instant, moscow))
    }

    @Test
    fun `the boundary is inclusive at the day start hour`() {
        // Exactly 04:00 Moscow -> a new day
        assertEquals(
            LocalDate.of(2026, 11, 7),
            DayWindow.dateOf(Instant.parse("2026-11-07T01:00:00Z"), moscow),
        )
        // One second earlier -> still the previous day
        assertEquals(
            LocalDate.of(2026, 11, 6),
            DayWindow.dateOf(Instant.parse("2026-11-07T00:59:59Z"), moscow),
        )
    }

    @Test
    fun `start and end of a normal day`() {
        val date = LocalDate.of(2026, 11, 7)
        assertEquals(Instant.parse("2026-11-07T01:00:00Z"), DayWindow.startOf(date, moscow))
        assertEquals(Instant.parse("2026-11-08T01:00:00Z"), DayWindow.endOf(date, moscow))
        assertEquals(24.0, DayWindow.lengthHours(date, moscow), 1e-9)
    }

    @Test
    fun `spring forward produces a 23 hour day`() {
        // Berlin 2026-03-29: 02:00 CET becomes 03:00 CEST, inside the 28th's window.
        assertEquals(
            Instant.parse("2026-03-28T03:00:00Z"),
            DayWindow.startOf(LocalDate.of(2026, 3, 28), berlin),
        )
        assertEquals(
            Instant.parse("2026-03-29T02:00:00Z"),
            DayWindow.startOf(LocalDate.of(2026, 3, 29), berlin),
        )
        assertEquals(23.0, DayWindow.lengthHours(LocalDate.of(2026, 3, 28), berlin), 1e-9)
        assertEquals(24.0, DayWindow.lengthHours(LocalDate.of(2026, 3, 29), berlin), 1e-9)
    }

    @Test
    fun `fall back produces a 25 hour day`() {
        // Berlin 2026-10-25: 03:00 CEST becomes 02:00 CET, inside the 24th's window.
        assertEquals(25.0, DayWindow.lengthHours(LocalDate.of(2026, 10, 24), berlin), 1e-9)
    }

    @Test
    fun `a midnight day start behaves like a calendar day`() {
        // 02:30 Moscow on the 7th, with no shift.
        val instant = Instant.parse("2026-11-06T23:30:00Z")
        assertEquals(LocalDate.of(2026, 11, 7), DayWindow.dateOf(instant, moscow, dayStartHour = 0))
    }

    @Test
    fun `rejects an impossible day start hour`() {
        assertThrows(IllegalArgumentException::class.java) {
            DayWindow.dateOf(Instant.now(), moscow, dayStartHour = 24)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DayWindow.startOf(LocalDate.of(2026, 1, 1), moscow, dayStartHour = -1)
        }
    }
}
