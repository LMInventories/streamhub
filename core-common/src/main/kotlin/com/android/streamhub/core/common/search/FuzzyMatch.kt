package com.android.streamhub.core.common.search

/**
 * Lightweight fuzzy-ish substring matching for Search - normalizes both sides (lowercase,
 * letters/digits only) before comparing, so differences that read as "obviously the same title"
 * to a person don't block a match: case ("eastenders" vs "EastEnders") and punctuation/spacing
 * ("spider man" vs "Spider-Man"). Not true edit-distance fuzzy matching (no typo tolerance) -
 * deliberately kept simple since the reported gaps were both normalization issues, not typos.
 * Shared across every Search source (EPG/VOD in :feature-iptv, Jellyfin) rather than duplicated,
 * since all three want the exact same normalization rule.
 */
object FuzzyMatch {
    private fun normalize(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

    fun matches(candidate: String, query: String): Boolean {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isEmpty()) return false
        return normalize(candidate).contains(normalizedQuery)
    }
}
