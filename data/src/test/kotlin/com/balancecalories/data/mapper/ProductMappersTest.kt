package com.balancecalories.data.mapper

import com.balancecalories.core.food.ProductSource
import com.balancecalories.core.food.EnergyPer100g
import com.balancecalories.data.db.entity.ProductEntity
import com.balancecalories.data.off.OffProduct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductMappersTest {

    private fun offProduct(
        code: String = "3017620422003",
        name: String? = "Nutella",
        nameRu: String? = null,
        energy: EnergyPer100g? = EnergyPer100g(539.0, estimated = false),
        lastModified: Long = 1793873005,
        packGrams: Double? = 400.0,
        packMillilitres: Double? = null,
    ) = OffProduct(
        code = code,
        name = name,
        nameRu = nameRu,
        brand = "Nutella",
        quantityText = "400 g e",
        servingText = null,
        servingGrams = null,
        packGrams = packGrams,
        packMillilitres = packMillilitres,
        energy = energy,
        proteinG = 6.3,
        fatG = 30.9,
        carbsG = 57.5,
        fiberG = 0.0,
        sugarG = 56.3,
        saltG = 0.107,
        imageUrl = "https://example.test/front.jpg",
        lastModifiedSeconds = lastModified,
        nutritionDataPer = "100g",
        declaresNoNutritionData = false,
    )

    @Test
    fun aUsableOffProductBecomesAnOffRowWithItsEnergyFlag() {
        val entity = offProduct().toEntity(existing = null, now = 1_000L)!!

        assertEquals("3017620422003", entity.barcode)
        assertEquals("Nutella", entity.name)
        assertEquals("off", entity.source)
        assertEquals(539.0, entity.kcal100g!!, 1e-9)
        assertFalse("kcal came from the label", entity.energyEstimated)
        assertEquals(400.0, entity.packGrams!!, 1e-9)
        assertEquals(1793873005L, entity.offRev)
        assertEquals(1_000L, entity.createdAt)
        assertEquals(1_000L, entity.updatedAt)
    }

    @Test
    fun anEstimatedEnergyIsCarriedIntoStorage() {
        val entity = offProduct(energy = EnergyPer100g(343.2, estimated = true))
            .toEntity(existing = null, now = 1L)!!

        assertTrue("a derived value must stay marked as derived", entity.energyEstimated)
    }

    @Test
    fun aProductWithoutEnergyIsNotStoredAtAll() {
        val entity = offProduct(energy = null).toEntity(existing = null, now = 1L)

        assertNull("no energy means no row, not a zero-calorie food", entity)
    }

    @Test
    fun aProductWithoutANameIsNotStoredAtAll() {
        assertNull(offProduct(name = null).toEntity(existing = null, now = 1L))
    }

    @Test
    fun refreshingKeepsTheRowIdBecauseEntriesPointAtIt() {
        val existing = ProductEntity(
            id = 42,
            barcode = "3017620422003",
            name = "Old name",
            source = "off",
            kcal100g = 500.0,
            createdAt = 500L,
            updatedAt = 500L,
        )

        val refreshed = offProduct().toEntity(existing = existing, now = 2_000L)!!

        assertEquals(42L, refreshed.id)
        assertEquals(500L, refreshed.createdAt)
        assertEquals(2_000L, refreshed.updatedAt)
        assertEquals("Nutella", refreshed.name)
    }

    @Test
    fun aUserCorrectionSurvivesTheRefreshFlag() {
        val corrected = ProductEntity(
            id = 7,
            barcode = "3017620422003",
            name = "Nutella (my numbers)",
            source = "user",
            kcal100g = 520.0,
            isOverride = true,
            createdAt = 1L,
            updatedAt = 1L,
        )

        val refreshed = offProduct().toEntity(existing = corrected, now = 2L)!!

        assertTrue("the override flag must not be lost", refreshed.isOverride)
    }

    @Test
    fun aHandWrittenRussianNameSurvivesARefreshThatHasNone() {
        val existing = ProductEntity(
            id = 1,
            barcode = "3017620422003",
            name = "Nutella",
            nameRu = "Нутелла",
            source = "off",
            createdAt = 1L,
            updatedAt = 1L,
        )

        val refreshed = offProduct(nameRu = null).toEntity(existing = existing, now = 2L)!!

        assertEquals("Нутелла", refreshed.nameRu)
    }

    @Test
    fun somethingSoldByVolumeIsMarkedLiquid() {
        val oil = offProduct(packGrams = null, packMillilitres = 1000.0)

        assertTrue(oil.toEntity(existing = null, now = 1L)!!.isLiquid)
    }

    @Test
    fun theRoundTripThroughTheDomainTypeKeepsEveryFieldTheMathsNeeds() {
        val entity = offProduct().toEntity(existing = null, now = 1L)!!
        val domain = entity.copy(id = 9).toDomain()

        assertEquals(9L, domain.id)
        assertEquals(ProductSource.OFF, domain.source)
        assertEquals(539.0, domain.kcal100g!!, 1e-9)
        assertEquals(6.3, domain.protein100g!!, 1e-9)
        assertEquals(0.107, domain.salt100g!!, 1e-9)
        assertTrue(domain.hasEnergy)

        val back = domain.toEntity(createdAt = 1L, updatedAt = 2L)
        assertEquals(9L, back.id)
        assertEquals("off", back.source)
        assertNull("a user save has no OFF revision of its own", back.offRev)
    }
}
