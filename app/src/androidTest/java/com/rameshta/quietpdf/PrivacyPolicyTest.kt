package com.rameshta.quietpdf

import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacyPolicyTest {
    @Test
    fun packagedPolicyContainsCurrentContactAgeAndAdvertisingDisclosure() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val policy = context.assets.open("index.html").bufferedReader().use { it.readText() }

        assertTrue(policy.contains("mailto:naimish.app@gmail.com"))
        assertFalse(policy.contains("mailto:naimishgupta983842377@gmail.com"))
        assertTrue(policy.contains("intended for users aged 13 and older"))
        assertTrue(policy.contains("Selected app language"))
        assertTrue(policy.contains("does not force an\n        age-restricted advertising treatment"))
        assertFalse(policy.contains("teen age-restricted treatment"))
    }

    @Test
    fun fullPolicyViewerIsPackagedAsANonExportedActivity() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val activityInfo = context.packageManager.getActivityInfo(
            ComponentName(context, PrivacyPolicyActivity::class.java),
            0,
        )

        assertFalse(activityInfo.exported)
    }
}
