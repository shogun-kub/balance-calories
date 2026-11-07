package com.balancecalories.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Maps instants to the local day a user thinks they ate and burned on.
 *
 * A day does not start at midnight: a dinner at 00:40 belongs to the evening before, and a night
 * shift should not split a meal across two days. The boundary is therefore a configurable local
 * hour, 04:00 by default. Everything that groups data by day goes through this object
 * (AGENTS.md, hard rule 12).
 */
object DayWindow {

    const val DEFAULT_DAY_START_HOUR = 4

    /** The local day an instant belongs to. */
    fun dateOf(
        instant: Instant,
        zone: ZoneId,
        dayStartHour: Int = DEFAULT_DAY_START_HOUR,
    ): LocalDate {
        requireDayStart(dayStartHour)
        return instant.atZone(zone).minusHours(dayStartHour.toLong()).toLocalDate()
    }

    /**
     * First instant of [date]'s window.
     *
     * On a spring-forward day the requested wall time may not exist (e.g. 02:30 in Berlin); the
     * zone rules then resolve it forward, which is what a user would expect from "day starts at 04:00".
     */
    fun startOf(
        date: LocalDate,
        zone: ZoneId,
        dayStartHour: Int = DEFAULT_DAY_START_HOUR,
    ): Instant {
        requireDayStart(dayStartHour)
        return date.atTime(dayStartHour, 0).atZone(zone).toInstant()
    }

    /** Exclusive end of [date]'s window, i.e. the start of the next one. */
    fun endOf(
        date: LocalDate,
        zone: ZoneId,
        dayStartHour: Int = DEFAULT_DAY_START_HOUR,
    ): Instant = startOf(date.plusDays(1), zone, dayStartHour)

    /** Window length in hours; 23 or 25 on daylight-saving transitions. */
    fun lengthHours(
        date: LocalDate,
        zone: ZoneId,
        dayStartHour: Int = DEFAULT_DAY_START_HOUR,
    ): Double {
        val start = startOf(date, zone, dayStartHour)
        val end = endOf(date, zone, dayStartHour)
        return (end.epochSecond - start.epochSecond) / 3600.0
    }

    private fun requireDayStart(hour: Int) {
        require(hour in 0..23) { "dayStartHour must be in 0..23, was $hour" }
    }
}
