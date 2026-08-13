package com.pinyn.ime

import android.content.res.AssetManager
import org.json.JSONObject

/**
 * SentenceNet — LM per la verifica di forma delle frasi pinyin.
 * MLP a finestra (embedding + hidden + softmax) che predice la parola successiva.
 * Porting nativo di `sentence-net.js`.
 */
class SentenceNet {

    data class Model(
        val K: Int,
        val V: Int,
        val d: Int,
        val H1: Int,
        val vocab: List<String>,
        val vocabIndex: Map<String, Int>,
        val E: FloatArray,    // [V * d]
        val W1: FloatArray,   // [(K*d) * H1]
        val W2: FloatArray,   // [H1 * V]
        val b1: FloatArray,   // [H1]
        val b2: FloatArray,   // [V]
    )

    data class Score(val avgNegLog: Double, val perplexity: Double, val flagged: Boolean)

    private var model: Model? = null

    fun loadFromAssets(assets: AssetManager): Boolean {
        return try {
            val json = assets.open("data/sentence-net.json").bufferedReader().readText()
            loadFromString(json)
        } catch (e: Exception) {
            android.util.Log.e("SentenceNet", "load from assets failed", e)
            false
        }
    }

    fun loadFromString(jsonContent: String): Boolean {
        return try {
            val start = jsonContent.indexOf('{')
            if (start < 0) return false
            var depth = 0
            var end = start
            for (i in start until jsonContent.length) {
                when (jsonContent[i]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            end = i + 1
                            break
                        }
                    }
                }
            }
            val root = JSONObject(jsonContent.substring(start, end))
            model = build(root)
            true
        } catch (e: Exception) {
            android.util.Log.e("SentenceNet", "load from string failed", e)
            false
        }
    }

    fun isLoaded(): Boolean = model != null

    fun getModelForTest(): Model? = model

    private fun build(m: JSONObject): Model {
        val K = m.getInt("K")
        val V = m.getInt("V")
        val d = m.getInt("d")
        val H1 = m.getInt("h1")
        val vocabArr = m.getJSONArray("vocab")
        val vocab = (0 until vocabArr.length()).map { vocabArr.getString(it) }
        val vocabIndex = vocab.withIndex().associate { it.value to it.index }
        val E = dequantize(m.getJSONObject("E"), d, true)
        val W1 = dequantize(m.getJSONObject("W1"), 0, false)
        val W2 = dequantize(m.getJSONObject("W2"), V, true)
        val b1 = floatArray(m.getJSONArray("b1"))
        val b2 = floatArray(m.getJSONArray("b2"))
        return Model(K, V, d, H1, vocab, vocabIndex, E, W1, W2, b1, b2)
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

    private fun forwardLogits(model: Model, idx: IntArray, start: Int): FloatArray {
        val K = model.K
        val d = model.d
        val H1 = model.H1
        val V = model.V

        val x = FloatArray(K * d)
        for (i in 0 until K) {
            val row = idx[start + i] * d
            if (row >= 0 && row + d <= model.E.size) {
                model.E.copyInto(x, i * d, row, row + d)
            }
        }

        val h1 = FloatArray(H1)
        for (j in 0 until H1) {
            var s = model.b1[j]
            for (k in 0 until K * d) s += x[k] * model.W1[k * H1 + j]
            h1[j] = if (s > 0) s else 0f
        }

        val logits = FloatArray(V)
        for (j in 0 until H1) {
            val h = h1[j]
            if (h == 0f) continue
            val row = j * V
            for (k in 0 until V) logits[k] += h * model.W2[row + k]
        }
        for (k in 0 until V) logits[k] += model.b2[k]
        return logits
    }

    /**
     * Punteggio di una frase (parole pinyin BARE).
     * Ritorna null se la frase è troppo corta (<= K parole).
     */
    fun scoreSentence(words: List<String>): Score? {
        val model = model ?: return null
        if (words.size <= model.K) return null
        val K = model.K
        val V = model.V

        val idx = IntArray(words.size)
        for (i in words.indices) {
            idx[i] = model.vocabIndex[words[i]] ?: 1 // 1 = <unk>
        }

        var sum = 0.0
        var n = 0
        for (t in K until words.size) {
            val logits = forwardLogits(model, idx, t - K)
            var max = -Double.MAX_VALUE
            for (j in 0 until V) if (logits[j].toDouble() > max) max = logits[j].toDouble()
            var den = 0.0
            for (j in 0 until V) den += Math.exp(logits[j].toDouble() - max)
            val p = Math.exp(logits[idx[t]].toDouble() - max) / den
            sum += -Math.log(Math.max(p, 1e-12))
            n++
        }
        val avg = sum / n
        return Score(avg, Math.exp(avg), avg > THRESHOLD)
    }

    companion object {
        const val THRESHOLD = 7.5
        const val MIN_WORDS = 5
    }
}
