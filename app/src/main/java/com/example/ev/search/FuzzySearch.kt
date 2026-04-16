package com.example.ev.search

import kotlin.math.max

object FuzzySearch {
    const val DEFAULT_THRESHOLD: Double = 0.55

    fun bestScore(query: String, candidates: List<String>): Double {
        if (query.isBlank() || candidates.isEmpty()) return 0.0
        return candidates.maxOfOrNull { score(query, it) } ?: 0.0
    }

    fun score(query: String, candidate: String): Double {
        val normalizedQuery = query.trim().lowercase()
        val normalizedCandidate = candidate.trim().lowercase()

        if (normalizedQuery.isEmpty() || normalizedCandidate.isEmpty()) return 0.0

        if (normalizedCandidate == normalizedQuery) return 1.0
        if (normalizedCandidate.startsWith(normalizedQuery)) return 0.95
        if (normalizedCandidate.contains(normalizedQuery)) return 0.9

        val distance = levenshteinDistance(normalizedQuery, normalizedCandidate)
        val maxLength = max(normalizedQuery.length, normalizedCandidate.length).toDouble()
        return (1.0 - distance / maxLength).coerceIn(0.0, 1.0)
    }

    private fun levenshteinDistance(source: String, target: String): Int {
        if (source == target) return 0
        if (source.isEmpty()) return target.length
        if (target.isEmpty()) return source.length

        var previous = IntArray(target.length + 1) { it }
        var current = IntArray(target.length + 1)

        for (sourceIndex in source.indices) {
            current[0] = sourceIndex + 1
            for (targetIndex in target.indices) {
                val substitutionCost = if (source[sourceIndex] == target[targetIndex]) 0 else 1
                current[targetIndex + 1] = minOf(
                    current[targetIndex] + 1,
                    previous[targetIndex + 1] + 1,
                    previous[targetIndex] + substitutionCost
                )
            }
            val swap = previous
            previous = current
            current = swap
        }

        return previous[target.length]
    }
}
