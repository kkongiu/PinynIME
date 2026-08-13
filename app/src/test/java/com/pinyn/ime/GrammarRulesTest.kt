package com.pinyn.ime

import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GrammarRulesTest {

    @Before
    fun setup() {
        GrammarRules.setPos(TestData.pos)
    }

    private fun w(hanzi: String) = GrammarRules.Word("", hanzi)

    @Test
    fun `degree adverb before noun warns`() {
        val notes = GrammarRules.checkGrammar(listOf(w("我"), w("很"), w("学生")))
        assertEquals(1, notes.size)
        assertTrue(notes[0].message.contains("很"))
    }

    @Test
    fun `hen before adjective or verb no warning`() {
        val notes = GrammarRules.checkGrammar(listOf(w("我"), w("很"), w("好")))
        assertEquals(0, notes.size)
    }

    @Test
    fun `shi before verb warns`() {
        val notes = GrammarRules.checkGrammar(listOf(w("我"), w("是"), w("去"), w("学校")))
        assertTrue(notes.any { it.message.contains("是") })
    }

    @Test
    fun `shi before noun no warning`() {
        val notes = GrammarRules.checkGrammar(listOf(w("我"), w("是"), w("学生")))
        assertEquals(0, notes.size)
    }

    @Test
    fun `ma not at sentence end warns`() {
        val notes = GrammarRules.checkGrammar(listOf(w("你"), w("去"), w("吗"), w("学校")))
        assertTrue(notes.any { it.message.contains("吗") })
    }

    @Test
    fun `ma at sentence end no warning`() {
        val notes = GrammarRules.checkGrammar(listOf(w("你"), w("去"), w("学校"), w("吗")))
        assertEquals(0, notes.size)
    }

    @Test
    fun `correct sentence no warning`() {
        val notes = GrammarRules.checkGrammar(listOf(w("我"), w("去"), w("学校")))
        assertEquals(0, notes.size)
    }

    @Test
    fun `ambiguous multi tag POS does not trigger rules`() {
        GrammarRules.setPos(TestData.pos + ("学生" to "V/N"))
        val notes = GrammarRules.checkGrammar(listOf(w("我"), w("很"), w("学生")))
        assertEquals(0, notes.size)
    }
}
