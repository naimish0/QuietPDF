package com.rameshta.quietpdf.ads

import android.content.Context

class FullScreenAdStore(context: Context) : FullScreenAdPersistence {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override var successfulWorkflowCount: Int
        get() = preferences.getInt(SUCCESS_COUNT, 0)
        set(value) { preferences.edit().putInt(SUCCESS_COUNT, value).apply() }
    override var completedSessionCount: Int
        get() = preferences.getInt(SESSION_COUNT, 0)
        set(value) { preferences.edit().putInt(SESSION_COUNT, value).apply() }
    override var lastAppOpenWallMillis: Long
        get() = preferences.getLong(LAST_APP_OPEN, Long.MIN_VALUE)
        set(value) { preferences.edit().putLong(LAST_APP_OPEN, value).apply() }
    override var appOpenDayKey: Int
        get() = preferences.getInt(APP_OPEN_DAY, Int.MIN_VALUE)
        set(value) { preferences.edit().putInt(APP_OPEN_DAY, value).apply() }
    override var appOpenDayCount: Int
        get() = preferences.getInt(APP_OPEN_DAY_COUNT, 0)
        set(value) { preferences.edit().putInt(APP_OPEN_DAY_COUNT, value).apply() }
    override var lastFullScreenWallMillis: Long
        get() = preferences.getLong(LAST_FULL_SCREEN, Long.MIN_VALUE)
        set(value) { preferences.edit().putLong(LAST_FULL_SCREEN, value).apply() }

    private companion object {
        const val PREFERENCES_NAME = "full_screen_ad_frequency"
        const val SUCCESS_COUNT = "successful_workflow_count"
        const val SESSION_COUNT = "completed_session_count"
        const val LAST_APP_OPEN = "last_app_open_wall_millis"
        const val APP_OPEN_DAY = "app_open_day"
        const val APP_OPEN_DAY_COUNT = "app_open_day_count"
        const val LAST_FULL_SCREEN = "last_full_screen_wall_millis"
    }
}
