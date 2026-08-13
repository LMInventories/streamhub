package com.android.streamhub.feature.emby.data

import com.android.streamhub.core.common.library.LibraryMatch
import com.android.streamhub.core.common.library.LibraryTitleFinder
import javax.inject.Inject
import kotlin.math.abs

/** Emby-side implementation of the shared LibraryTitleFinder interface - see JellyfinLibraryTitleFinder's matching doc, identical shape. */
class EmbyLibraryTitleFinder @Inject constructor(
    private val browseRepository: EmbyBrowseRepository,
) : LibraryTitleFinder {

    override suspend fun findInLibrary(title: String, year: Int?, isSeries: Boolean): LibraryMatch? {
        val wantedType = if (isSeries) EmbyItemType.SERIES else EmbyItemType.MOVIE
        val candidates = browseRepository.search(title, limit = 5).filter { it.type == wantedType }
        val best = candidates.firstOrNull { candidate ->
            year == null || candidate.productionYear == null || abs(candidate.productionYear!! - year) <= 1
        } ?: candidates.firstOrNull()
        return best?.let { LibraryMatch(it.id) }
    }
}
