package com.rameshta.quietpdf.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullScreenAdPolicyTest {
    private val store = FakeStore()
    private val clock = FakeClock()
    private val policy = FullScreenAdPolicy(store, clock)

    @Test
    fun interstitialRequiresTwoUniqueSuccessfulWorkflows() {
        policy.recordSuccessfulWorkflow("compress:one")
        policy.recordSuccessfulWorkflow("compress:one")
        assertFalse(policy.tryInterstitial())
        policy.recordSuccessfulWorkflow("merge:two")
        assertTrue(policy.tryInterstitial())
        policy.onAdShown(FullScreenAdFormat.Interstitial)
        policy.release(FullScreenAdFormat.Interstitial)
        assertEquals(0, store.successfulWorkflowCount)
    }

    @Test
    fun failedCancelledAndNoOutputWorkflowsDoNotCountUnlessCallerRecordsSuccess() {
        assertEquals(0, store.successfulWorkflowCount)
        policy.recordSuccessfulWorkflow("")
        assertEquals(0, store.successfulWorkflowCount)
    }

    @Test
    fun unavailableInterstitialNeverBlocksOrResetsEligibility() {
        policy.recordSuccessfulWorkflow("one")
        policy.recordSuccessfulWorkflow("two")
        assertFalse(policy.tryInterstitial(adAvailable = false))
        assertEquals(2, store.successfulWorkflowCount)
        assertTrue(policy.tryInterstitial())
        policy.release(FullScreenAdFormat.Interstitial)
        assertEquals(2, store.successfulWorkflowCount)
    }

    @Test
    fun sharedCooldownAndThreeInterstitialSessionLimitAreEnforced() {
        repeat(3) { index ->
            policy.recordSuccessfulWorkflow("$index-a")
            policy.recordSuccessfulWorkflow("$index-b")
            if (index > 0) {
                clock.elapsed += FullScreenAdPolicy.SHARED_COOLDOWN_MILLIS
                clock.wall += FullScreenAdPolicy.SHARED_COOLDOWN_MILLIS
            }
            assertTrue(policy.tryInterstitial())
            policy.onAdShown(FullScreenAdFormat.Interstitial)
            policy.release(FullScreenAdFormat.Interstitial)
        }
        clock.elapsed += FullScreenAdPolicy.SHARED_COOLDOWN_MILLIS
        clock.wall += FullScreenAdPolicy.SHARED_COOLDOWN_MILLIS
        policy.recordSuccessfulWorkflow("four-a")
        policy.recordSuccessfulWorkflow("four-b")
        assertFalse(policy.tryInterstitial())
    }

    @Test
    fun appOpenStartsOnlyAfterTwoCompletedSessions() {
        policy.beginSession()
        assertFalse(policy.tryAppOpen())
        policy.completeSession()
        policy.beginSession()
        assertFalse(policy.tryAppOpen())
        policy.completeSession()
        policy.beginSession()
        assertTrue(policy.tryAppOpen())
    }

    @Test
    fun consentOrUnavailableAppOpenNeverStartsFullScreenState() {
        store.completedSessionCount = 2
        assertFalse(
            policy.tryBeginAppOpen(false, true, true, false),
        )
        assertFalse(
            policy.tryBeginAppOpen(true, false, true, false),
        )
        assertFalse(policy.isFullScreenAdActive)
    }

    @Test
    fun appOpenHonorsHomeReadinessAndFourHourInterval() {
        store.completedSessionCount = 2
        assertFalse(policy.tryAppOpen(homeInteractive = true))
        assertTrue(policy.tryAppOpen())
        policy.onAdShown(FullScreenAdFormat.AppOpen)
        policy.release(FullScreenAdFormat.AppOpen)

        clock.elapsed += FullScreenAdPolicy.SHARED_COOLDOWN_MILLIS
        clock.wall += FullScreenAdPolicy.SHARED_COOLDOWN_MILLIS
        assertFalse(policy.tryAppOpen())
        clock.wall += FullScreenAdPolicy.APP_OPEN_INTERVAL_MILLIS
        assertTrue(policy.tryAppOpen())
        policy.onAdShown(FullScreenAdFormat.AppOpen)
        policy.release(FullScreenAdFormat.AppOpen)
    }

    @Test
    fun appOpenRequiresFiveMinuteBackgroundForResumeButAllowsColdStart() {
        store.completedSessionCount = 2
        assertTrue(isQualifiedAppOpenForegroundTransition(null))
        assertFalse(
            isQualifiedAppOpenForegroundTransition(FullScreenAdPolicy.MIN_BACKGROUND_MILLIS - 1),
        )
        assertTrue(
            isQualifiedAppOpenForegroundTransition(FullScreenAdPolicy.MIN_BACKGROUND_MILLIS),
        )
        assertFalse(
            policy.tryBeginAppOpen(true, true, true, false, qualifiedForegroundTransition = false),
        )
        assertTrue(
            policy.tryBeginAppOpen(true, true, true, false, qualifiedForegroundTransition = true),
        )
    }

    @Test
    fun appOpenIsLimitedToTwoImpressionsPerCalendarDay() {
        store.completedSessionCount = 2
        repeat(FullScreenAdPolicy.MAX_APP_OPEN_ADS_PER_DAY) {
            assertTrue(policy.tryAppOpen())
            policy.onAdShown(FullScreenAdFormat.AppOpen)
            policy.release(FullScreenAdFormat.AppOpen)
            store.lastAppOpenWallMillis = Long.MIN_VALUE
            clock.elapsed += FullScreenAdPolicy.SHARED_COOLDOWN_MILLIS
            clock.wall += FullScreenAdPolicy.SHARED_COOLDOWN_MILLIS
        }
        assertFalse(policy.tryAppOpen())

        clock.wall += 24 * 60 * 60 * 1000L
        assertTrue(policy.tryAppOpen())
    }

    @Test
    fun combinedDailyLimitBlocksTheThirteenthFullScreenAdAcrossProcesses() {
        repeat(FullScreenAdPolicy.MAX_FULL_SCREEN_ADS_PER_DAY) { impression ->
            val processPolicy = FullScreenAdPolicy(store, clock)
            processPolicy.recordSuccessfulWorkflow("$impression-a")
            processPolicy.recordSuccessfulWorkflow("$impression-b")
            assertTrue(processPolicy.tryInterstitial())
            processPolicy.onAdShown(FullScreenAdFormat.Interstitial)
            processPolicy.release(FullScreenAdFormat.Interstitial)
            clock.elapsed += FullScreenAdPolicy.SHARED_COOLDOWN_MILLIS
            clock.wall += FullScreenAdPolicy.SHARED_COOLDOWN_MILLIS
        }

        val nextProcess = FullScreenAdPolicy(store, clock)
        nextProcess.recordSuccessfulWorkflow("blocked-a")
        nextProcess.recordSuccessfulWorkflow("blocked-b")
        assertFalse(nextProcess.tryInterstitial())
        store.completedSessionCount = 2
        assertFalse(nextProcess.tryAppOpen())
        assertEquals(FullScreenAdPolicy.MAX_FULL_SCREEN_ADS_PER_DAY, store.fullScreenDayCount)
    }

    @Test
    fun fullScreenFormatsCannotCollideAndProtectedWorkflowsSuppressInterstitials() {
        store.completedSessionCount = 2
        assertTrue(policy.tryAppOpen())
        policy.recordSuccessfulWorkflow("one")
        policy.recordSuccessfulWorkflow("two")
        assertFalse(policy.tryInterstitial())
        policy.release(FullScreenAdFormat.AppOpen)
        assertFalse(policy.tryInterstitial(protected = true))
    }

    private fun FullScreenAdPolicy.tryInterstitial(
        adAvailable: Boolean = true,
        protected: Boolean = false,
    ) = tryBeginInterstitial(true, adAvailable, protected)

    private fun FullScreenAdPolicy.tryAppOpen(
        homeInteractive: Boolean = false,
    ) = tryBeginAppOpen(true, true, true, homeInteractive)

    private class FakeClock(
        var elapsed: Long = 1_000_000L,
        var wall: Long = 1_800_000_000_000L,
    ) : AdClock {
        override fun elapsedRealtime() = elapsed
        override fun currentTimeMillis() = wall
    }

    private class FakeStore : FullScreenAdPersistence {
        override var successfulWorkflowCount = 0
        override var completedSessionCount = 0
        override var lastAppOpenWallMillis = Long.MIN_VALUE
        override var lastFullScreenWallMillis = Long.MIN_VALUE
        override var fullScreenDayKey = Int.MIN_VALUE
        override var fullScreenDayCount = 0
        override var appOpenDayKey = Int.MIN_VALUE
        override var appOpenDayCount = 0
    }
}
