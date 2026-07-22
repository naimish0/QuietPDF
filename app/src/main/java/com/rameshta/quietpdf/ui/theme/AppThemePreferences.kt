package com.rameshta.quietpdf.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

enum class AppThemeMode(val storedValue: String) {
    Light("light"),
    Dark("dark");

    companion object {
        fun fromStoredValue(value: String?, fallback: AppThemeMode): AppThemeMode =
            entries.firstOrNull { it.storedValue == value } ?: fallback
    }
}

class AppThemePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )

    fun read(fallback: AppThemeMode): AppThemeMode = AppThemeMode.fromStoredValue(
        preferences.getString(ThemeKey, null),
        fallback,
    )

    fun write(mode: AppThemeMode) {
        preferences.edit().putString(ThemeKey, mode.storedValue).apply()
    }

    private companion object {
        const val PreferencesName = "quietpdf_appearance"
        const val ThemeKey = "theme_mode"
    }
}

class LauncherIconController(private val context: Context) {
    fun apply(mode: AppThemeMode) {
        val packageManager = context.packageManager
        val light = ComponentName(context.packageName, "${context.packageName}.LightLauncherAlias")
        val dark = ComponentName(context.packageName, "${context.packageName}.DarkLauncherAlias")
        val enabled = if (mode == AppThemeMode.Dark) dark else light
        val disabled = if (mode == AppThemeMode.Dark) light else dark

        if (isAlreadyApplied(packageManager, enabled, disabled)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.setComponentEnabledSettings(
                listOf(
                    PackageManager.ComponentEnabledSetting(
                        enabled,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP,
                    ),
                    PackageManager.ComponentEnabledSetting(
                        disabled,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    ),
                ),
            )
        } else {
            setStateIfNeeded(packageManager, enabled, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            setStateIfNeeded(packageManager, disabled, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        }
    }

    private fun isAlreadyApplied(
        packageManager: PackageManager,
        enabled: ComponentName,
        disabled: ComponentName,
    ): Boolean =
        packageManager.getComponentEnabledSetting(enabled) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED &&
            packageManager.getComponentEnabledSetting(disabled) ==
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    private fun setStateIfNeeded(
        packageManager: PackageManager,
        component: ComponentName,
        state: Int,
    ) {
        if (packageManager.getComponentEnabledSetting(component) == state) return
        packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
    }
}
