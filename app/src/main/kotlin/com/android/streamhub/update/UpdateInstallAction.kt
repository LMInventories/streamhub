package com.android.streamhub.update

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Shared "tap Update" behavior for the Home banner and the Settings row - if install-from-
 * unknown-sources isn't already granted, redirects there first (mirrors the one other runtime-
 * permission ask in this app, ProgramSchedulingUi's POST_NOTIFICATIONS flow). The grant/deny
 * result code from that Settings screen is unreliable across OEMs, so this just re-checks
 * [canInstallPackages] once the user returns rather than trusting the launcher's result.
 *
 * Many Android TV builds have no Settings activity resolving ACTION_MANAGE_UNKNOWN_APP_SOURCES at
 * all (that per-app "install unknown apps" screen is a phone-Settings-app feature, not guaranteed
 * on TV/OEM boxes) - launching it there throws ActivityNotFoundException, which without this
 * catch left the whole tap silently doing nothing and the download never even starting. Falling
 * back to attempting the download/install directly is still useful even without that redirect:
 * the system package installer itself prompts for the same permission when it's actually missing
 * (standard since Android 8), so this only skips a nicety, not a hard requirement.
 */
@Composable
fun rememberUpdateInstallAction(
    canInstallPackages: () -> Boolean,
    installPermissionIntent: () -> Intent,
    onStartDownload: () -> Unit,
): () -> Unit {
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (canInstallPackages()) onStartDownload()
    }
    return remember(canInstallPackages, installPermissionIntent, onStartDownload) {
        {
            if (canInstallPackages()) {
                onStartDownload()
            } else {
                runCatching { permissionLauncher.launch(installPermissionIntent()) }
                    .onFailure { onStartDownload() }
            }
        }
    }
}
