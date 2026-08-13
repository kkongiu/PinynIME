package com.pinyn.ime

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentenceNetTest {

    private fun loadModel(): SentenceNet {
        val net = SentenceNet()
        assertTrue(net.loadFromString(TestData.resource("data/sentence-net.json")), "model should load")
        assertTrue(net.isLoaded())
        return net
    }

    @Test
    fun `model loads with expected dimensions`() {
        val net = loadModel()
        val m = net.getModelForTest()
        assertNotNull(m)
        assertEquals(2, m.K)
        assertEquals(3000, m.V)
        assertEquals(32, m.d)
        assertEquals(96, m.H1)
        assertEquals(3000, m.vocab.size)
        assertEquals(3000 * 32, m.E.size)
        assertEquals((2 * 32) * 96, m.W1.size)
        assertEquals(96 * 3000, m.W2.size)
        assertEquals(96, m.b1.size)
        assertEquals(3000, m.b2.size)
    }

    @Test
    fun `sentence too short returns null`() {
        val net = loadModel()
        assertNull(net.scoreSentence(listOf("wo", "qu")))
    }

    @Test
    fun `correct short sentence is not flagged`() {
        val net = loadModel()
        val sc = net.scoreSentence("wo qu xuexiao".split(" "))
        assertNotNull(sc)
        assertFalse(sc!!.flagged)
    }

    @Test
    fun `correct long sentence is not flagged`() {
        val net = loadModel()
        val sc = net.scoreSentence("ta mingtian qu shangdian mai shu".split(" "))
        assertNotNull(sc)
        assertFalse(sc!!.flagged)
    }

    @Test
    fun `ungrammatical sentence is flagged`() {
        val net = loadModel()
        val sc = net.scoreSentence("ta zhe ge xihuan hen shu".split(" "))
        assertNotNull(sc)
        assertTrue(sc!!.flagged)
    }

    @Test
    fun `score in expected range`() {
        val net = loadModel()
        val sc = net.scoreSentence("wo xihuan zhongguo".split(" "))
        assertNotNull(sc)
        assertTrue(sc!!.avgNegLog > 0 && sc.avgNegLog < 12, "avgNegLog=${sc.avgNegLog}")
        assertTrue(sc.perplexity > 1)
    }
}
