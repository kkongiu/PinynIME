package com.pinyn.ime

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PinyinUtilsTest {

    @Test
    fun `extract tone from diacritic`() {
        assertEquals(PinyinUtils.ToneResult("ni", 3), PinyinUtils.extractTone("nǐ"))
    }

    @Test
    fun `extract tone from number`() {
        assertEquals(PinyinUtils.ToneResult("hao", 3), PinyinUtils.extractTone("hao3"))
        assertEquals(PinyinUtils.ToneResult("ma", 0), PinyinUtils.extractTone("ma0"))
    }

    @Test
    fun `add tone mark`() {
        assertEquals("nǐ", PinyinUtils.addToneMark("ni", 3))
    }

    @Test
    fun `convert between formats`() {
        assertEquals("ni3", PinyinUtils.toToneNumber("nǐ"))
        assertEquals("hǎo", PinyinUtils.toToneMark("hao3"))
    }

    @Test
    fun `tone placement on iu ui diphthongs`() {
        assertEquals("qiú", PinyinUtils.toToneMark("qiu2"))
        assertEquals("liù", PinyinUtils.toToneMark("liu4"))
        assertEquals("jiǔ", PinyinUtils.toToneMark("jiu3"))
        assertEquals("guǐ", PinyinUtils.toToneMark("gui3"))
        assertEquals("shuǐ", PinyinUtils.toToneMark("shui3"))
        assertEquals("yǒu", PinyinUtils.toToneMark("you3"))
    }

    @Test
    fun `tokenize preserving non-latin text`() {
        val toks = PinyinUtils.tokenize("ciao ni hao! 你好")
        assertEquals(6, toks.size)
        assertEquals("ciao", toks[0].text)
        assertTrue(toks[0].isLatin)
        assertEquals("hao", toks[4].text)
        assertFalse(toks[5].isLatin)
    }

    @Test
    fun `stripTone removes all tones`() {
        assertEquals("kafei", PinyinUtils.stripTone("kāfēi"))
        assertEquals("zhongguo", PinyinUtils.stripTone("zhōngguó"))
        assertEquals("ni hao", PinyinUtils.stripTone("nǐ hǎo"))
        assertEquals("hao", PinyinUtils.stripTone("hao3"))
    }

    @Test
    fun `case insensitive uppercase is pinyin`() {
        val t = PinyinUtils.tokenize("Wo3 ni Hao")
        assertTrue(t.any { it.text == "wo3" && it.isLatin }, t.toString())
        assertTrue(t.any { it.text == "hao" && it.isLatin })
        assertEquals("wǒ", PinyinUtils.toToneMark("Wo3"))
        assertEquals("wǒ", PinyinUtils.convertToneNumbers("Wo3"))
    }

    @Test
    fun `tone numbered token recognition`() {
        assertTrue(PinyinUtils.isToneNumbered("hao3"))
        assertFalse(PinyinUtils.isToneNumbered("hao"))
    }

    @Test
    fun `tokenize tone digits as part of token`() {
        val toks = PinyinUtils.tokenize("ni3 hao3")
        assertEquals(listOf("ni3", " ", "hao3"), toks.map { it.text })
        assertTrue(toks[0].isLatin)
    }

    @Test
    fun `convertToneNumbers numbers to diacritics`() {
        assertEquals("nǐ hǎo", PinyinUtils.convertToneNumbers("ni3 hao3"))
        assertEquals("ma", PinyinUtils.convertToneNumbers("ma0"))
        assertEquals("Hello nǐ", PinyinUtils.convertToneNumbers("Hello ni3"))
    }

    @Test
    fun `hasToneMark detection`() {
        assertTrue(PinyinUtils.hasToneMark("nǐ"))
        assertFalse(PinyinUtils.hasToneMark("ni"))
    }

    @Test
    fun `normalize v to u umlaut`() {
        assertEquals("lü", PinyinUtils.normalizeVowelV("lv"))
        assertEquals("lü4", PinyinUtils.toToneNumber("lǜ"))
    }
}
