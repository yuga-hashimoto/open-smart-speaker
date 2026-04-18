package com.opendash.app.tool.memory

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Lightweight TF-IDF index for semantic-ish search over a small corpus.
 *
 * Not a replacement for real vector embeddings, but effective for short
 * memory entries (<200 tokens each) and requires zero dependencies —
 * unlike ONNX/sentence-transformers which would add ~50-100MB to the APK.
 *
 * Given a few hundred memory entries, cosine similarity over TF-IDF
 * vectors retrieves semantically related items far better than SQL LIKE.
 */
class TfIdfIndex(
    private val documents: List<Document>
) {

    data class Document(val id: String, val text: String)

    data class Hit(val document: Document, val score: Double)

    private val vocabulary: Map<String, Int>
    private val idf: DoubleArray
    private val docVectors: List<DoubleArray>

    init {
        val terms = mutableSetOf<String>()
        val tokenLists = documents.map { tokenize(it.text).also { terms.addAll(it) } }
        vocabulary = terms.sorted().withIndex().associate { (i, t) -> t to i }

        // Document frequency per term
        val df = IntArray(vocabulary.size)
        for (tokens in tokenLists) {
            for (term in tokens.toSet()) {
                df[vocabulary.getValue(term)]++
            }
        }
        val n = documents.size.coerceAtLeast(1)
        idf = DoubleArray(vocabulary.size) { i ->
            ln((n + 1.0) / (df[i] + 1.0)) + 1.0 // smoothed IDF
        }

        docVectors = tokenLists.map { tokens ->
            val tf = DoubleArray(vocabulary.size)
            for (t in tokens) {
                tf[vocabulary.getValue(t)] += 1.0
            }
            val total = tokens.size.coerceAtLeast(1).toDouble()
            for (i in tf.indices) {
                tf[i] = (tf[i] / total) * idf[i]
            }
            normalize(tf)
            tf
        }
    }

    fun search(query: String, limit: Int): List<Hit> {
        if (documents.isEmpty()) return emptyList()
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return emptyList()

        val qv = DoubleArray(vocabulary.size)
        var matched = 0
        for (t in tokens) {
            val idx = vocabulary[t] ?: continue
            qv[idx] += 1.0 * idf[idx]
            matched++
        }
        if (matched == 0) return emptyList()
        normalize(qv)

        return documents.indices
            .map { i -> Hit(documents[i], cosine(qv, docVectors[i])) }
            .filter { it.score > 0 }
            .sortedByDescending { it.score }
            .take(limit)
    }

    private fun cosine(a: DoubleArray, b: DoubleArray): Double {
        var s = 0.0
        for (i in a.indices) s += a[i] * b[i]
        return s
    }

    private fun normalize(v: DoubleArray) {
        var sq = 0.0
        for (x in v) sq += x * x
        val norm = sqrt(sq)
        if (norm > 0) {
            for (i in v.indices) v[i] /= norm
        }
    }

    companion object {
        private val TOKEN_RE = Regex("""[\p{L}\p{N}]+""")
        private val STOP_WORDS = setOf(
            "a", "an", "and", "the", "is", "are", "was", "were",
            "of", "in", "on", "at", "to", "for", "with", "by",
            "it", "this", "that", "be", "as", "or", "but"
        )

        fun tokenize(text: String): List<String> =
            TOKEN_RE.findAll(text.lowercase())
                .map { it.value }
                .filter { it.length > 1 && it !in STOP_WORDS }
                .toList()
    }
}
