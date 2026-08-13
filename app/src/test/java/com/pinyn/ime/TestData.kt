package com.pinyn.ime

import java.io.File

object TestData {

    fun resource(name: String): String {
        val res = javaClass.classLoader?.getResource(name)
            ?: throw IllegalStateException("test resource not found: $name")
        return File(res.toURI()).readText()
    }

    val syllables: Set<String> = VALID_SYLLABLES

    val hskWords: List<HskWord> by lazy { HskData.parseHsk(resource("data/hsk-3.0.js")) }

    val pos: Map<String, String> by lazy { HskData.parsePos(resource("data/hsk-pos.js")) }

    fun engine(level: Double = 9.0): CorrectionEngine =
        CorrectionEngine(syllables, hskWords, level)

    fun toneNet(): ToneNet {
        val net = ToneNet()
        check(net.loadFromString(resource("data/tone-net-data.js")))
        return net
    }
}
