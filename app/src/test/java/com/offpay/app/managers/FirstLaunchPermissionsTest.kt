// SPDX-License-Identifier: Apache-2.0
package com.offpay.app.managers

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.core.content.edit
import com.offpay.app.constants.PermissionConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirstLaunchPermissionsTest {
    private val app: Application = RuntimeEnvironment.getApplication()

    @Before
    fun reset() {
        app.getSharedPreferences("offpay_permissions", Context.MODE_PRIVATE).edit { clear() }
        shadowOf(app).denyPermissions(*PermissionConstants.FIRST_LAUNCH_PERMISSIONS)
    }

    @Test
    fun `first launch requests all four feature groups without inbox access`() {
        var requested = emptySet<String>()
        FirstLaunchPermissions(app).requestIfNeeded { requested = it.toSet() }
        assertTrue(requested.containsAll(PermissionConstants.PHONE_PERMISSIONS.toList()))
        assertTrue(
            requested.containsAll(
                listOf(Manifest.permission.CAMERA, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_CONTACTS)
            )
        )
        assertFalse(requested.contains(Manifest.permission.READ_SMS))
    }

    @Test
    fun `already granted permissions are not requested again`() {
        shadowOf(app).grantPermissions(*PermissionConstants.PHONE_PERMISSIONS, Manifest.permission.CAMERA)
        var requested = emptySet<String>()
        FirstLaunchPermissions(app).requestIfNeeded { requested = it.toSet() }
        assertEquals(setOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_CONTACTS), requested)
    }

    @Test
    fun `denial and activity recreation do not automatically restart the dialogs`() {
        var launches = 0
        FirstLaunchPermissions(app).requestIfNeeded { launches++ }
        // A new helper represents a recreated Activity or a later app launch; permissions remain denied.
        FirstLaunchPermissions(app).requestIfNeeded { launches++ }
        assertEquals(1, launches)
    }

    @Test
    fun `a fully granted installation does not launch a permission dialog`() {
        shadowOf(app).grantPermissions(*PermissionConstants.FIRST_LAUNCH_PERMISSIONS)
        var launches = 0
        FirstLaunchPermissions(app).requestIfNeeded { launches++ }
        assertEquals(0, launches)
    }

    @Test
    fun `settings is offered only for a previously requested permission that cannot show a dialog`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val history = FirstLaunchPermissions(app)
            val permissions = arrayOf(Manifest.permission.CAMERA)
            assertFalse(history.shouldOpenSettings(controller.get(), permissions))
            history.recordRequest(permissions)
            assertTrue(history.shouldOpenSettings(controller.get(), permissions))
            shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
            assertFalse(history.shouldOpenSettings(controller.get(), permissions))
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
