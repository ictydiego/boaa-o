package br.unasp.boacao.util

import java.security.MessageDigest

object HashUtil {
    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun certificateHash(volunteerId: String, eventId: String): String =
        sha256("$volunteerId|$eventId")
}
