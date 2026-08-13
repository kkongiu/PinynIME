package com.pinyn.ime

import org.junit.Test
import kotlin.test.assertEquals

class LevenshteinTest {

    @Test
    fun `base distance`() {
        assertEquals(3, Levenshtein.distance("kitten", "sitting"))
    }

    @Test
    fun `adjacent swap cost 1`() {
        assertEquals(1, Levenshtein.distance("ao", "oa"))
        assertEquals(1, Levenshtein.distance("hkao", "hao"))
    }

    @Test
    fun `empty strings`() {
        assertEquals(3, Levenshtein.distance("", "abc"))
        assertEquals(3, Levenshtein.distance("abc", ""))
        assertEquals(0, Levenshtein.distance("", ""))
    }

    @Test
    fun `identical strings`() {
        assertEquals(0, Levenshtein.distance("nihao", "nihao"))
    }

    @Test
    fun `single substitution`() {
        assertEquals(1, Levenshtein.distance("hao", "haa"))
        assertEquals(1, Levenshtein.distance("ma", "na"))
    }

    @Test
    fun `deletion of stray letter`() {
        assertEquals(1, Levenshtein.distance("haoz", "hao"))
    }

    @Test
    fun `zonguo vs zhongguo is distance 2`() {
        assertEquals(2, Levenshtein.distance("zonguo", "zhongguo"))
    }
}
