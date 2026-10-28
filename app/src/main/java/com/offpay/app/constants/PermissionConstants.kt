// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.offpay.app.constants

import android.Manifest

/** Optional feature access. No runtime permission is required to open the system dialer. */
object PermissionConstants {
    val REQUIRED_PERMISSIONS = emptyArray<String>()
    val CRITICAL_PERMISSIONS = emptyArray<String>()
    val PHONE_PERMISSIONS = arrayOf(Manifest.permission.CALL_PHONE)

    // A single first-run invitation; denied permissions never block onboarding or payment handoff.
    val FIRST_LAUNCH_PERMISSIONS = PHONE_PERMISSIONS + arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.READ_CONTACTS
    )
}
