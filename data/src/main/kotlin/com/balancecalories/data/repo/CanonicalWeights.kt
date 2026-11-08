package com.balancecalories.data.repo

import com.balancecalories.data.db.BalanceCaloriesDatabase
import java.time.LocalDate

/**
 * One weight per day, with a fixed source preference.
 *
 * Several writers can report the same morning: a manual entry, a connected scale, Gadgetbridge relaying the band,
 * and Health Connect. They disagree by a few hundred grams, and choosing arbitrarily would make the weight trend
 * jump between sources. The order is a product decision, not an accident:
 *
 * 1. **manual** — the person chose to correct it;
 * 2. **scale** — a real instrument;
 * 3. **Gadgetbridge** — the band relaying what its app recorded;
 * 4. **Health Connect** — the broadest net, often a merge of the above.
 *
 * Shared by the adaptive TDEE and the history screen so the number they disagree about can only be the maths.
 */
class CanonicalWeights(private val database: BalanceCaloriesDatabase) {

    suspend fun perDay(profileId: Long, from: LocalDate, to: LocalDate): Map<LocalDate, Double> {
        val rows = database.bodyMeasurementDao().weightsBetween(profileId, from.toString(), to.toString())
        val best = LinkedHashMap<LocalDate, Pair<Int, Double>>()
        for (row in rows) {
            val weight = row.weightKg ?: continue
            val date = runCatching { LocalDate.parse(row.localDate) }.getOrNull() ?: continue
            val rank = rank(row.source)
            val existing = best[date]
            if (existing == null || rank < existing.first) best[date] = rank to weight
        }
        return best.mapValues { it.value.second }
    }

    private fun rank(source: String): Int = when (source) {
        BodyMeasurementRepository.SOURCE_MANUAL -> 0
        BodyMeasurementRepository.SOURCE_SCALE -> 1
        BodyMeasurementRepository.SOURCE_GADGETBRIDGE -> 2
        BodyMeasurementRepository.SOURCE_HEALTH_CONNECT -> 3
        else -> 4
    }
}
