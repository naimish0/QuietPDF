package com.rameshta.quietpdf

import android.graphics.drawable.AdaptiveIconDrawable
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import com.rameshta.quietpdf.ui.theme.AppThemeMode
import com.rameshta.quietpdf.ui.theme.AppThemePreferences
import com.rameshta.quietpdf.ui.theme.LauncherIconController

@RunWith(AndroidJUnit4::class)
class LauncherIconTest {
    @Test
    fun launcherUsesBrandedAdaptiveAndRoundIcons() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val applicationInfo = context.applicationInfo
        val packageManager = context.packageManager

        assertTrue(packageManager.getApplicationIcon(applicationInfo) is AdaptiveIconDrawable)
        assertTrue(context.getDrawable(R.mipmap.ic_launcher_round) is AdaptiveIconDrawable)
        assertTrue(context.getDrawable(R.mipmap.ic_launcher_dark) is AdaptiveIconDrawable)
        assertTrue(context.getDrawable(R.mipmap.ic_launcher_dark_round) is AdaptiveIconDrawable)
    }

    @Test
    fun themeSelectionSwitchesLauncherAliasWithoutKillingTheApp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val packageManager = context.packageManager
        val light = ComponentName(context.packageName, "${context.packageName}.LightLauncherAlias")
        val dark = ComponentName(context.packageName, "${context.packageName}.DarkLauncherAlias")
        val controller = LauncherIconController(context)

        try {
            controller.apply(AppThemeMode.Dark)
            assertTrue(
                packageManager.getComponentEnabledSetting(dark) ==
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            )
            assertTrue(
                packageManager.getComponentEnabledSetting(light) ==
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            )
        } finally {
            controller.apply(AppThemeMode.Light)
        }
    }

    @Test
    fun selectedThemePersistsAcrossPreferenceInstances() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = AppThemePreferences(context)
        try {
            preferences.write(AppThemeMode.Dark)
            assertTrue(AppThemePreferences(context).read(AppThemeMode.Light) == AppThemeMode.Dark)
        } finally {
            preferences.write(AppThemeMode.Light)
        }
    }
}
