package com.balancecalories.core.food

/**
 * Retail barcode rules, in one place because both the scanner and the network client need them.
 *
 * A barcode is accepted when it is 8–14 digits after removing the spaces and dashes people type. The
 * check digit is exposed separately ([hasValidCheckDigit]) and is **not** part of acceptance: a store's
 * internal code can be a legitimate identifier without being a valid EAN, and refusing it would make the
 * app unable to log that product at all. The UI may warn; the storage layer does not judge.
 */
object Barcode {

    private const val MIN_LENGTH = 8
    private const val MAX_LENGTH = 14

    fun normalize(raw: String?): String? {
        if (raw == null) return null
        val trimmed = raw.trim().replace(" ", "").replace("-", "")
        if (trimmed.length !in MIN_LENGTH..MAX_LENGTH) return null
        if (!trimmed.all { it in '0'..'9' }) return null
        return trimmed
    }

    /**
     * EAN/UPC check digit: weights alternate 3 and 1 from the right of the payload.
     *
     * Returned as a fact rather than a filter, so a manual entry can be corrected instead of rejected.
     */
    fun hasValidCheckDigit(barcode: String): Boolean {
        val normalized = normalize(barcode) ?: return false
        val payload = normalized.dropLast(1)
        val expected = normalized.last().digitToInt()
        val sum = payload.reversed().withIndex().sumOf { (index, char) ->
            val value = char.digitToInt()
            if (index % 2 == 0) value * 3 else value
        }
        return (10 - sum % 10) % 10 == expected
    }
}
