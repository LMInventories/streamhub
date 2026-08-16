package com.android.streamhub.core.tmdb

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

data class TmdbPersonSummary(
    val id: Int,
    val name: String,
    val profileUrl: String?,
)

data class TmdbPersonDetail(
    val id: Int,
    val name: String,
    val biography: String?,
    val birthday: String?,
    val placeOfBirth: String?,
    val profileUrl: String?,
)

data class TmdbFilmographyEntry(
    val tmdbId: Int,
    val title: String,
    val posterUrl: String?,
    val year: Int?,
    val isSeries: Boolean,
)

data class TmdbFilmography(
    val movies: List<TmdbFilmographyEntry>,
    val tvShows: List<TmdbFilmographyEntry>,
)

/** Builds TMDB's CDN image URLs directly rather than round-tripping through /configuration first - its base URL has been stable for years, this is standard practice for TMDB clients. */
object TmdbImageUrls {
    private const val BASE = "https://image.tmdb.org/t/p/"

    fun profile(path: String?, size: String = "w300"): String? = path?.let { "$BASE$size$it" }

    fun poster(path: String?, size: String = "w342"): String? = path?.let { "$BASE$size$it" }
}

/**
 * Source-agnostic TMDB access shared by Jellyfin and Emby's cast rows and the actor profile
 * screen. Every call is best-effort: a missing/invalid API key, a network failure, or TMDB simply
 * having no match all degrade to null/empty rather than throwing, so a cast row or profile page
 * never blocks or crashes on this - it just silently has no extra info.
 *
 * findPerson resolves a plain name string (no provider-id cross-reference exists anywhere in this
 * app - see the actor-profile plan) via TMDB's own person-search endpoint, taking the first
 * result. This can occasionally mismatch for common names (TMDB search ranks by popularity, which
 * is usually but not always the actor the cast credit actually means) - same honesty standard as
 * this codebase's other fuzzy matching (FuzzyMatch, used by Master Search).
 *
 * In-memory only cache (dies with the process, no Room/DataStore) - a cheap mitigation given the
 * embedded API key's shared-rate-limit exposure, not a full caching layer.
 */
@Singleton
class TmdbRepository @Inject constructor(
    private val api: TmdbApi,
) {
    private val apiKey: String = BuildConfig.TMDB_API_KEY

    private val personByNameCache = mutableMapOf<String, TmdbPersonSummary?>()
    private val personByNameMutex = Mutex()
    private val personDetailCache = mutableMapOf<Int, TmdbPersonDetail?>()
    private val personDetailMutex = Mutex()
    private val filmographyCache = mutableMapOf<Int, TmdbFilmography>()
    private val filmographyMutex = Mutex()

    suspend fun findPerson(name: String): TmdbPersonSummary? {
        if (apiKey.isBlank() || name.isBlank()) return null
        val key = name.trim().lowercase()
        personByNameMutex.withLock {
            if (personByNameCache.containsKey(key)) return personByNameCache[key]
        }
        val result = runCatching { api.searchPerson(apiKey = apiKey, query = name) }
            .getOrNull()
            ?.results
            ?.firstOrNull()
            ?.let { TmdbPersonSummary(id = it.id, name = it.name.orEmpty(), profileUrl = TmdbImageUrls.profile(it.profilePath)) }
        personByNameMutex.withLock { personByNameCache[key] = result }
        return result
    }

    /**
     * Backfills TMDB profile photos for cast members whose own Jellyfin/Emby source has no image
     * - the overview cast row used to show only member.imageUrl, sourced from that platform's own
     * PrimaryImageTag, which frequently comes back null/absent (see EmbyPersonDto.primaryImageTag's
     * own UNVERIFIED-field doc); the actor-profile screen already gets a reliable photo via this
     * same [findPerson] lookup, just triggered per-name on click rather than proactively for a
     * whole row. Takes bare id/name pairs rather than either platform's own CastMember type so one
     * function serves both without either feature module depending on the other. Concurrent per
     * member (findPerson's own cache + mutex keep repeat names cheap); a member with no TMDB match
     * is simply absent from the result rather than mapped to null, so callers can merge this
     * straight onto whatever placeholder they already show.
     */
    suspend fun findProfileImageFallbacks(members: List<Pair<String, String>>): Map<String, String> = coroutineScope {
        members
            .map { (id, name) -> async { id to findPerson(name)?.profileUrl } }
            .map { it.await() }
            .mapNotNull { (id, url) -> url?.let { id to it } }
            .toMap()
    }

    suspend fun getPersonDetail(personId: Int): TmdbPersonDetail? {
        if (apiKey.isBlank()) return null
        personDetailMutex.withLock {
            if (personDetailCache.containsKey(personId)) return personDetailCache[personId]
        }
        val result = runCatching { api.getPerson(personId = personId, apiKey = apiKey) }
            .getOrNull()
            ?.let {
                TmdbPersonDetail(
                    id = it.id,
                    name = it.name.orEmpty(),
                    biography = it.biography?.takeIf { bio -> bio.isNotBlank() },
                    birthday = it.birthday,
                    placeOfBirth = it.placeOfBirth,
                    profileUrl = TmdbImageUrls.profile(it.profilePath),
                )
            }
        personDetailMutex.withLock { personDetailCache[personId] = result }
        return result
    }

    suspend fun getFilmography(personId: Int): TmdbFilmography {
        if (apiKey.isBlank()) return TmdbFilmography(emptyList(), emptyList())
        filmographyMutex.withLock {
            filmographyCache[personId]?.let { return it }
        }
        val credits = runCatching { api.getCombinedCredits(personId = personId, apiKey = apiKey) }
            .getOrNull()
            ?.cast
            .orEmpty()

        val movies = credits
            .asSequence()
            .filter { it.mediaType == "movie" }
            .mapNotNull { it.toFilmographyEntry(isSeries = false) }
            .distinctBy { it.tmdbId }
            .sortedByDescending { it.year ?: Int.MIN_VALUE }
            .toList()

        val tvShows = credits
            .asSequence()
            .filter { it.mediaType == "tv" }
            .mapNotNull { it.toFilmographyEntry(isSeries = true) }
            .distinctBy { it.tmdbId }
            .sortedByDescending { it.year ?: Int.MIN_VALUE }
            .toList()

        val result = TmdbFilmography(movies = movies, tvShows = tvShows)
        filmographyMutex.withLock { filmographyCache[personId] = result }
        return result
    }

    private fun TmdbCreditDto.toFilmographyEntry(isSeries: Boolean): TmdbFilmographyEntry? {
        val title = (if (isSeries) name else title) ?: return null
        val dateString = if (isSeries) firstAirDate else releaseDate
        val year = dateString?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
        return TmdbFilmographyEntry(
            tmdbId = id,
            title = title,
            posterUrl = TmdbImageUrls.poster(posterPath),
            year = year,
            isSeries = isSeries,
        )
    }
}
