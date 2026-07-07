package com.android.streamhub.feature.iptv.data

import java.time.Instant

/** Source-agnostic "now/next" program info, however it was actually obtained (Xtream short-EPG or XMLTV). */
data class EpgProgram(
    val title: String,
    val startAt: Instant,
    val endAt: Instant,
    val description: String? = null,
) {
    fun isCurrentAt(instant: Instant): Boolean = instant >= startAt && instant < endAt
}
