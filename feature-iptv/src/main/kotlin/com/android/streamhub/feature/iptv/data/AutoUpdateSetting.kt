package com.android.streamhub.feature.iptv.data

import kotlinx.serialization.Serializable

enum class AutoUpdateMode { OFF, ON_APP_OPEN, EVERY_N_DAYS, EVERY_N_HOURS }

val AUTO_UPDATE_DAY_OPTIONS = 1..5
val AUTO_UPDATE_HOUR_OPTIONS = listOf(3, 6, 12, 18)

@Serializable
data class AutoUpdateSetting(
    val mode: AutoUpdateMode = AutoUpdateMode.OFF,
    val days: Int = 1,
    val hours: Int = 6,
)
