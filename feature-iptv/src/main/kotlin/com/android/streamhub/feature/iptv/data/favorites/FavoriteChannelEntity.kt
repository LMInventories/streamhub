package com.android.streamhub.feature.iptv.data.favorites

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Denormalized rather than just storing channelId - there's no "get a single channel by id"
 * Xtream/M3U endpoint, only "get channels in a category", so a favourited channel's display
 * info (name/logo/stream URL) has to be captured at the moment it's favourited to be shown
 * later without knowing (or re-fetching) which category it came from.
 */
@Entity(tableName = "favorite_channels")
data class FavoriteChannelEntity(
    @PrimaryKey val channelId: String,
    val name: String,
    val logoUrl: String?,
    val streamUrl: String,
    val addedAtEpochSeconds: Long,
)
