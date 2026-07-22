package com.rameshta.quietpdf

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.ads.AgeRestrictedTreatment
import com.google.android.gms.ads.MobileAds
import com.rameshta.quietpdf.ads.configureStandardAgeTreatment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdMobConfigurationTest {
    @Test
    fun adRequestsDoNotForceAgeRestrictedTreatment() {
        configureStandardAgeTreatment()

        assertEquals(
            AgeRestrictedTreatment.UNSPECIFIED,
            MobileAds.getRequestConfiguration().ageRestrictedTreatment,
        )
    }

    @Test
    fun debugBuildUsesOnlyOfficialGoogleTestIdentifiers() {
        assertTrue(BuildConfig.ADMOB_ENABLED)
        assertEquals(
            "ca-app-pub-3940256099942544/9214589741",
            BuildConfig.ADMOB_HOME_BANNER_ID,
        )
        assertEquals(
            "ca-app-pub-3940256099942544/2247696110",
            BuildConfig.ADMOB_NATIVE_ID,
        )
        assertEquals(
            "ca-app-pub-3940256099942544/1033173712",
            BuildConfig.ADMOB_INTERSTITIAL_ID,
        )
        assertEquals(
            "ca-app-pub-3940256099942544/9257395921",
            BuildConfig.ADMOB_APP_OPEN_ID,
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
