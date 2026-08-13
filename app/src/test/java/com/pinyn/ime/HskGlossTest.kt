package com.pinyn.ime

import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HskGlossTest {

    @Before
    fun setup() {
        HskGloss.loadGloss(TestData.resource("data/gloss.txt"))
        HskGloss.loadReverse(TestData.resource("data/hsk-reverse.js"))
    }

    @Test
    fun `gloss for common hanzi`() {
        assertTrue(HskGloss.glossFor("是")!!.contains("to be"), HskGloss.glossFor("是").toString())
        assertEquals("good", HskGloss.glossFor("好"))
        assertEquals("China", HskGloss.glossFor("中国"))
        assertEquals("hello; hi", HskGloss.glossFor("你好"))
    }

    @Test
    fun `reverse lookup hello`() {
        val r = HskGloss.reverseLookup("hello")
        assertTrue(r.any { it.hanzi == "你好" }, r.toString())
    }

    @Test
    fun `reverse lookup water`() {
        val r = HskGloss.reverseLookup("water")
        assertTrue(r.any { it.hanzi == "水" }, r.toString())
    }

    @Test
    fun `reverse lookup coffee`() {
        val r = HskGloss.reverseLookup("coffee")
        assertTrue(r.any { it.hanzi == "咖啡" }, r.toString())
    }

    @Test
    fun `reverse lookup unknown is empty`() {
        assertEquals(emptyList(), HskGloss.reverseLookup("zzzqqq"))
    }

    @Test
    fun `reverse lookup is case insensitive`() {
        assertTrue(HskGloss.reverseLookup("Hello").any { it.hanzi == "你好" })
    }
}
