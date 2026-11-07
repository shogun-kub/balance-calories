package com.balancecalories.core.energy

/**
 * Resting energy expenditure predictors, kcal/day.
 *
 * Mifflin–St Jeor is the default: it is the most accurate general-purpose equation for
 * non-athletes (±10% typical error) and needs no body-fat measurement.
 *
 * Mifflin MD, St Jeor ST, et al. "A new predictive equation for resting energy expenditure in
 * healthy individuals." Am J Clin Nutr. 1990;51(2):241–7. doi:10.1093/ajcn/51.2.241
 */
object Bmr {

    /**
     * Mifflin–St Jeor (1990).
     *
     * male:   10·W + 6.25·H − 5·A + 5
     * female: 10·W + 6.25·H − 5·A − 161
     *
     * @param weightKg body mass in kilograms
     * @param heightCm standing height in centimetres
     * @param ageYears completed years
     */
    fun mifflinStJeor(sex: Sex, weightKg: Double, heightCm: Double, ageYears: Int): Double {
        require(weightKg > 0.0) { "weightKg must be positive, was $weightKg" }
        require(heightCm > 0.0) { "heightCm must be positive, was $heightCm" }
        require(ageYears in 1..120) { "ageYears out of range: $ageYears" }

        val neutral = 10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears
        return when (sex) {
            Sex.MALE -> neutral + 5.0
            Sex.FEMALE -> neutral - 161.0
        }
    }

    /**
     * Harris–Benedict as revised by Roza & Shizgal (1984). Kept for comparison and for anyone who
     * wants to sanity-check Mifflin; the app uses Mifflin by default.
     *
     * Roza AM, Shizgal HM. Am J Clin Nutr. 1984;40(1):168–82.
     */
    fun harrisBenedictRevised(sex: Sex, weightKg: Double, heightCm: Double, ageYears: Int): Double {
        require(weightKg > 0.0) { "weightKg must be positive, was $weightKg" }
        require(heightCm > 0.0) { "heightCm must be positive, was $heightCm" }
        require(ageYears in 1..120) { "ageYears out of range: $ageYears" }

        return when (sex) {
            Sex.MALE -> 88.362 + 13.397 * weightKg + 4.799 * heightCm - 5.677 * ageYears
            Sex.FEMALE -> 447.593 + 9.247 * weightKg + 3.098 * heightCm - 4.330 * ageYears
        }
    }
}
