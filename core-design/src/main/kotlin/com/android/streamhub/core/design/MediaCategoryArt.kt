package com.android.streamhub.core.design

/**
 * Hardcoded hero art for the home screen's "Media" row (one tile per Favourites/library) - shared
 * by Jellyfin and Emby since both render the exact same row shape off the exact same
 * source-agnostic label (the library's own name, or "Favourites"). Matched by keyword against that
 * label rather than an exact id, since a user's library names are free text they chose on their own
 * server, not a fixed enum this app controls. Order matters below - more specific patterns (anime/
 * sports/4K variants) are checked before the plain "Movies"/"TV Shows" fallback, so a library named
 * e.g. "Anime Movies" doesn't match the plain Movies art just because it also contains "movies".
 * Returns null (caller keeps its existing plain-surface placeholder look) for anything that matches
 * nothing here - most user libraries won't share these exact names, this is a hardcoded set for one
 * specific server's library names, not a general-purpose library-art system.
 */
fun mediaCategoryArtFor(label: String): Int? {
    val lower = label.lowercase()
    return when {
        lower.contains("favourite") || lower.contains("favorite") -> R.drawable.media_favourites
        lower.contains("anime") && lower.contains("movie") -> R.drawable.media_anime_movies
        lower.contains("anime") && (lower.contains("tv") || lower.contains("show")) -> R.drawable.media_anime_tv
        lower.contains("sport") -> R.drawable.media_sports_movies
        // 4K variants before the plain "movie"/"tv" fallbacks below - "Movies 4K" and "TV Shows 4K"
        // both also contain "movie"/"tv", so they'd otherwise never reach these branches.
        lower.contains("4k") && (lower.contains("dv") || lower.contains("dolby")) -> R.drawable.media_movies_4k_dv
        lower.contains("4k") && (lower.contains("tv") || lower.contains("show")) -> R.drawable.media_tv_shows_4k
        lower.contains("4k") -> R.drawable.media_movies_4k
        lower.contains("movie") -> R.drawable.media_movies
        lower.contains("tv") || lower.contains("show") -> R.drawable.media_tv_shows
        else -> null
    }
}
