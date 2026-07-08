package com.android.streamhub.feature.iptv.data.recent

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Denormalized (name/logo/streamUrl captured at view-time) for the same reason FavoriteChannelEntity is - no "get a single channel by id" endpoint to re-fetch display data from later. */
@Entity(tableName = "recent_channels")
data class RecentChannelEntity(
    @PrimaryKey val channelId: String,
    val name: String,
    val logoUrl: String?,
    val streamUrl: String,
    val lastViewedAtEpochSeconds: Long,
)
