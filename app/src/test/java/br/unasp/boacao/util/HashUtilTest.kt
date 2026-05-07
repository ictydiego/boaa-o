package br.unasp.boacao.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HashUtilTest {

    @Test
    fun sha256_emptyInput_returnsKnownDigest() {
        // SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            HashUtil.sha256("")
        )
    }

    @Test
    fun sha256_knownVector_returnsExpected() {
        // SHA-256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            HashUtil.sha256("abc")
        )
    }

    @Test
    fun certificateHash_concatenatesWithSeparator() {
        val expected = HashUtil.sha256("user-1|event-9")
        assertEquals(expected, HashUtil.certificateHash("user-1", "event-9"))
    }

    @Test
    fun certificateHash_differentVolunteersProduceDifferentHashes() {
        val a = HashUtil.certificateHash("v1", "e1")
        val b = HashUtil.certificateHash("v2", "e1")
        assertNotEquals(a, b)
    }

    @Test
    fun certificateHash_differentEventsProduceDifferentHashes() {
        val a = HashUtil.certificateHash("v1", "e1")
        val b = HashUtil.certificateHash("v1", "e2")
        assertNotEquals(a, b)
    }

    @Test
    fun certificateHash_isDeterministic() {
        val first = HashUtil.certificateHash("abc", "xyz")
        val second = HashUtil.certificateHash("abc", "xyz")
        assertEquals(first, second)
    }

    @Test
    fun certificateHash_resistsTrivialReorder() {
        // separator prevents collisions like ("ab","c") vs ("a","bc")
        val a = HashUtil.certificateHash("ab", "c")
        val b = HashUtil.certificateHash("a", "bc")
        assertNotEquals(a, b)
    }
}
