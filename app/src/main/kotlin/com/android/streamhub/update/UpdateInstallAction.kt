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
            if (canInstallPackages()) onStartDownload() else permissionLauncher.launch(installPermissionIntent())
        }
    }
}
