package com.pinyn.ime

import android.content.res.AssetManager
import org.json.JSONObject

class ToneNet {

    data class Model(
        val N: Int,
        val vocab: List<String>,
        val vocabIndex: Map<String, Int>,
        val embDim: Int,
        val h1: Int,
        val h2: Int,
        val E: FloatArray,    // [V * embDim]
        val W1: FloatArray,   // [(2N+1)*embDim * h1]
        val W2: FloatArray,   // [h1 * h2]
        val W3: FloatArray,   // [h2 * 5]
        val b1: FloatArray,   // [h1]
        val b2: FloatArray,   // [h2]
        val b3: FloatArray    // [5]
    )

    private var model: Model? = null

    /** Carica da AssetManager (produzione) */
    fun loadFromAssets(assets: AssetManager): Boolean {
        return try {
            val json = assets.open("data/tone-net-data.js").bufferedReader().readText()
            loadFromString(json)
        } catch (e: Exception) {
            android.util.Log.e("ToneNet", "load from assets failed", e)
            false
        }
    }

    /** Carica da stringa JSON (testabile) */
    fun loadFromString(jsonContent: String): Boolean {
        return try {
            val start = jsonContent.indexOf('{')
            if (start < 0) return false
            var depth = 0; var end = start
            for (i in start until jsonContent.length) {
                when (jsonContent[i]) {
                    '{' -> depth++
                    '}' -> { depth--; if (depth == 0) { end = i + 1; break } }
                }
            }
            val root = JSONObject(jsonContent.substring(start, end))
            model = build(root)
            true
        } catch (e: Exception) {
            android.util.Log.e("ToneNet", "load from string failed", e)
            false
        }
    }

    fun isLoaded(): Boolean = model != null

    /** Accesso al modello per test */
    fun getModelForTest(): Model? = model

    private fun build(root: JSONObject): Model {
        val N = root.getInt("N")
        val embDim = root.getInt("emb_dim")
        val h1 = root.getInt("h1")
        val h2 = root.getInt("h2")

        val vocabArr = root.getJSONArray("vocab")
        val vocab = (0 until vocabArr.length()).map { vocabArr.getString(it) }
        val vocabIndex = vocab.withIndex().associate { it.value to it.index }

        val E = dequantize(root.getJSONObject("E"), embDim, true)
        val W1 = dequantize(root.getJSONObject("W1"), 0, false)
        val W2 = dequantize(root.getJSONObject("W2"), 0, false)
        val W3 = dequantize(root.getJSONObject("W3"), 0, false)
        val b1 = floatArray(root.getJSONArray("b1"))
        val b2 = floatArray(root.getJSONArray("b2"))
        val b3 = floatArray(root.getJSONArray("b3"))

        return Model(N, vocab, vocabIndex, embDim, h1, h2, E, W1, W2, W3, b1, b2, b3)
    }

    private fun floatArray(arr: org.json.JSONArray): FloatArray {
        return FloatArray(arr.length()) { arr.getDouble(it).toFloat() }
    }

    private fun intArray(arr: org.json.JSONArray): IntArray {
        return IntArray(arr.length()) { arr.getInt(it) }
    }

    private fun dequantize(layer: JSONObject, dim: Int, perRow: Boolean): FloatArray {
        val vArr = intArray(layer.getJSONArray("v"))
        val n = vArr.size
        val out = FloatArray(n)

        if (perRow && dim > 0) {
            val scales = floatArray(layer.getJSONArray("s"))
            for (r in scales.indices) {
                val sc = scales[r]
                val base = r * dim
                for (j in 0 until dim) out[base + j] = vArr[base + j] * sc
            }
        } else {
            val sc = when (val sv = layer.get("s")) {
                is Double -> sv.toFloat()
                is Int -> sv.toFloat()
                is Long -> sv.toFloat()
                is java.math.BigDecimal -> sv.toFloat()
                is Float -> sv
                else -> 0f
            }
            for (i in 0 until n) out[i] = vArr[i] * sc
        }
        return out
    }

    /** Forward pass: predice il tono (1-4, 0=neutro) per una sillaba dato il contesto */
    private fun forwardOne(m: Model, X: IntArray): Int {
        val L = 2 * m.N + 1  // 11
        val embDim = m.embDim  // 32

        // Embedding lookup + flatten
        val x = FloatArray(L * embDim)
        for (i in 0 until L) {
            val row = X[i] * embDim
            if (row >= 0 && row + embDim <= m.E.size) {
                m.E.copyInto(x, i * embDim, row, row + embDim)
            }
        }

        // FC1: (L*embDim) -> h1, ReLU
        val h1 = FloatArray(m.h1)
        for (j in 0 until m.h1) {
            var s = m.b1[j]
            for (k in 0 until L * embDim) {
                s += x[k] * m.W1[k * m.h1 + j]
            }
            h1[j] = if (s > 0) s else 0f
        }

        // FC2: h1 -> h2, ReLU
        val h2 = FloatArray(m.h2)
        for (j in 0 until m.h2) {
            var s = m.b2[j]
            for (k in 0 until m.h1) {
                s += h1[k] * m.W2[k * m.h2 + j]
            }
            h2[j] = if (s > 0) s else 0f
        }

        // FC3: h2 -> 5 logits, argmax
        var best = 0
        var bestV = -Float.MAX_VALUE
        for (j in 0 until 5) {
            var s = m.b3[j]
            for (k in 0 until m.h2) {
                s += h2[k] * m.W3[k * 5 + j]
            }
            if (s > bestV) { bestV = s; best = j }
        }
        return best
    }

    /**
     * Predice i toni per un array di sillabe BARE (senza tono).
     * Ritorna IntArray della stessa lunghezza con toni 0-4.
     */
    fun predictTones(syllables: Array<String>): IntArray? {
        val m = model ?: return null
        val N = m.N
        val L = 2 * N + 1

        val idx = IntArray(syllables.size) {
            m.vocabIndex[syllables[it].lowercase()] ?: 0
        }

        val tones = IntArray(syllables.size)
        val X = IntArray(L)

        for (p in syllables.indices) {
            for (k in -N..N) {
                val q = p + k
                X[k + N] = if (q in syllables.indices) idx[q] else 0
            }
            tones[p] = forwardOne(m, X)
        }
        return tones
    }
}
