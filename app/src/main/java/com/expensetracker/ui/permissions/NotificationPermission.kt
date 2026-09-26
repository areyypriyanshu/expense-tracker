package com.expensetracker.ui.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Notification permission helpers.
 *
 * `POST_NOTIFICATIONS` only became a runtime permission in Android 13 (API 33).
 * Below that it is granted at install time and the only way to turn
 * notifications off is the system settings screen, so the platform level has to
 * be checked before any of this is used.
 */
object NotificationPermission {

    /** True only where the runtime permission actually exists. */
    fun requiresRuntimeRequest(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * Whether the app still holds the POST_NOTIFICATIONS grant, ignoring the
     * separate per-channel switches in system settings.
     *
     * Reports `true` on Android 12 and below, where there is nothing to grant.
     */
    fun isPermissionGranted(context: Context): Boolean =
        !requiresRuntimeRequest() ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
