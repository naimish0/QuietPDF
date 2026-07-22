package com.rameshta.quietpdf

import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherIconTest {
    @Test
    fun launcherUsesBrandedAdaptiveAndRoundIcons() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val applicationInfo = context.applicationInfo
        val packageManager = context.packageManager

        assertTrue(packageManager.getApplicationIcon(applicationInfo) is AdaptiveIconDrawable)
        assertTrue(context.getDrawable(R.mipmap.ic_launcher_round) is AdaptiveIconDrawable)
    }
}
