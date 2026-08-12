package com.pinyn.ime

import org.junit.Test
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ToneNetTest {

    private fun loadModel(): ToneNet {
        val res = javaClass.classLoader?.getResource("data/tone-net-data.js")
            ?: throw IllegalStateException("test resource not found")
        val content = File(res.toURI()).readText()
        val net = ToneNet()
        assertTrue(net.loadFromString(content), "model should load")
        assertTrue(net.isLoaded(), "model should be loaded")
        return net
    }

    @Test
    fun `model loads and has expected dimensions`() {
        val net = loadModel()
        val m = net.getModelForTest()
        assertNotNull(m)
        assertEquals(5, m.N)
        assertEquals(444, m.vocab.size)
        assertEquals(32, m.embDim)
        assertEquals(128, m.h1)
        assertEquals(64, m.h2)
        assertEquals(444 * 32, m.E.size)
        assertEquals((2 * 5 + 1) * 32 * 128, m.W1.size)
        assertEquals(128 * 64, m.W2.size)
        assertEquals(64 * 5, m.W3.size)
    }

    @Test
    fun `predict ni hao gives 3 3`() {
        val net = loadModel()
        val tones = net.predictTones(arrayOf("ni", "hao"))
        assertContentEquals(intArrayOf(3, 3), tones)
    }

    @Test
    fun `predict wo gives 3`() {
        val net = loadModel()
        assertContentEquals(intArrayOf(3), net.predictTones(arrayOf("wo")))
    }

    @Test
    fun `predict shi gives 4`() {
        val net = loadModel()
        assertContentEquals(intArrayOf(4), net.predictTones(arrayOf("shi")))
    }

    @Test
    fun `predict zhong guo gives 1 2`() {
        val net = loadModel()
        assertContentEquals(intArrayOf(1, 2), net.predictTones(arrayOf("zhong", "guo")))
    }

    @Test
    fun `predict wo ai ni gives 3 4 4`() {
        val net = loadModel()
        assertContentEquals(intArrayOf(3, 4, 4), net.predictTones(arrayOf("wo", "ai", "ni")))
    }

    @Test
    fun `predict xue xi gives 2 2`() {
        val net = loadModel()
        assertContentEquals(intArrayOf(2, 2), net.predictTones(arrayOf("xue", "xi")))
    }

    @Test
    fun `predict shi bu shi gives 4 0 4 (context neutral bu)`() {
        val net = loadModel()
        assertContentEquals(intArrayOf(4, 0, 4), net.predictTones(arrayOf("shi", "bu", "shi")))
    }

    @Test
    fun `predict is deterministic`() {
        val net = loadModel()
        val a = net.predictTones(arrayOf("ni", "hao", "ma"))
        val b = net.predictTones(arrayOf("ni", "hao", "ma"))
        assertContentEquals(a, b)
    }

    @Test
    fun `predict returns valid tone range`() {
        val net = loadModel()
        val tones = net.predictTones(arrayOf("a", "ai", "an", "ang", "ba", "zhuang"))
        assertNotNull(tones)
        assertTrue(tones.all { it in 0..4 }, "tones should be 0-4, got ${tones.joinToString()}")
    }
}
