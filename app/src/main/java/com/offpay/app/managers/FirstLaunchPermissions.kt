// SPDX-License-Identifier: Apache-2.0
package com.offpay.app.managers

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.offpay.app.constants.PermissionConstants

/** Requests consent once during onboarding, without retrying declined permissions on every launch. */
class FirstLaunchPermissions(private val context: Context) {
    private val prefs = context.getSharedPreferences("offpay_permissions", Context.MODE_PRIVATE)

    fun requestIfNeeded(launch: (Array<String>) -> Unit) {
        if (prefs.getBoolean("onboarding_requested", false)) return
        // Persist before launching: rotation/process recreation must not open another request.
        prefs.edit { putBoolean("onboarding_requested", true) }
        val missing = PermissionConstants.FIRST_LAUNCH_PERMISSIONS.filterNot(::isGranted).toTypedArray()
        if (missing.isNotEmpty()) {
            recordRequest(missing)
            launch(missing)
        }
    }

    fun recordRequest(permissions: Array<String>) {
        prefs.edit { permissions.forEach { putBoolean(it, true) } }
    }

    /** Android no longer shows a dialog for a permanently denied permission; offer app settings. */
    fun shouldOpenSettings(activity: Activity, permissions: Array<String>): Boolean =
        permissions.any {
            !isGranted(it) && prefs.getBoolean(it, false) &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
        }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
