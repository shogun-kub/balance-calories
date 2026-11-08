package com.balancecalories.app

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.toArgb
import com.balancecalories.app.ui.ChartTones
import com.balancecalories.app.ui.DarkChartTones
import com.balancecalories.app.ui.LightChartTones
import com.balancecalories.core.color.Contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chart palette, measured against the surface it is actually drawn on.
 *
 * This is the test M7.5 exists for. Before it, the light theme's macro tones were chosen for a *feeling* — quiet
 * colours that would not read as a traffic light — and were never measured: they sat at 1.4–2.9 : 1 on cream, and
 * the fat bar at 1.4 : 1 was effectively invisible. A chart whose bars cannot be seen is a number the person
 * cannot read, which breaks the app's first rule.
 *
 * The bar is `Contrast.TEXT` (4.5 : 1) rather than the 3 : 1 graphic minimum, because these tones are also drawn as
 * small legend swatches and chip dots, where a lower ratio starts to disappear.
 *
 * The surfaces come from the same colour schemes `BalanceTheme` installs, so a future theme change moves this test
 * with it instead of leaving a stale assumption behind.
 */
class ChartPaletteContrastTest {

    private val lightSurface = lightColorScheme().surface.toArgb()
    private val darkSurface = darkColorScheme().surface.toArgb()

    private fun tonesByName(tones: ChartTones): Map<String, Int> = mapOf(
        "protein" to tones.protein,
        "fat" to tones.fat,
        "carbs" to tones.carbs,
        "other" to tones.other,
        "burn" to tones.burn,
        "burnEstimate" to tones.burnEstimate,
    )

    private fun assertAllReadable(tones: ChartTones, surface: Int) {
        for ((name, argb) in tonesByName(tones)) {
            val ratio = Contrast.ratio(argb, surface)
            val measured = "#%06X is only %.2f : 1 on #%06X, below the %.1f : 1 floor".format(
                argb and 0xFFFFFF, ratio, surface and 0xFFFFFF, Contrast.TEXT,
            )
            assertTrue("$name $measured", ratio >= Contrast.TEXT)
        }
    }

    @Test
    fun everyLightToneIsReadableOnTheLightSurface() {
        assertAllReadable(LightChartTones, lightSurface)
    }

    @Test
    fun everyDarkToneIsReadableOnTheDarkSurface() {
        assertAllReadable(DarkChartTones, darkSurface)
    }

    @Test
    fun theTwoModesAreDifferentPalettes() {
        // A copy-paste that left the dark set equal to the light one would pass one of the tests above and be wrong
        // in the other mode: the same six colours cannot be legible on cream and on near-black at once.
        val light = tonesByName(LightChartTones)
        val dark = tonesByName(DarkChartTones)
        for (name in light.keys) {
            assertNotEquals("$name is the same colour in both modes", light[name], dark[name])
        }
    }

    @Test
    fun theOldLightTonesWereMeasuredNotRemembered() {
        // The specific failure that started this: #FFD54F, the previous fat colour, on the same surface.
        val oldFat = 0xFFFFD54F.toInt()
        assertTrue(
            "the old fat tone was supposed to fail the graphic floor",
            Contrast.ratio(oldFat, lightSurface) < Contrast.GRAPHIC,
        )
        // and the tone that replaced it clears the text bar
        assertTrue(
            "the new fat tone does not clear the text bar",
            Contrast.ratio(LightChartTones.fat, lightSurface) >= Contrast.TEXT,
        )
    }

    @Test
    fun theTonesAreDistinguishableFromTheTrack() {
        // Gauges draw the filled arc straight onto the track, so a tone that equals the track disappears.
        val lightTrack = lightColorScheme().surfaceVariant.toArgb()
        val darkTrack = darkColorScheme().surfaceVariant.toArgb()
        for ((name, argb) in tonesByName(LightChartTones)) {
            assertTrue(
                "$name vanishes into the light track",
                Contrast.ratio(argb, lightTrack) >= Contrast.GRAPHIC,
            )
        }
        for ((name, argb) in tonesByName(DarkChartTones)) {
            assertTrue(
                "$name vanishes into the dark track",
                Contrast.ratio(argb, darkTrack) >= Contrast.GRAPHIC,
            )
        }
    }

    @Test
    fun noToneIsTransparent() {
        // Contrast ignores alpha; a colour written with a typo'd alpha would look fine in this test and render
        // invisible on the phone.
        for (tones in listOf(LightChartTones, DarkChartTones)) {
            for ((name, argb) in tonesByName(tones)) {
                assertEquals("$name is not fully opaque", 0xFFL, ((argb ushr 24) and 0xFF).toLong())
            }
        }
    }
}
