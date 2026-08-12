package com.pinyn.ime

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PinyinDataTest {

    @Test
    fun `valid syllables count is 443`() {
        assertTrue(VALID_SYLLABLES.size in 440..450,
            "expected ~443 valid syllables, got ${VALID_SYLLABLES.size}")
    }

    @Test
    fun `common syllables present`() {
        for (s in listOf("a", "ni", "hao", "shi", "wo", "bu", "ma", "zhong", "guo", "zhuang")) {
            assertTrue(s in VALID_SYLLABLES, "expected $s in VALID_SYLLABLES")
        }
    }

    @Test
    fun `candidate map has key syllables`() {
        for (s in listOf("ni", "hao", "shi", "wo", "bu", "ma", "zhong", "guo")) {
            assertTrue(CANDIDATE_MAP.containsKey(s), "expected CANDIDATE_MAP[$s]")
        }
    }

    @Test
    fun `candidate map has multiple candidates per syllable`() {
        assertTrue(CANDIDATE_MAP["ni"]!!.size >= 3, "ni should have >=3 candidates")
        assertTrue(CANDIDATE_MAP["shi"]!!.size >= 4, "shi should have >=4 candidates")
        assertTrue(CANDIDATE_MAP["wo"]!!.size >= 2, "wo should have >=2 candidates")
    }

    @Test
    fun `candidates have non-empty marked pinyin`() {
        for ((bare, cands) in CANDIDATE_MAP) {
            for (c in cands) {
                assertTrue(c.marked.isNotEmpty(), "marked empty for $bare")
                assertEquals(bare, c.compact, "compact should match key $bare")
            }
        }
    }

    @Test
    fun `tone marks are present in marked pinyin`() {
        val niCands = CANDIDATE_MAP["ni"]!!
        // at least one candidate should have a tone mark (precomposed or combining)
        val hasToneMark = niCands.any { c ->
            c.marked.any { ch ->
                ch in "\u0304\u0301\u030c\u0300" ||               // combining
                ch in "āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ"              // precomposed
            }
        }
        assertTrue(hasToneMark, "ni candidates should include tone-marked pinyin")
    }
}
