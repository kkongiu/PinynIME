package com.pinyn.ime

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PinyinEngineTest {

    // === Segmentazione DP ===

    @Test
    fun `segment basic two syllables`() {
        assertEquals(listOf("ni", "hao"), PinyinEngine.segment("nihao"))
    }

    @Test
    fun `segment three syllables`() {
        assertEquals(listOf("shi", "bu", "shi"), PinyinEngine.segment("shibushi"))
    }

    @Test
    fun `segment we men`() {
        assertEquals(listOf("wo", "men"), PinyinEngine.segment("women"))
    }

    @Test
    fun `segment long syllable zhuang`() {
        assertEquals(listOf("zhuang"), PinyinEngine.segment("zhuang"))
    }

    @Test
    fun `segment with u colon variant`() {
        // nü written as "nv" should segment as single syllable
        assertEquals(listOf("nv"), PinyinEngine.segment("nv"))
    }

    @Test
    fun `segment invalid returns input`() {
        assertEquals(listOf("xqzw"), PinyinEngine.segment("xqzw"))
    }

    @Test
    fun `segment empty returns empty`() {
        assertEquals(emptyList(), PinyinEngine.segment(""))
    }

    // === Tone marks ===

    @Test
    fun `tone mark on a priority`() {
        assertEquals("mā", PinyinEngine.addToneMark("ma", 1))
        assertEquals("má", PinyinEngine.addToneMark("ma", 2))
        assertEquals("mǎ", PinyinEngine.addToneMark("ma", 3))
        assertEquals("mà", PinyinEngine.addToneMark("ma", 4))
    }

    @Test
    fun `tone mark on o priority`() {
        assertEquals("wǒ", PinyinEngine.addToneMark("wo", 3))
        assertEquals("lóng", PinyinEngine.addToneMark("long", 2))
    }

    @Test
    fun `tone mark on e priority`() {
        assertEquals("hěn", PinyinEngine.addToneMark("hen", 3))
    }

    @Test
    fun `tone mark on last vowel`() {
        assertEquals("nǐ", PinyinEngine.addToneMark("ni", 3))
        assertEquals("shì", PinyinEngine.addToneMark("shi", 4))
        assertEquals("liǔ", PinyinEngine.addToneMark("liu", 3))
    }

    @Test
    fun `tone mark neutral returns bare`() {
        assertEquals("ha", PinyinEngine.addToneMark("ha", 0))
        assertEquals("ha", PinyinEngine.addToneMark("ha", 5))
    }

    @Test
    fun `tone mark on ue and ui`() {
        assertEquals("duì", PinyinEngine.addToneMark("dui", 4))
        assertEquals("xué", PinyinEngine.addToneMark("xue", 2))
    }

    // === Process ===

    @Test
    fun `process nihao returns candidates`() {
        val result = PinyinEngine.process("nihao")
        assertTrue(result.isNotEmpty(), "expected candidates for nihao")
        assertTrue(result.any { it.compact == "ni" }, "expected ni candidates")
    }

    @Test
    fun `process wo returns wo candidates`() {
        val result = PinyinEngine.process("wo")
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.compact == "wo" })
        // should include 我 (wo3) as hanzi
        assertTrue(result.any { it.hanzi == "我" }, "expected hanzi 我 for wo")
    }

    @Test
    fun `process invalid returns empty`() {
        assertEquals(emptyList(), PinyinEngine.process(""))
        assertEquals(emptyList(), PinyinEngine.process("?!?"))
    }

    @Test
    fun `process shi includes verb to be`() {
        val result = PinyinEngine.process("shi")
        assertTrue(result.any { it.hanzi == "是" }, "expected hanzi 是 for shi")
    }
}
