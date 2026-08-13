package com.android.streamhub.core.common.library

import javax.inject.Qualifier

data class LibraryMatch(val itemId: String)

/**
 * Best-effort "does this title exist in the user's own library" lookup, used by the actor profile
 * screen (feature-person) to decide whether a filmography entry can deep-link into the real
 * Jellyfin/Emby item detail screen or should stay purely informational.
 *
 * feature-person cannot depend on feature-jellyfin/feature-emby directly (feature modules never
 * depend on each other in this codebase - see Route.kt's own doc comment), so this interface lives
 * here instead and each source module provides + Hilt-binds its own implementation, qualified by
 * JellyfinLibraryFinder/EmbyLibraryFinder below. feature-person's ViewModel injects both qualified
 * instances and picks the right one based on which source the route was navigated from.
 *
 * No provider-ID (TMDB/IMDB/TVDB) cross-referencing is used - unverified/unavailable on both
 * SDKs today. Implementations match on title + year instead, reusing whatever search capability
 * the source module already has for Master Search.
 */
interface LibraryTitleFinder {
    suspend fun findInLibrary(title: String, year: Int?, isSeries: Boolean): LibraryMatch?
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class JellyfinLibraryFinder

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EmbyLibraryFinder
