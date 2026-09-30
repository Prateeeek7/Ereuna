package com.researchradar.core.model

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10

private val SUPERSCRIPT = mapOf(
    '-' to '⁻', '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
    '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
)

/**
 * A reported number the way a paper would print it: whole numbers plain, ordinary
 * decimals with up to four significant digits, very small or very large values in
 * scientific notation ("7.4 × 10⁻⁴").
 */
fun formatReported(value: Double): String {
    if (value == 0.0 || value.isNaN() || value.isInfinite()) return value.toString().removeSuffix(".0")
    val magnitude = abs(value)
    if (magnitude < 1e-3 || magnitude >= 1e7) {
        val exponent = floor(log10(magnitude)).toInt()
        val mantissa = value / Math.pow(10.0, exponent.toDouble())
        val m = trimDecimal(String.format(Locale.ROOT, "%.3g", mantissa))
        val exp = exponent.toString().map { SUPERSCRIPT[it] ?: it }.joinToString("")
        return if (m == "1") "10$exp" else "$m × 10$exp"
    }
    if (value == floor(value)) return value.toLong().toString()
    return trimDecimal(String.format(Locale.ROOT, "%.4g", value))
}

private fun trimDecimal(text: String): String =
    if ('.' in text && 'e' !in text && 'E' !in text) text.trimEnd('0').trimEnd('.') else text
