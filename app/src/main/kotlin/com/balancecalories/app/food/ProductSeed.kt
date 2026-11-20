package com.balancecalories.app.food

/**
 * What is already known about a food the person is about to type in themselves.
 *
 * A barcode OpenFoodFacts knows but cannot be logged still has its code and, usually, its name — and so does a
 * search hit with no energy. Carrying both into the manual form is the difference between "add it by hand" and
 * "type everything again", which is the dead end the owner hit on 2026-11-20.
 */
data class ProductSeed(val barcode: String? = null, val name: String? = null)
