package com.balancecalories.core.profile

/**
 * Conversion at the edge only. Everything is stored metric, so a unit preference can never corrupt a
 * stored measurement or make two screens disagree about a number.
 */
object Units {

    const val CM_PER_INCH = 2.54
    const val KG_PER_POUND = 0.45359237

    /** Height as shown to the user: centimetres or inches. */
    fun heightFromCm(cm: Double, system: UnitSystem): Double = when (system) {
        UnitSystem.METRIC -> cm
        UnitSystem.IMPERIAL -> cm / CM_PER_INCH
    }

    fun heightToCm(value: Double, system: UnitSystem): Double = when (system) {
        UnitSystem.METRIC -> value
        UnitSystem.IMPERIAL -> value * CM_PER_INCH
    }

    /** Weight as shown to the user: kilograms or pounds. */
    fun weightFromKg(kg: Double, system: UnitSystem): Double = when (system) {
        UnitSystem.METRIC -> kg
        UnitSystem.IMPERIAL -> kg / KG_PER_POUND
    }

    fun weightToKg(value: Double, system: UnitSystem): Double = when (system) {
        UnitSystem.METRIC -> value
        UnitSystem.IMPERIAL -> value * KG_PER_POUND
    }
}
