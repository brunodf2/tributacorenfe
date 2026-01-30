package com.tributacore.api.service

import org.springframework.stereotype.Component

@Component
class Similarity(private val textNormalizer: TextNormalizer) {

    fun jaccardSimilarity(text1: String, text2: String): Double {
        val tokens1 = textNormalizer.tokenize(text1)
        val tokens2 = textNormalizer.tokenize(text2)

        if (tokens1.isEmpty() && tokens2.isEmpty()) return 1.0
        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0

        val intersection = tokens1.intersect(tokens2).size
        val union = tokens1.union(tokens2).size

        return if (union > 0) intersection.toDouble() / union.toDouble() else 0.0
    }

    fun ngramSimilarity(text1: String, text2: String, n: Int = 2): Double {
        val ngrams1 = generateNgrams(textNormalizer.normalize(text1), n)
        val ngrams2 = generateNgrams(textNormalizer.normalize(text2), n)

        if (ngrams1.isEmpty() && ngrams2.isEmpty()) return 1.0
        if (ngrams1.isEmpty() || ngrams2.isEmpty()) return 0.0

        val intersection = ngrams1.intersect(ngrams2).size
        val union = ngrams1.union(ngrams2).size

        return if (union > 0) intersection.toDouble() / union.toDouble() else 0.0
    }

    private fun generateNgrams(text: String, n: Int): Set<String> {
        if (text.length < n) return setOf(text)
        return (0..text.length - n).map { text.substring(it, it + n) }.toSet()
    }

    fun combinedSimilarity(text1: String, text2: String): Double {
        val jaccard = jaccardSimilarity(text1, text2)
        val ngram = ngramSimilarity(text1, text2, 3)
        return (jaccard * 0.6) + (ngram * 0.4)
    }
}
