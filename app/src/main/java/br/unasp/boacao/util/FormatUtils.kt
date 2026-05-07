package br.unasp.boacao.util

object FormatUtils {

    fun formatDocument(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when (digits.length) {
            11 -> "${digits.substring(0, 3)}.${digits.substring(3, 6)}.${digits.substring(6, 9)}-${digits.substring(9, 11)}"
            14 -> "${digits.substring(0, 2)}.${digits.substring(2, 5)}.${digits.substring(5, 8)}/${digits.substring(8, 12)}-${digits.substring(12, 14)}"
            else -> raw
        }
    }

    fun formatHours(value: Double): String {
        val asInt = value.toInt()
        return if (value == asInt.toDouble()) "$asInt" else "%.1f".format(value)
    }
}
