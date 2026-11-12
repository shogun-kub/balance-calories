package com.balancecalories.app.history

import com.balancecalories.data.repo.DaySummary

/**
 * Folds a run of days into the bars the chart actually draws.
 *
 * The problem this solves is arithmetic, not style: thirty days across a phone is twelve dp per day, and a
 * twelve-dp slot holding two bars is a texture, not a chart (owner feedback, 2026-11-12). A week per bar is
 * readable at any of those sizes, and the target line stays a constant, because every full bar is exactly
 * seven days.
 *
 * The honesty cost is stated rather than hidden: a weekly sum cannot show that Tuesday was a binge, only that
 * the week as a whole was fine. That is why the bar keeps the count of **days that actually have a log**, and
 * why the caller still prints the per-day summary sentence above the chart.
 *
 * Buckets are seven **consecutive** days counted from the oldest day in the range, not calendar weeks: the
 * newest bucket then ends on today, which is the week a person is actually living, and only the oldest bucket
 * can be short. Calendar weeks would cut both ends and produce two partial bars for no gain.
 */
internal fun bucketForChart(days: List<DaySummary>, rangeDays: Int): List<DaySummary> {
    if (rangeDays <= SEVEN || days.size <= SEVEN) return days
    return days.chunked(SEVEN).map { week -> foldWeek(week) }
}

/** How many days one aggregated bar covers. The target line is multiplied by this. */
internal const val WEEK_DAYS: Int = 7

private const val SEVEN = 7

private fun foldWeek(week: List<DaySummary>): DaySummary {
    val first = week.first()
    return DaySummary(
        // The bucket is named by its first day, which is the date the axis prints.
        date = first.date,
        intakeKcal = week.sumOf { it.intakeKcal },
        logged = week.any { it.logged },
        burnKcal = week.sumOf { it.burnKcal },
        // One estimate in the week makes the week's burn an estimate: the bar is only as measured as its
        // weakest day, and a solid bar would lend the whole week an authority three days do not have.
        burnIsMeasured = week.all { it.burnIsMeasured },
        quality = week.last().quality,
        weightKg = week.lastOrNull { it.weightKg != null }?.weightKg,
        proteinG = week.sumOf { it.proteinG },
        fatG = week.sumOf { it.fatG },
        carbsG = week.sumOf { it.carbsG },
    )
}
