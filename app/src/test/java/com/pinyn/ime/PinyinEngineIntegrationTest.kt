package com.pinyn.ime

import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PinyinEngineIntegrationTest {

    @Before
    fun setup() {
        WordIndex.loadFromString(TestData.resource("data/words.txt"))
        PinyinEngine.initData(
            toneNetJson = TestData.resource("data/tone-net-data.js"),
            hskJs = TestData.resource("data/hsk-3.0.js"),
            sentenceNetJson = TestData.resource("data/sentence-net.json"),
            posJs = TestData.resource("data/hsk-pos.js"),
        )
        PinyinEngine.initGlossData(
            TestData.resource("data/gloss.txt"),
            TestData.resource("data/hsk-reverse.js"),
        )
    }

    @After
    fun teardown() {
        PinyinEngine.reset()
    }

    @Test
    fun `typo hhao corrects to hao`() {
        val r = PinyinEngine.process("hhao")
        assertTrue(r.any { it.hanzi == "好" || it.marked == "hǎo" }, r.toString())
    }

    @Test
    fun `typo produces corrected candidates not raw typo`() {
        val r = PinyinEngine.process("hkao")
        assertTrue(r.isNotEmpty())
        assertTrue(r.none { it.compact == "hkao" }, r.toString())
        assertTrue(r.any { it.compact in VALID_SYLLABLES }, r.toString())
    }

    @Test
    fun `word typo zonguo suggests zhongguo with hanzi`() {
        val r = PinyinEngine.process("zonguo")
        assertTrue(r.any { it.hanzi == "中国" }, r.toString())
    }

    @Test
    fun `exact word still matches hanzi first`() {
        val r = PinyinEngine.process("nihao")
        assertEquals("你好", r.first().hanzi)
    }

    @Test
    fun `sentence lm flags unusual structure`() {
        val a = PinyinEngine.analyze("ta zhe ge xihuan hen shu")
        assertTrue(a.warnings.isNotEmpty(), a.warnings.toString())
    }

    @Test
    fun `grammar warning for degree adverb before noun`() {
        val a = PinyinEngine.analyze("wo hen xuesheng")
        assertTrue(a.warnings.any { it.contains("很") }, a.warnings.toString())
    }

    @Test
    fun `grammar clean sentence has no grammar warning`() {
        val a = PinyinEngine.analyze("wo qu xuexiao")
        assertTrue(a.warnings.none { it.contains("很") || it.contains("是") || it.contains("吗") }, a.warnings.toString())
    }

    @Test
    fun `candidate gloss for shi`() {
        val r = PinyinEngine.process("shi")
        val shi = r.firstOrNull { it.hanzi == "是" }
        assertTrue(shi != null && shi.gloss.isNotEmpty(), r.toString())
    }

    @Test
    fun `no candidate is shown without hanzi`() {
        for (syl in listOf("shi", "shou", "shu", "a", "ang", "ha", "ne", "wo", "ni", "hao", "hang", "en", "ou")) {
            val r = PinyinEngine.process(syl)
            assertTrue(r.none { it.hanzi.isEmpty() }, "$syl -> $r")
        }
    }

    @Test
    fun `rare syllables get override hanzi`() {
        val overrides = mapOf(
            "ang" to "昂", "bin" to "宾", "ga" to "尬", "ha" to "哈", "hang" to "行",
            "jiong" to "囧", "me" to "么", "min" to "民", "nu" to "努", "nüe" to "虐",
            "ou" to "欧", "pou" to "剖", "sen" to "森", "shei" to "谁", "zen" to "怎",
        )
        for ((syl, hanzi) in overrides) {
            val r = PinyinEngine.process(syl)
            assertTrue(r.isNotEmpty(), "$syl produced no candidates")
            assertTrue(r.any { it.hanzi == hanzi }, "$syl -> $r (expected $hanzi)")
        }
    }

    @Test
    fun `v is normalized to u umlaut`() {
        val r = PinyinEngine.process("nv")
        assertTrue(r.any { it.hanzi == "女" }, r.toString())
        val lv = PinyinEngine.process("lv")
        assertTrue(lv.any { it.hanzi == "绿" }, lv.toString())
    }

    @Test
    fun `custom term from user dictionary is findable`() {
        PinyinEngine.applySettings(7.5, setOf("测试词\tce4 shi4 ci2"))
        val r = PinyinEngine.process("ceshici")
        assertTrue(r.any { it.hanzi == "测试词" }, r.toString())
    }

    @Test
    fun `applySettings with no custom terms still works`() {
        PinyinEngine.applySettings(3.0, emptySet())
        val r = PinyinEngine.process("nihao")
        assertEquals("你好", r.first().hanzi)
    }

    @Test
    fun `english reverse mode with asterisk`() {
        val a = PinyinEngine.analyze("*hello")
        assertTrue(a.candidates.any { it.hanzi == "你好" }, a.candidates.toString())
        val nihao = a.candidates.first { it.hanzi == "你好" }
        assertTrue(nihao.gloss.isNotEmpty())
        assertEquals("nǐhǎo", nihao.marked)
    }

    @Test
    fun `english reverse mode empty word returns empty`() {
        assertEquals(emptyList(), PinyinEngine.analyze("*").candidates)
        assertEquals(emptyList(), PinyinEngine.analyze("*123").candidates)
    }
}
