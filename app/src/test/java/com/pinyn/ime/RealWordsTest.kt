package com.pinyn.ime

import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RealWordsTest {

    @Before
    fun setup() {
        val res = javaClass.classLoader?.getResource("data/words.txt")
            ?: throw IllegalStateException("words.txt test resource not found")
        val content = File(res.toURI()).readText()
        WordIndex.loadFromString(content)
    }

    @Test
    fun `nihao phrase matches as word`() {
        val result = PinyinEngine.process("nihao")
        // first candidate should be the phrase "nǐ hǎo"
        val first = result.first()
        assertEquals("nihao", first.compact)
        assertEquals("nǐhǎo", first.marked)
        assertEquals("你好", first.hanzi)
    }

    @Test
    fun `zhongguo phrase with hanzi`() {
        val result = PinyinEngine.process("zhongguo")
        val phrase = result.firstOrNull { it.hanzi == "中国" }
        assertTrue(phrase != null, "expected 中国 phrase, got $result")
        assertEquals("zhōngguó", phrase.marked)
    }

    @Test
    fun `wo single syllable common hanzi first`() {
        val result = PinyinEngine.process("wo")
        assertTrue(result.any { it.hanzi == "我" }, "expected 我 for wo")
        // wǒ should be present
        assertTrue(result.any { it.marked == "wǒ" }, "expected wǒ")
    }

    @Test
    fun `hao gives good first`() {
        val result = PinyinEngine.process("hao")
        assertTrue(result.any { it.hanzi == "好" }, "expected 好 for hao")
        assertTrue(result.any { it.marked == "hǎo" }, "expected hǎo")
    }

    @Test
    fun `shangwu phrase`() {
        val result = PinyinEngine.process("shangwu")
        val phrase = result.firstOrNull { it.hanzi == "上午" }
        assertTrue(phrase != null, "expected 上午, got $result")
    }

    @Test
    fun `xuexi phrase`() {
        val result = PinyinEngine.process("xuexi")
        val phrase = result.firstOrNull { it.hanzi == "学习" }
        assertTrue(phrase != null, "expected 学习")
    }

    @Test
    fun `numbers to marks conversion`() {
        assertEquals("nǐhǎo", PinyinEngine.numbersToMarks("ni3 hao3"))
        assertEquals("zhōngguó", PinyinEngine.numbersToMarks("zhong1 guo2"))
        assertEquals("shàngwǔ", PinyinEngine.numbersToMarks("shang4 wu3"))
    }
}
