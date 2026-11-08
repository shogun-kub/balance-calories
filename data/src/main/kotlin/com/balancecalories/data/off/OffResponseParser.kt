package com.balancecalories.data.off

import com.balancecalories.core.food.LabelEnergy
import org.json.JSONObject

/**
 * Parses OpenFoodFacts product responses — v2 **and** v3, because they disagree on the status field.
 *
 * Measured against the live API on 2026-11-08, not recalled:
 *
 * | Case           | Endpoint | HTTP | status               | status_verbose          |
 * |----------------|----------|------|----------------------|-------------------------|
 * | found          | v2       | 200  | `1` (number)         | product found           |
 * | found          | v3       | 200  | `"success"` (string) | absent                  |
 * | absent         | both     | 404  | `0` / error list     | product not found       |
 * | malformed code | v2       | 200  | `0`                  | no code or invalid code |
 *
 * v3 is the endpoint the client uses; the v2 shape is still understood so a cached or proxied response
 * cannot silently look like a missing product.
 *
 * Note the trap: OFF happily returns success for junk codes that happen to exist in its database
 * (9999999999999 is a cucumber), so a found product is not the same as a *useful* one. That is why
 * [OffProduct.isUsable] exists and why "found but empty" is surfaced instead of hidden.
 */
object OffResponseParser {

    fun parse(httpStatus: Int, body: String?): OffLookup {
        if (httpStatus == 404) return OffLookup.NotFound
        if (httpStatus != 200) return OffLookup.Unavailable("HTTP $httpStatus")
        if (body.isNullOrBlank()) return OffLookup.Unavailable("empty response body")

        val root = runCatching { JSONObject(body) }.getOrElse {
            return OffLookup.Unavailable("unreadable JSON: ${it.message}")
        }

        if (!isSuccess(root)) {
            val verbose = root.optString("status_verbose")
            // v2 names the problem in prose; v3 lists errors and uses "invalid_code" as the id.
            val invalid = verbose.contains("invalid code", ignoreCase = true) ||
                body.contains("invalid_code", ignoreCase = true)
            return if (invalid) OffLookup.Invalid(root.optString("code")) else OffLookup.NotFound
        }

        val product = root.optJSONObject("product")
            ?: return OffLookup.Unavailable("a successful status without a product object")
        return OffLookup.Found(parseProduct(product))
    }

    /** v2 says `1`, v3 says `"success"`; anything else is a miss. */
    private fun isSuccess(root: JSONObject): Boolean = when (val raw = root.opt("status")) {
        is Number -> raw.toInt() == 1
        is String -> raw.equals("success", ignoreCase = true) || raw == "1"
        else -> false
    }

    fun parseProduct(product: JSONObject): OffProduct {
        val nutriments = product.optJSONObject("nutriments") ?: JSONObject()

        val protein = nutriments.number("proteins_100g")
        val fat = nutriments.number("fat_100g")
        val carbs = nutriments.number("carbohydrates_100g")
        val fiber = nutriments.number("fiber_100g")

        // OFF stores energy under several names; energy_100g is kilojoules, despite the bare name.
        val kcal = nutriments.number("energy-kcal_100g")
        val kj = nutriments.number("energy-kj_100g") ?: nutriments.number("energy_100g")
        val energy = LabelEnergy.resolve(kcal, kj, protein, fat, carbs, fiber)

        val quantity = product.text("quantity")
        val parsedQuantity = parseQuantity(quantity)

        return OffProduct(
            code = product.text("code").orEmpty(),
            name = product.text("product_name"),
            nameRu = product.text("product_name_ru"),
            brand = product.text("brands")?.substringBefore(',')?.trim()?.ifBlank { null },
            quantityText = quantity,
            servingText = product.text("serving_size"),
            servingGrams = servingGrams(product),
            packGrams = parsedQuantity.grams,
            packMillilitres = parsedQuantity.millilitres,
            energy = energy,
            proteinG = protein,
            fatG = fat,
            carbsG = carbs,
            fiberG = fiber,
            sugarG = nutriments.number("sugars_100g"),
            saltG = salt(nutriments),
            imageUrl = product.text("image_url") ?: product.text("image_front_url"),
            lastModifiedSeconds = product.long("last_modified_t"),
            nutritionDataPer = product.text("nutrition_data_per"),
            declaresNoNutritionData = product.text("no_nutrition_data") == "on",
        )
    }

