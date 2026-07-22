package com.rameshta.quietpdf

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppVersionTest {
    @Test
    fun packagedVersionMatchesBuildConfigAndUsesReleaseFormats() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_META_DATA,
        )

        assertEquals(BuildConfig.VERSION_NAME, packageInfo.versionName)
        assertEquals(BuildConfig.VERSION_CODE.toLong(), packageInfo.longVersionCode)
        assertTrue(BuildConfig.VERSION_CODE > 0)
        assertTrue(
            BuildConfig.VERSION_NAME.matches(
                Regex("^\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z.-]+)?$"),
            ),
        )
    }
}
