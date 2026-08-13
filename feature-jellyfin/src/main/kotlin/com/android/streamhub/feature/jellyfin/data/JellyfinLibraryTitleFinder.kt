package com.android.streamhub.feature.jellyfin.data

import com.android.streamhub.core.common.library.LibraryMatch
import com.android.streamhub.core.common.library.LibraryTitleFinder
import javax.inject.Inject
import kotlin.math.abs

/**
 * Jellyfin-side implementation of the shared LibraryTitleFinder interface (see that file's doc for
 * why this lives behind a Hilt qualifier rather than feature-person depending on feature-jellyfin
 * directly). Reuses JellyfinBrowseRepository.search - the same broad-search-plus-FuzzyMatch call
 * Master Search already makes - rather than a second search path.
 */
class JellyfinLibraryTitleFinder @Inject constructor(
    private val browseRepository: JellyfinBrowseRepository,
) : LibraryTitleFinder {

    override suspend fun findInLibrary(title: String, year: Int?, isSeries: Boolean): LibraryMatch? {
        val wantedType = if (isSeries) JellyfinItemType.SERIES else JellyfinItemType.MOVIE
        val candidates = browseRepository.search(title, limit = 5).filter { it.type == wantedType }
        val best = candidates.firstOrNull { candidate ->
            year == null || candidate.productionYear == null || abs(candidate.productionYear!! - year) <= 1
        } ?: candidates.firstOrNull()
        return best?.let { LibraryMatch(it.id) }
    }
}
