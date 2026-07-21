package com.rameshta.quietpdf

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdMobConfigurationTest {
    @Test
    fun debugBuildUsesOnlyOfficialGoogleTestIdentifiers() {
        assertTrue(BuildConfig.ADMOB_ENABLED)
        assertEquals(
            "ca-app-pub-3940256099942544/9214589741",
            BuildConfig.ADMOB_HOME_BANNER_ID,
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val applicationInfo = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA,
        )
        assertEquals(
            "ca-app-pub-3940256099942544~3347511713",
            applicationInfo.metaData.getString("com.google.android.gms.ads.APPLICATION_ID"),
        )
        assertTrue(
            applicationInfo.metaData.getBoolean(
                "com.google.android.gms.ads.DELAY_APP_MEASUREMENT_INIT",
            ),
        )
    }
}
