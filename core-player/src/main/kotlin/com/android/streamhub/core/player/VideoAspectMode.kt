package com.android.streamhub.core.player

import androidx.media3.ui.AspectRatioFrameLayout

/**
 * Bundles the two concerns a "4:3 / 16:9 / Fit / Fill" picker actually needs: how PlayerView
 * fits video within its bounds, and what those bounds even are. FIT/FILL let the player take
 * all available space; the forced ratios constrain the container itself and letterbox the
 * video inside it, which is why they also use RESIZE_MODE_FIT rather than a resize mode of
 * their own - there's no separate "resize mode" for a viewport shape, only for content-within-
 * viewport fitting.
 */
enum class VideoAspectMode(
    val containerAspectRatio: Float?,
    val resizeMode: Int,
) {
    FIT(containerAspectRatio = null, resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL(containerAspectRatio = null, resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    RATIO_4_3(containerAspectRatio = 4f / 3f, resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT),
    RATIO_16_9(containerAspectRatio = 16f / 9f, resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT),
}
