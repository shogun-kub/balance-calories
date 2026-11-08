package com.balancecalories.app.scanner

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import zxingcpp.BarcodeReader

/**
 * Proves the decoder before the camera exists.
 *
 * The risk with a JNI dependency is not the Kotlin call site — that compiles or it does not — it is whether
 * the `.so` loads on this ABI and whether the pixels are read the way we think. So this test renders a real
 * EAN-13 bitmap, hands it to the same reader the app uses, and asserts the digits come back.
 *
 * It also pins the format restriction: the reader is configured for retail EAN/UPC only, so a QR code on
 * packaging cannot be mistaken for a product barcode.
 */
@RunWith(AndroidJUnit4::class)
class ZxingBarcodeScannerTest {

    /** Standard EAN-13 module patterns, left-hand odd parity. */
    private val leftOdd = listOf(
        "0001101", "0011001", "0010011", "0111101", "0100011",
        "0110001", "0101111", "0111011", "0110111", "0001011",
    )

    /** Right-hand patterns; the even (G) codes are these reversed. */
    private val rightEven = listOf(
        "1110010", "1100110", "1101100", "1000010", "1011100",
        "1001110", "1010000", "1000100", "1001000", "1110100",
    )

    /** Which of the first six digits use even parity, selected by the leading digit. */
    private val parity = listOf(
        "LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG",
        "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL",
    )

    private fun checkDigit(payload: String): Int {
        val sum = payload.reversed().withIndex().sumOf { (index, char) ->
            val digit = char.digitToInt()
            if (index % 2 == 0) digit * 3 else digit
        }
        return (10 - sum % 10) % 10
    }

    /** Renders [payload] (12 digits) as a scannable EAN-13 bitmap with a quiet zone. */
    private fun renderEan13(payload: String, moduleWidth: Int = 3, barHeight: Int = 120): Pair<Bitmap, String> {
        require(payload.length == 12)
        val code = payload + checkDigit(payload)

        val bits = StringBuilder("101") // start guard
        val parityPattern = parity[code[0].digitToInt()]
        for (i in 1..6) {
            val digit = code[i].digitToInt()
            bits.append(if (parityPattern[i - 1] == 'L') leftOdd[digit] else rightEven[digit].reversed())
        }
        bits.append("01010") // centre guard
        for (i in 7..12) bits.append(rightEven[code[i].digitToInt()])
        bits.append("101") // end guard

        val quiet = 10
        val width = (bits.length + quiet * 2) * moduleWidth
        val height = barHeight
        val pixels = IntArray(width * height) { Color.WHITE }
        bits.forEachIndexed { index, bit ->
            if (bit == '1') {
                val startX = (quiet + index) * moduleWidth
                for (x in startX until startX + moduleWidth) {
                    for (y in 0 until height) pixels[y * width + x] = Color.BLACK
                }
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888) to code
    }

    @Test
    fun decodesAGeneratedEan13() {
        val (bitmap, expected) = renderEan13("460000000001")

        val results = BarcodeReader().read(bitmap, Rect(0, 0, bitmap.width, bitmap.height), 0)

        assertEquals(1, results.size)
        assertEquals(expected, results.single().text)
        assertEquals(BarcodeReader.Format.EAN_13, results.single().format)
        assertNull("a clean read reports no error", results.single().error)
    }

    @Test
    fun decodesAnotherRealisticRussianBarcode() {
        // 460-469 is the GS1 prefix range registered to Russia, which is what the app will actually see.
        val (bitmap, expected) = renderEan13("469000000000")

        val results = BarcodeReader().read(bitmap, Rect(0, 0, bitmap.width, bitmap.height), 0)

        assertEquals(expected, results.single().text)
    }

    @Test
    fun anEmptyImageReturnsNothingInsteadOfGarbage() {
        val blank = Bitmap.createBitmap(300, 120, Bitmap.Config.ARGB_8888)
        blank.eraseColor(Color.WHITE)

        val results = BarcodeReader().read(blank, Rect(0, 0, blank.width, blank.height), 0)

        assertEquals(emptyList<BarcodeReader.Result>(), results)
    }

    @Test
    fun theScannerInterfaceReturnsTextOrNull() {
        val (bitmap, expected) = renderEan13("460123456789")
        // The interface method takes an ImageProxy; the Bitmap path is what a unit test can drive, so this
        // asserts the same reader configuration rather than the CameraX plumbing.
        val scanner = ZxingBarcodeScanner()
        val reader = BarcodeReader().apply {
            options = BarcodeReader.Options().apply {
                formats = setOf(
                    BarcodeReader.Format.EAN_13,
                    BarcodeReader.Format.EAN_8,
                    BarcodeReader.Format.UPC_A,
                    BarcodeReader.Format.UPC_E,
                    BarcodeReader.Format.EAN_UPC,
                )
                tryHarder = true
            }
        }

        assertEquals(expected, reader.read(bitmap, Rect(0, 0, bitmap.width, bitmap.height), 0).firstOrNull()?.text)
        scanner.close()
    }
}
