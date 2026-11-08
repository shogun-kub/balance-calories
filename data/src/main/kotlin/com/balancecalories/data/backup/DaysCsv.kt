package com.balancecalories.data.backup

import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.Locale

/**
 * One row per day for a spreadsheet.
 *
 * Two deliberate differences from the backup. First, this is a **report**, not a restore point: it holds the numbers
 * a person wants to look at, not every column. Second, it is CSV, so the formatting has to survive Excel and Google
 * Sheets — both of which treat a comma as a separator and will happily reinterpret `1,5` as two cells. The file is
 * therefore written with `;` as the delimiter and `.` as the decimal mark, RFC 4180 quoting, and CRLF line endings.
 *
 * Empty cells mean "no data", never zero: a day with no food log has an empty `eaten_kcal`, because writing `0`
 * would be a claim about a person rather than a missing entry — the same rule the History screen follows.
 */
object DaysCsv {

    const val DELIMITER = ";"

    private val HEADER = listOf(
        "date",
        "eaten_kcal",
        "burned_kcal",
        "balance_kcal",
        "burn_is_measured",
        "quality",
        "steps",
        "weight_kg",
        "logged",
    )

    /**
     * What a day's activity row offers a burn calculation, or null when the day has no row at all.
     *
     * The caller decides the burn, because the precedence rule lives in `DailyBurn` and a second implementation here
     * would eventually disagree with the screen — the report is supposed to be the screen, in a spreadsheet.
     */
    data class ActivityInput(
        val activeKcal: Double?,
        val totalKcal: Double?,
        /** Whether any app was named as the writer. The Honor's platform baseline has none, and that decides
         *  whether the burn counts as measured — the same rule the Today and History screens apply. */
        val hasOrigins: Boolean,
    )

    /**
     * @param burnFor the burn to report for a day. Null means "no burn to report", which leaves the cell empty
     *   rather than printing a zero the person never earned.
     */
    fun build(
        db: SupportSQLiteDatabase,
        profileId: Long,
        burnFor: (date: String, activity: ActivityInput?) -> Burn?,
    ): String {
        val eaten = eatenByDay(db, profileId)
        val activity = activityByDay(db, profileId)
        val weights = weightByDay(db, profileId)

        val dates = (eaten.keys + activity.keys + weights.keys).sorted()
        val out = StringBuilder()
        out.append(HEADER.joinToString(DELIMITER)).append("\r\n")

        dates.forEach { date ->
            val eatenKcal = eaten[date]
            val activityRow = activity[date]
            val burn = burnFor(
                date,
                activityRow?.let { ActivityInput(it.activeKcal, it.totalKcal, it.hasOrigins) },
            )
            out.append(listOf(
                date,
                number(eatenKcal?.first),
                number(burn?.kcal),
                // A balance needs both sides; with one of them missing there is no balance to report.
                if (eatenKcal != null && burn != null) number(eatenKcal.first - burn.kcal) else "",
                if (burn != null) if (burn.isMeasured) "1" else "0" else "",
                activityRow?.quality ?: "",
                activityRow?.steps?.toString() ?: "",
                number(weights[date]),
                if (eatenKcal != null) "1" else "0",
            ).joinToString(DELIMITER) { cell(it) })
            out.append("\r\n")
        }
        return out.toString()
    }

    /** A burn in the form the report needs: the number, and whether it came from a measurement or a formula. */
    data class Burn(val kcal: Double, val isMeasured: Boolean)

    private data class Activity(
        val steps: Int?,
        val quality: String,
        val activeKcal: Double?,
        val totalKcal: Double?,
        val hasOrigins: Boolean,
    )

    private fun eatenByDay(db: SupportSQLiteDatabase, profileId: Long): Map<String, Pair<Double, Boolean>> {
        val result = mutableMapOf<String, Pair<Double, Boolean>>()
        val sql = "SELECT local_date, SUM(kcal) FROM food_entry " +
            "WHERE profile_id = ? AND deleted_at IS NULL GROUP BY local_date ORDER BY local_date"
        db.query(sql, arrayOf<Any>(profileId)).use { cursor ->
            while (cursor.moveToNext()) {
                result[cursor.getString(0)] = cursor.getDouble(1) to true
            }
        }
        return result
    }

    private fun activityByDay(db: SupportSQLiteDatabase, profileId: Long): Map<String, Activity> {
        val result = mutableMapOf<String, Activity>()
        val sql = "SELECT local_date, steps, quality, active_kcal, total_kcal, data_origins FROM daily_activity " +
            "WHERE profile_id = ? ORDER BY local_date"
        db.query(sql, arrayOf<Any>(profileId)).use { cursor ->
            while (cursor.moveToNext()) {
                val steps = if (cursor.isNull(1)) null else cursor.getInt(1)
                result[cursor.getString(0)] = Activity(
                    steps = steps,
                    quality = cursor.getString(2),
                    activeKcal = if (cursor.isNull(3)) null else cursor.getDouble(3),
                    totalKcal = if (cursor.isNull(4)) null else cursor.getDouble(4),
                    hasOrigins = !cursor.isNull(5) && cursor.getString(5).isNotBlank(),
                )
            }
        }
        return result
    }

    private fun weightByDay(db: SupportSQLiteDatabase, profileId: Long): Map<String, Double> {
        // Ranked like everywhere else, so the CSV and the History screen cannot disagree about a day's weight:
        // manual > scale > Gadgetbridge > Health Connect.
        val result = mutableMapOf<String, Double>()
        val sql = "SELECT local_date, weight_kg, " +
            "CASE source WHEN 'manual' THEN 0 WHEN 'scale' THEN 1 WHEN 'gadgetbridge' THEN 2 ELSE 3 END AS rank " +
            "FROM body_measurement WHERE profile_id = ? AND weight_kg IS NOT NULL " +
            "ORDER BY local_date, rank, measured_at DESC"
        db.query(sql, arrayOf<Any>(profileId)).use { cursor ->
            while (cursor.moveToNext()) {
                val date = cursor.getString(0)
                // Ordered by rank, so the first row per day is the canonical one.
                if (date !in result) result[date] = cursor.getDouble(1)
            }
        }
        return result
    }

    /** Three decimals at most, and a dot: a comma would split the cell in two. */
    private fun number(value: Double?): String {
        if (value == null) return ""
        val rounded = Math.round(value * 1000.0) / 1000.0
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.ROOT, "%.3f", rounded).trimEnd('0').trimEnd('.')
        }
    }

    private fun cell(value: String): String {
        val needsQuotes = value.any { it == DELIMITER.single() || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }
}