    /**
     * Salt, falling back to sodium × 2.5.
     *
     * The conversion is the standard one (salt = sodium × 2.5) and is recorded in docs/05-notes.md;
     * it is an estimate if the label only declared sodium, which is most EU labels.
     */
    private fun salt(nutriments: JSONObject): Double? {
        nutriments.number("salt_100g")?.let { return it }
        return nutriments.number("sodium_100g")?.let { it * 2.5 }
    }

    /** `serving_quantity` is a number with a separate unit; only mass is taken as grams. */
    private fun servingGrams(product: JSONObject): Double? {
        val amount = product.number("serving_quantity") ?: return null
        val unit = product.text("serving_quantity_unit")?.lowercase()
        return when (unit) {
            "g", "gram", "grams", null -> amount
            else -> null
        }
    }

    /** A parsed pack size: mass and volume stay separate, see [parseQuantity]. */
    data class ParsedQuantity(val grams: Double?, val millilitres: Double?)

    private val QUANTITY_PATTERN = Regex(
        """(?i)^\s*([0-9]+(?:[.,][0-9]+)?)\s*(kg|kgs|g|gr|grams?|ml|l|cl)\b""",
    )

    /**
     * `"400 g e"` → 400 g, `"1 kg"` → 1000 g, `"1 l"` → 1000 ml.
     *
     * Volume stays volume: turning millilitres into grams would assume a density of 1, which is true for
     * water and wrong for oil or cream. The caller decides, and [OffProduct.packMillilitres] keeps the
     * observation honest.
     */
    fun parseQuantity(raw: String?): ParsedQuantity {
        if (raw.isNullOrBlank()) return ParsedQuantity(null, null)
        val match = QUANTITY_PATTERN.find(raw) ?: return ParsedQuantity(null, null)
        val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull()
            ?: return ParsedQuantity(null, null)
        return when (match.groupValues[2].lowercase()) {
            "kg", "kgs" -> ParsedQuantity(amount * 1000, null)
            "l" -> ParsedQuantity(null, amount * 1000)
            "cl" -> ParsedQuantity(null, amount * 10)
            "ml" -> ParsedQuantity(null, amount)
            else -> ParsedQuantity(amount, null)
        }
    }

    /**
     * OFF sometimes sends numbers as strings ("539"), so a plain `optDouble` would quietly produce 0.
     * Accepting both shapes is cheaper than losing a product's energy to a type mismatch.
     */
    private fun JSONObject.number(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val raw = get(key)
        val value = when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.replace(',', '.').toDoubleOrNull()
            else -> null
        }
        return value?.takeIf { it.isFinite() && it >= 0.0 }
    }

    private fun JSONObject.long(key: String): Long {
        if (!has(key) || isNull(key)) return 0L
        return when (val raw = get(key)) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    /**
     * Reads a text field, tolerating the two shapes OFF actually uses.
     *
     * **The barcode endpoint and the search endpoint disagree on `brands`**, measured 2026-11-08: the
     * product object carries `"brands": "Nutella, Ferrero"` (a string), while a search hit carries
     * `"brands": ["Nutella"]` (an array). Reading the array with `getString` throws, which turned two
     * parser tests red and would have crashed the search screen on the first real result. Arrays are joined
     * with ", " so the "first brand" rule downstream keeps working on both shapes.
     */
    private fun JSONObject.text(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val joined = when (val raw = get(key)) {
            is org.json.JSONArray -> (0 until raw.length()).mapNotNull { raw.optString(it).ifBlank { null } }
                .joinToString(", ")
                .ifBlank { null }
            else -> getString(key)
        } ?: return null
        return joined.trim().ifBlank { null }
    }
}
