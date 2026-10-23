package com.koala.cartes

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter

object Codes {
    val formats = listOf(
        "CODE_128", "EAN_13", "EAN_8", "UPC_A", "UPC_E", "CODE_39", "CODE_93",
        "ITF", "CODABAR", "QR_CODE", "PDF_417", "AZTEC", "DATA_MATRIX",
    )
    private val twoD = setOf("QR_CODE", "PDF_417", "AZTEC", "DATA_MATRIX")

    fun is2D(f: String) = f in twoD

    fun label(f: String) = when (f) {
        "CODE_128" -> "Code 128"
        "EAN_13" -> "EAN-13"
        "EAN_8" -> "EAN-8"
        "UPC_A" -> "UPC-A"
        "UPC_E" -> "UPC-E"
        "CODE_39" -> "Code 39"
        "CODE_93" -> "Code 93"
        "ITF" -> "ITF"
        "CODABAR" -> "Codabar"
        "QR_CODE" -> "QR code"
        "PDF_417" -> "PDF417"
        "AZTEC" -> "Aztec"
        "DATA_MATRIX" -> "Data Matrix"
        else -> f
    }

    fun fromMlKit(f: Int): String = when (f) {
        Barcode.FORMAT_CODE_128 -> "CODE_128"
        Barcode.FORMAT_EAN_13 -> "EAN_13"
        Barcode.FORMAT_EAN_8 -> "EAN_8"
        Barcode.FORMAT_UPC_A -> "UPC_A"
        Barcode.FORMAT_UPC_E -> "UPC_E"
        Barcode.FORMAT_CODE_39 -> "CODE_39"
        Barcode.FORMAT_CODE_93 -> "CODE_93"
        Barcode.FORMAT_ITF -> "ITF"
        Barcode.FORMAT_CODABAR -> "CODABAR"
        Barcode.FORMAT_QR_CODE -> "QR_CODE"
        Barcode.FORMAT_PDF417 -> "PDF_417"
        Barcode.FORMAT_AZTEC -> "AZTEC"
        Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
        else -> "CODE_128"
    }

    private fun checksumOk(d: String): Boolean {
        if (d.length < 2 || !d.all { it.isDigit() }) return false
        val body = d.dropLast(1).reversed()
        var sum = 0
        body.forEachIndexed { i, c -> sum += (c - '0') * (if (i % 2 == 0) 3 else 1) }
        return (10 - sum % 10) % 10 == d.last() - '0'
    }

    /** Devine le type de code pour une saisie manuelle. */
    fun guess(number: String): String {
        val n = number.trim()
        return when {
            n.length == 13 && checksumOk(n) -> "EAN_13"
            n.length == 8 && checksumOk(n) -> "EAN_8"
            n.length == 12 && checksumOk(n) -> "UPC_A"
            else -> "CODE_128"
        }
    }

    fun render(text: String, format: String, width: Int): Bitmap? {
        if (text.isBlank()) return null
        val f = runCatching { BarcodeFormat.valueOf(format) }.getOrDefault(BarcodeFormat.CODE_128)
        return encode(text, f, width)
            ?: if (f != BarcodeFormat.CODE_128 && !is2D(format)) encode(text, BarcodeFormat.CODE_128, width) else null
    }

    private fun encode(text: String, f: BarcodeFormat, width: Int): Bitmap? = try {
        val two = is2D(f.name)
        val w = width.coerceIn(200, 1600)
        val h = if (two) w else (w * 0.38f).toInt()
        val hints = mutableMapOf<EncodeHintType, Any>(EncodeHintType.MARGIN to if (two) 1 else 6)
        if (two) hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        val m = MultiFormatWriter().encode(text, f, w, h, hints)
        val px = IntArray(m.width * m.height)
        for (y in 0 until m.height) {
            val o = y * m.width
            for (x in 0 until m.width) px[o + x] = if (m[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
    } catch (e: Exception) {
        null
    }
}
