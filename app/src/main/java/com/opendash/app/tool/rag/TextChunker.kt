package com.opendash.app.tool.rag

/**
 * Splits large text into overlapping chunks suitable for retrieval.
 * OpenClaw uses 400 tokens / 80 overlap — we use approximate chars:
 * 1 token ≈ 4 chars, so ~1600 chars / ~320 char overlap.
 */
class TextChunker(
    private val chunkSize: Int = 1600,
    private val overlap: Int = 320
) {

    fun chunk(text: String): List<String> {
        val cleaned = text.trim()
        if (cleaned.length <= chunkSize) {
            return if (cleaned.isEmpty()) emptyList() else listOf(cleaned)
        }

        val chunks = mutableListOf<String>()
        var start = 0
        while (start < cleaned.length) {
            val end = (start + chunkSize).coerceAtMost(cleaned.length)
            // Try to break at a sentence boundary for cleaner chunks
            val hardEnd = if (end < cleaned.length) {
                findSentenceBoundary(cleaned, end).coerceAtLeast(start + chunkSize / 2)
            } else {
                end
            }
            chunks.add(cleaned.substring(start, hardEnd).trim())
            if (hardEnd >= cleaned.length) break
            start = hardEnd - overlap
            if (start < 0) start = 0
        }
        return chunks.filter { it.isNotBlank() }
    }

    private fun findSentenceBoundary(text: String, preferredEnd: Int): Int {
        val window = 200
        val lo = (preferredEnd - window).coerceAtLeast(0)
        val hi = (preferredEnd + window).coerceAtMost(text.length)
        // Prefer splitting at a period/question/exclamation followed by space/newline
        for (i in preferredEnd downTo lo) {
            if (i + 1 < text.length && text[i] in ".!?。" && (text[i + 1].isWhitespace() || text[i + 1] == '\n')) {
                return i + 1
            }
        }
        // Otherwise split at the nearest whitespace after preferredEnd
        for (i in preferredEnd until hi) {
            if (text[i].isWhitespace()) return i
        }
        return preferredEnd
    }
}
