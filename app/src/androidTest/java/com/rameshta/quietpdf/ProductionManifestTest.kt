package com.rameshta.quietpdf

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductionManifestTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun privateMetadataCannotBeBackedUpAndCleartextTrafficIsDisabled() {
        val flags = context.applicationInfo.flags
        assertFalse(flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
        assertFalse(flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC != 0)
    }

    @Test
    fun onlyRequiredEntryPointIsExportedAndFileProviderRemainsPrivate() {
        @Suppress("DEPRECATION")
        val activity = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            PackageManager.GET_META_DATA,
        )
        assertTrue(activity.exported)
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TOP, activity.launchMode)

        @Suppress("DEPRECATION")
        val provider = context.packageManager.getProviderInfo(
            ComponentName(context, FileProvider::class.java),
            PackageManager.GET_META_DATA,
        )
        assertFalse(provider.exported)
        assertTrue(provider.grantUriPermissions)
    }
}
