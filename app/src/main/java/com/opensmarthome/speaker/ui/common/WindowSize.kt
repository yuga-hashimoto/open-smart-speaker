package com.opensmarthome.speaker.ui.common

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Detects tablet-class landscape where we have enough horizontal space to
 * show split layouts (clock+weather on one side, device/timer info on the other).
 *
 * Threshold is 600dp wide, following Material Window Size Classes "medium" minimum.
 */
@Composable
@ReadOnlyComposable
fun isExpandedLandscape(): Boolean {
    val cfg = LocalConfiguration.current
    return cfg.orientation == Configuration.ORIENTATION_LANDSCAPE &&
        cfg.screenWidthDp >= 600
}
