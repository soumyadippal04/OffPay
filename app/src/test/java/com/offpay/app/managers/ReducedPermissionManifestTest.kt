// SPDX-License-Identifier: Apache-2.0

package com.offpay.app.managers

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import com.offpay.app.BuildConfig
import com.offpay.app.constants.PermissionConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReducedPermissionManifestTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val optionalPermissions = setOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.CAMERA,
        Manifest.permission.READ_CONTACTS
    )

    @Test
    fun `merged manifest includes SMS permissions only in the optional SMS edition`() {
        val requested = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toSet()
        val removedPermissions = setOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SYSTEM_ALERT_WINDOW,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.INTERNET
        )

        assertTrue(requested.containsAll(optionalPermissions + Manifest.permission.VIBRATE))
        assertEquals(emptySet<String>(), requested.intersect(removedPermissions))
        assertTrue(requested.containsAll(PermissionConstants.FIRST_LAUNCH_PERMISSIONS.toSet()))
        assertEquals(BuildConfig.SMS_CONFIRMATION_AVAILABLE, Manifest.permission.RECEIVE_SMS in requested)
        assertEquals(BuildConfig.SMS_CONFIRMATION_AVAILABLE, Manifest.permission.POST_NOTIFICATIONS in requested)
    }

    @Test
    fun `merged manifest registers SMS receiver only when built in and never the legacy overlay`() {
        val components = app.packageManager.getPackageInfo(
            app.packageName,
            PackageManager.GET_RECEIVERS or PackageManager.GET_SERVICES
        )

        assertEquals(
            BuildConfig.SMS_CONFIRMATION_AVAILABLE,
            components.receivers.orEmpty().any { it.name == "com.offpay.app.receivers.SimpleSMSReceiver" }
        )
        assertFalse(components.services.orEmpty().any { it.name == "com.offpay.app.services.CallOverlayService" })
    }

    @Test
    fun `declining every optional feature leaves no core permission gate`() {
        shadowOf(app).denyPermissions(*optionalPermissions.toTypedArray())
        assertTrue(optionalPermissions.containsAll(PermissionConstants.FIRST_LAUNCH_PERMISSIONS.toSet()))
        assertTrue(PermissionConstants.REQUIRED_PERMISSIONS.isEmpty())
        assertTrue(PermissionConstants.CRITICAL_PERMISSIONS.isEmpty())
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val manager = PermissionManager(controller.get())
            assertTrue(manager.checkAllPermissions())
            assertTrue(manager.getMissingPermissions().isEmpty())
            assertFalse(manager.hasPhonePermissions())
            assertFalse(manager.hasCameraPermission())
            assertFalse(manager.hasContactPermission())
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `camera hardware remains optional so image import works without a camera`() {
        val features = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_CONFIGURATIONS)
            .reqFeatures.orEmpty().associateBy { it.name }
        for (name in listOf(PackageManager.FEATURE_CAMERA, PackageManager.FEATURE_CAMERA_AUTOFOCUS)) {
            assertTrue("Camera feature must be declared optional: $name", features.containsKey(name))
            assertEquals(0, features.getValue(name).flags and FeatureInfo.FLAG_REQUIRED)
        }
    }
}
