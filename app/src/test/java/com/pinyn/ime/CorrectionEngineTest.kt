package com.pinyn.ime

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CorrectionEngineTest {

    private fun engine(level: Double = 9.0) = TestData.engine(level)

    // === Bigrams ===

    @Test
    fun `bigrams built from HSK vocabulary`() {
        val e = engine()
        assertTrue((e.bigrams["ni hao"] ?: 0) >= 1, "ni hao expected")
        assertTrue((e.bigrams["zai jian"] ?: 0) >= 1, "zai jian expected")
    }

    @Test
    fun `contextual ranking prev zai privileges jian`() {
        val sugg = engine().rankSuggestions("jianx", "zai")
        assertTrue(sugg.contains("jian"), "jian expected on top: $sugg")
    }

    @Test
    fun `non contextual ranking ok`() {
        val sugg = engine().rankSuggestions("haoz", null)
        assertTrue(sugg.contains("hao"))
    }

    @Test
    fun `hsk filter words above level+1 do not weigh`() {
        val engL1 = engine(1.0)
        val sugg = engL1.rankSuggestions("dian", null)
        assertTrue(sugg.contains("dian"))
    }

    @Test
    fun `setLevel updates filter`() {
        val eng = engine()
        eng.setLevel(1.0)
        assertTrue(eng.rankSuggestions("dian", null).isNotEmpty())
    }

    // === Segmentation ===

    @Test
    fun `segment nihao to ni hao`() {
        assertEquals(listOf("ni", "hao"), engine().segment("nihao"))
    }

    @Test
    fun `segment zaijian without space`() {
        val seg = engine().segment("zaijian")
        assertTrue(seg != null && seg.joinToString("") == "zaijian")
        assertTrue(seg!!.contains("jian"))
    }

    @Test
    fun `segment invalid returns null`() {
        assertNull(engine().segment("xzq"))
    }

    @Test
    fun `segmentWords groups dictionary words`() {
        val e = engine()
        assertEquals(listOf("wo", "qu", "xuexiao"), e.segmentWords(listOf("wo", "qu", "xue", "xiao")))
        assertEquals(listOf("ta", "zhe", "ge", "xihuan", "hen", "shu"),
            e.segmentWords(listOf("ta", "zhe", "ge", "xi", "huan", "hen", "shu")))
    }

    @Test
    fun `segmentWords keeps unknown syllables as single`() {
        val e = engine()
        assertEquals(listOf("hello", "world"), e.segmentWords(listOf("hello", "world")))
    }

    // === Word suggestions ===

    @Test
    fun `findWordSuggestions zonguo approaches zhong guo`() {
        val sugg = engine().findWordSuggestions("zonguo", 2)
        assertTrue(sugg.contains("zhōng guó"), sugg.toString())
    }

    @Test
    fun `findWordSuggestions nihao top is ni hao`() {
        val sugg = engine().findWordSuggestions("nihao", 2)
        assertEquals("nǐ hǎo", sugg.first(), sugg.toString())
    }

    // === Auto accents ===

    @Test
    fun `addAccentsToPhrase ni hao`() {
        assertEquals(listOf("nǐ", "hǎo"), engine().addAccentsToPhrase(listOf("ni", "hao")))
    }

    @Test
    fun `addAccentsToPhrase zai jian`() {
        assertEquals(listOf("zài", "jiàn"), engine().addAccentsToPhrase(listOf("zai", "jian")))
    }

    @Test
    fun `accentSingle wo`() {
        assertEquals("wǒ", engine().accentSingle("wo"))
    }

    @Test
    fun `accentSingle xie`() {
        assertEquals("xiē", engine().accentSingle("xie"))
    }

    @Test
    fun `accentRun joins words`() {
        val e = engine()
        assertEquals(listOf(CorrectionEngine.Segment(2, "zhōngguó")), e.accentRun(listOf("zhong", "guo")))
        assertEquals(listOf(CorrectionEngine.Segment(2, "nǐhǎo")), e.accentRun(listOf("ni", "hao")))
    }

    @Test
    fun `accentRun separates different words`() {
        val expected = listOf(
            CorrectionEngine.Segment(1, "wǒ"),
            CorrectionEngine.Segment(1, "ài"),
            CorrectionEngine.Segment(1, "nǐ"),
        )
        assertEquals(expected, engine().accentRun(listOf("wo", "ai", "ni")))
    }

    @Test
    fun `non pinyin word stays unchanged`() {
        val e = engine()
        assertEquals("hello", e.accentSingle("hello"))
        assertEquals(listOf("hello", "world"), e.addAccentsToPhrase(listOf("hello", "world")))
    }

    // === Tone suspicion ===

    @Test
    fun `toneSuspect flags rare tones`() {
        val e = engine()
        assertTrue(e.toneSuspect("hao", 1))     // hao1 raro
        assertTrue(!e.toneSuspect("hao", 3))    // hǎo dominante
        assertTrue(e.toneSuspect("ma", 4))      // mà raro
        assertTrue(!e.toneSuspect("shi", 2))    // bilanciato
        assertTrue(!e.toneSuspect("zhong", 3))  // 种 abbastanza comune
    }

    // === pinyinLevel ===

    @Test
    fun `pinyinLevel resolves accented words`() {
        val e = engine()
        assertEquals(1.0, e.pinyinLevel("zhōng guó"))
        assertEquals(4.0, e.pinyinLevel("yī zài"))
        assertEquals(4.0, e.pinyinLevel("yī lǜ"))
    }

    @Test
    fun `pinyinLevel resolves single syllable`() {
        assertEquals(1.0, engine().pinyinLevel("hǎo"))
    }

    @Test
    fun `pinyinLevel unknown word is null`() {
        assertNull(engine().pinyinLevel("hello world"))
    }

    // === toneVariants ===

    @Test
    fun `toneVariants he offers he and he`() {
        val v = engine().toneVariants("he")
        assertTrue(v.contains("hé"), v.toString())
        assertTrue(v.contains("hē"), v.toString())
    }

    @Test
    fun `toneVariants wo top two`() {
        val v = engine().toneVariants("wo")
        assertEquals(listOf("wǒ", "wò"), v.take(2), v.toString())
    }

    @Test
    fun `accentChoice returns valid tone and share`() {
        val c = engine().accentChoice("wo")
        assertTrue(c.tone in 0..4)
        assertTrue(c.share > 0 && c.share <= 1)
    }

    // === ToneNet integration (hybrid accents) ===

    @Test
    fun `accentRun without predictor uses heuristic`() {
        val r = engine().accentRun(listOf("wo", "qu", "shang", "hai"))
            .joinToString(" ") { it.text }
        assertTrue(r.startsWith("wǒ"), r)
    }

    @Test
    fun `accentRun with predictor accents contextual tones`() {
        val net = TestData.toneNet()
        val eng = engine()
        eng.setTonePredictor { syllables -> net.predictTones(syllables) }
        val r = eng.accentRun(listOf("wo", "qu", "shang", "hai"))
            .joinToString(" ") { it.text }
        assertEquals("wǒ qù shànghǎi", r)
    }

    @Test
    fun `predictor does not break non pinyin words`() {
        val net = TestData.toneNet()
        val eng = engine()
        eng.setTonePredictor { syllables -> net.predictTones(syllables) }
        val r = eng.accentRun(listOf("hello", "world"))
            .joinToString(" ") { it.text }
        assertEquals("hello world", r)
    }
}
