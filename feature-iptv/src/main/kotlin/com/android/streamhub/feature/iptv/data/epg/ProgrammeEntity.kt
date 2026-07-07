package com.android.streamhub.feature.iptv.data.epg

import androidx.room.Entity
import com.android.streamhub.feature.iptv.data.EpgProgram
import java.time.Instant

@Entity(tableName = "programmes", primaryKeys = ["channelId", "startAtEpochSeconds"])
data class ProgrammeEntity(
    val channelId: String,
    val startAtEpochSeconds: Long,
    val endAtEpochSeconds: Long,
    val title: String,
    val description: String?,
)

fun ProgrammeEntity.toEpgProgram(): EpgProgram = EpgProgram(
    title = title,
    startAt = Instant.ofEpochSecond(startAtEpochSeconds),
    endAt = Instant.ofEpochSecond(endAtEpochSeconds),
    description = description,
)
