package com.neargems.domain

private val SENTENCE_END = Regex("(?<=[.!?])\\s+")

/**
 * The opening sentences of [text] that fit in [maxChars], so the card shows whole sentences and
 * nothing is cut off half-way. If the first sentence alone is too long, it is cut at a word and
 * given an ellipsis. The full text is still available in the place's modal.
 */
fun summarise(text: String, maxChars: Int): String {
    val sentences = text.trim().split(SENTENCE_END).filter { it.isNotBlank() }
    if (sentences.isEmpty()) return text
    var summary = ""
    for (sentence in sentences) {
        val next = if (summary.isEmpty()) sentence else "$summary $sentence"
        if (next.length > maxChars) break
        summary = next
    }
    if (summary.isNotEmpty()) return summary
    val first = sentences.first()
    val room = maxChars.coerceAtLeast(1) - 1
    val cut = first.take(room).substringBeforeLast(' ', missingDelimiterValue = first.take(room))
    return cut.trimEnd(',', ';', ':', ' ') + "…"
}
