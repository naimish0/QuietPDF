package com.rameshta.quietpdf.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/** Owns all full-screen SDK state. It never stores an Activity reference. */
class FullScreenAdCoordinator(
    context: Context,
    private val interstitialAdUnitId: String,
    private val appOpenAdUnitId: String,
    private val clock: AdClock = SystemAdClock,
    private val policy: FullScreenAdPolicy = FullScreenAdPolicy(
        FullScreenAdStore(context.applicationContext),
        clock,
    ),
) {
    private val applicationContext = context.applicationContext
    private var interstitial: InterstitialAd? = null
    private var interstitialLoading = false
    private var appOpen: AppOpenAd? = null
    private var appOpenLoading = false
    private var appOpenLoadedAtMillis = 0L

    val isFullScreenAdActive: Boolean get() = policy.isFullScreenAdActive

    fun beginSession() = policy.beginSession()
    fun completeSession() = policy.completeSession()
    fun recordSuccessfulWorkflow(completionId: String) =
        policy.recordSuccessfulWorkflow(completionId)

    fun preload(consentAllowsAds: Boolean) {
        if (!consentAllowsAds) return
        loadInterstitial()
        loadAppOpen()
    }

    fun showInterstitialIfEligible(
        activity: Activity,
        consentAllowsAds: Boolean,
        protectedWorkflowActive: Boolean,
        continueNavigation: () -> Unit,
    ) {
        val ad = interstitial
        if (!policy.tryBeginInterstitial(
                consentAllowsAds = consentAllowsAds,
                adAvailable = ad != null,
                protectedWorkflowActive = protectedWorkflowActive,
            )
        ) {
            continueNavigation()
            preload(consentAllowsAds)
            return
        }
        interstitial = null
        var completed = false
        fun finish() {
            if (completed) return
            completed = true
            policy.release(FullScreenAdFormat.Interstitial)
            continueNavigation()
            preload(consentAllowsAds)
        }
        ad?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                policy.onAdShown(FullScreenAdFormat.Interstitial)
            }
            override fun onAdDismissedFullScreenContent() = finish()
            override fun onAdFailedToShowFullScreenContent(adError: AdError) = finish()
        }
        ad?.show(activity) ?: finish()
    }

    fun showAppOpenIfEligible(
        activity: Activity,
        consentAllowsAds: Boolean,
        safeHomeTransition: Boolean,
        homeAlreadyInteractive: Boolean,
        backgroundDurationMillis: Long?,
    ): Boolean {
        discardExpiredAppOpen()
        val ad = appOpen
        if (!policy.tryBeginAppOpen(
                consentAllowsAds = consentAllowsAds,
                adAvailableAndFresh = ad != null,
                safeHomeTransition = safeHomeTransition,
                homeAlreadyInteractive = homeAlreadyInteractive,
                qualifiedForegroundTransition = isQualifiedAppOpenForegroundTransition(
                    backgroundDurationMillis,
                ),
            )
        ) {
            preload(consentAllowsAds)
            return false
        }
        appOpen = null
        ad?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                policy.onAdShown(FullScreenAdFormat.AppOpen)
            }
            override fun onAdDismissedFullScreenContent() = finishAppOpen(consentAllowsAds)
            override fun onAdFailedToShowFullScreenContent(adError: AdError) =
                finishAppOpen(consentAllowsAds)
        }
        if (ad == null) {
            policy.release(FullScreenAdFormat.AppOpen)
            return false
        }
        ad.show(activity)
        return true
    }

    private fun finishAppOpen(consentAllowsAds: Boolean) {
        policy.release(FullScreenAdFormat.AppOpen)
        preload(consentAllowsAds)
    }

    private fun loadInterstitial() {
        if (interstitialAdUnitId.isBlank() || interstitial != null || interstitialLoading) return
        interstitialLoading = true
        InterstitialAd.load(
            applicationContext,
            interstitialAdUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialLoading = false
                    interstitial = ad
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialLoading = false
                    interstitial = null
                }
            },
        )
    }

    private fun loadAppOpen() {
        discardExpiredAppOpen()
        if (appOpenAdUnitId.isBlank() || appOpen != null || appOpenLoading) return
        appOpenLoading = true
        AppOpenAd.load(
            applicationContext,
            appOpenAdUnitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenLoading = false
                    appOpen = ad
                    appOpenLoadedAtMillis = clock.elapsedRealtime()
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    appOpenLoading = false
                    appOpen = null
                }
            },
        )
    }

    private fun discardExpiredAppOpen() {
        if (appOpen != null &&
            clock.elapsedRealtime() - appOpenLoadedAtMillis >= FullScreenAdPolicy.APP_OPEN_EXPIRY_MILLIS
        ) appOpen = null
    }
}

internal fun isQualifiedAppOpenForegroundTransition(backgroundDurationMillis: Long?): Boolean =
    backgroundDurationMillis == null ||
        backgroundDurationMillis >= FullScreenAdPolicy.MIN_BACKGROUND_MILLIS
