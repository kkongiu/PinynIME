package com.pinyn.ime

/**
 * Distanza di edit con costi opzionali e scambio di caratteri adiacenti.
 * Il costo di default per lo scambio è 1 (più realistico per errori di
 * tastiera rispetto a inserimento+omissione = 2).
 */
object Levenshtein {

    fun distance(
        a: String,
        b: String,
        swapCost: Int = 1,
        insertCost: Int = 1,
        deleteCost: Int = 1,
        substituteCost: Int = 1,
    ): Int {
        val n = a.length
        val m = b.length
        if (n == 0) return m * insertCost
        if (m == 0) return n * deleteCost

        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) dp[i][0] = i * deleteCost
        for (j in 0..m) dp[0][j] = j * insertCost

        for (i in 1..n) {
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else substituteCost
                dp[i][j] = minOf(
                    dp[i - 1][j] + deleteCost,
                    dp[i][j - 1] + insertCost,
                    dp[i - 1][j - 1] + cost,
                )
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    dp[i][j] = minOf(dp[i][j], dp[i - 2][j - 2] + swapCost)
                }
            }
        }
        return dp[n][m]
    }
}
