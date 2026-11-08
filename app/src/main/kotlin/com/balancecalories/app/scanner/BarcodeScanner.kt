package com.balancecalories.app.scanner

import androidx.camera.core.ImageProxy
import zxingcpp.BarcodeReader

/**
 * Reads one barcode out of one camera frame.
 *
 * The engine sits behind this interface so a different decoder can be added later without touching the
 * screen or the resolver — the choice of zxing-cpp is recorded in ADR-017 and measured in
 * docs/02-research.md §4.1, but a decision is easier to revisit when it is one class wide.
 */
interface BarcodeScanner {
    /** Returns the first retail barcode in [image], or null when nothing was decoded. */
    fun scan(image: ImageProxy): String?

    fun close() {}
}

/**
 * zxing-cpp, restricted to the four retail formats.
 *
 * QR, DataMatrix and the industrial symbologies are deliberately **not** enabled: food packaging is full
 * of QR codes, and a decoder allowed to read them captures a marketing link instead of the product. Retail
 * EAN/UPC is the only thing this screen wants.
 */
class ZxingBarcodeScanner : BarcodeScanner {

    private val reader = BarcodeReader(
        BarcodeReader.Options().apply {
            formats = setOf(
                BarcodeReader.Format.EAN_13,
                BarcodeReader.Format.EAN_8,
                BarcodeReader.Format.UPC_A,
                BarcodeReader.Format.UPC_E,
                BarcodeReader.Format.EAN_UPC,
            )
            tryHarder = true
            tryRotate = true
            maxNumberOfSymbols = 1
        },
    )

    override fun scan(image: ImageProxy): String? {
        val results = reader.read(image)
        return results.firstOrNull()?.text?.trim()?.takeIf { it.isNotEmpty() }
    }
}
