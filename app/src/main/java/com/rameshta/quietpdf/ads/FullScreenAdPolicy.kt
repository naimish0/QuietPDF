package com.rameshta.quietpdf.ads

import android.os.SystemClock
import java.util.Calendar

interface AdClock {
    fun elapsedRealtime(): Long
    fun currentTimeMillis(): Long
}

object SystemAdClock : AdClock {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}

interface FullScreenAdPersistence {
    var successfulWorkflowCount: Int
    var completedSessionCount: Int
    var lastAppOpenWallMillis: Long
    var lastFullScreenWallMillis: Long
    var fullScreenDayKey: Int
    var fullScreenDayCount: Int
}

enum class FullScreenAdFormat { Interstitial, AppOpen }

/** Pure frequency and collision policy. SDK objects and Activity references never enter this type. */
class FullScreenAdPolicy(
    private val persistence: FullScreenAdPersistence,
    private val clock: AdClock = SystemAdClock,
) {
    private val completionIds = mutableSetOf<String>()
    private var lastFullScreenElapsedMillis: Long? = null
    private var activeFormat: FullScreenAdFormat? = null
    private var sessionInterstitialCount = 0
    private var currentSessionCompleted = false

    val isFullScreenAdActive: Boolean get() = activeFormat != null

    fun recordSuccessfulWorkflow(completionId: String) {
        if (completionId.isBlank() || !completionIds.add(completionId)) return
        persistence.successfulWorkflowCount = (persistence.successfulWorkflowCount + 1)
            .coerceAtMost(MAX_PENDING_WORKFLOWS)
    }

    fun beginSession() {
        currentSessionCompleted = false
    }

    fun completeSession() {
        if (currentSessionCompleted) return
        currentSessionCompleted = true
        persistence.completedSessionCount = (persistence.completedSessionCount + 1)
            .coerceAtMost(MAX_COMPLETED_SESSIONS)
    }

    fun tryBeginInterstitial(
        consentAllowsAds: Boolean,
        adAvailable: Boolean,
        protectedWorkflowActive: Boolean,
    ): Boolean {
        if (!consentAllowsAds || !adAvailable || protectedWorkflowActive || isFullScreenAdActive) {
            return false
        }
        if (persistence.successfulWorkflowCount < WORKFLOWS_PER_INTERSTITIAL) return false
        if (sessionInterstitialCount >= MAX_INTERSTITIALS_PER_SESSION) return false
        if (!sharedCooldownPassed()) return false
        if (!dailyFullScreenLimitAllowsAd()) return false
        activeFormat = FullScreenAdFormat.Interstitial
        return true
    }

    fun tryBeginAppOpen(
        consentAllowsAds: Boolean,
        adAvailableAndFresh: Boolean,
        safeHomeTransition: Boolean,
        homeAlreadyInteractive: Boolean,
    ): Boolean {
        if (!consentAllowsAds || !adAvailableAndFresh || !safeHomeTransition ||
            homeAlreadyInteractive || isFullScreenAdActive
        ) return false
        if (persistence.completedSessionCount < REQUIRED_PRIOR_SESSIONS) return false
        if (!sharedCooldownPassed()) return false
        val now = clock.currentTimeMillis()
        val lastAppOpen = persistence.lastAppOpenWallMillis
        if (lastAppOpen != Long.MIN_VALUE && now - lastAppOpen < APP_OPEN_INTERVAL_MILLIS) {
            return false
        }
        if (!dailyFullScreenLimitAllowsAd(now)) return false
        activeFormat = FullScreenAdFormat.AppOpen
        return true
    }

    fun onAdShown(format: FullScreenAdFormat) {
        if (activeFormat != format) return
        lastFullScreenElapsedMillis = clock.elapsedRealtime()
        val now = clock.currentTimeMillis()
        persistence.lastFullScreenWallMillis = now
        refreshFullScreenDailyCount(now)
        persistence.fullScreenDayCount++
        when (format) {
            FullScreenAdFormat.Interstitial -> {
                sessionInterstitialCount++
                persistence.successfulWorkflowCount = 0
                completionIds.clear()
            }
            FullScreenAdFormat.AppOpen -> {
                persistence.lastAppOpenWallMillis = now
            }
        }
    }

    fun release(format: FullScreenAdFormat) {
        if (activeFormat == format) activeFormat = null
    }

    private fun sharedCooldownPassed(): Boolean {
        val inSessionPassed = lastFullScreenElapsedMillis?.let {
            clock.elapsedRealtime() - it >= SHARED_COOLDOWN_MILLIS
        } ?: true
        val persisted = persistence.lastFullScreenWallMillis
        val acrossProcessPassed = persisted == Long.MIN_VALUE ||
            clock.currentTimeMillis() - persisted >= SHARED_COOLDOWN_MILLIS
        return inSessionPassed && acrossProcessPassed
    }

    private fun dailyFullScreenLimitAllowsAd(now: Long = clock.currentTimeMillis()): Boolean {
        refreshFullScreenDailyCount(now)
        return persistence.fullScreenDayCount < MAX_FULL_SCREEN_ADS_PER_DAY
    }

    private fun refreshFullScreenDailyCount(now: Long) {
        val dayKey = calendarDayKey(now)
        if (persistence.fullScreenDayKey != dayKey) {
            persistence.fullScreenDayKey = dayKey
            persistence.fullScreenDayCount = 0
        }
    }

    private fun calendarDayKey(now: Long): Int = Calendar.getInstance().run {
        timeInMillis = now
        get(Calendar.YEAR) * 1000 + get(Calendar.DAY_OF_YEAR)
    }

    companion object {
        const val WORKFLOWS_PER_INTERSTITIAL = 2
        const val MAX_INTERSTITIALS_PER_SESSION = 3
        const val REQUIRED_PRIOR_SESSIONS = 2
        const val MAX_FULL_SCREEN_ADS_PER_DAY = 12
        const val SHARED_COOLDOWN_MILLIS = 3 * 60 * 1000L
        const val APP_OPEN_INTERVAL_MILLIS = 4 * 60 * 60 * 1000L
        const val APP_OPEN_EXPIRY_MILLIS = 4 * 60 * 60 * 1000L
        private const val MAX_PENDING_WORKFLOWS = 100
        private const val MAX_COMPLETED_SESSIONS = 10_000
    }
}
