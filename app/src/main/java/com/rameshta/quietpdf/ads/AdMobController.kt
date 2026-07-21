package com.rameshta.quietpdf.ads

import android.app.Activity
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.rameshta.quietpdf.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AdMobState(
    val canRequestAds: Boolean = false,
    val privacyOptionsRequired: Boolean = false,
)

/** Keeps consent and ad initialization isolated from all document state and metadata. */
class AdMobController {
    private val started = AtomicBoolean(false)
    private val initialized = AtomicBoolean(false)
    private val mutableState = MutableStateFlow(AdMobState())

    val state: StateFlow<AdMobState> = mutableState.asStateFlow()

    fun start(activity: Activity) {
        if (!BuildConfig.ADMOB_ENABLED || !started.compareAndSet(false, true)) return
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val parameters = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            parameters,
            {
                updateState(consentInformation)
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    updateState(consentInformation)
                    initializeAdsIfAllowed(activity, consentInformation)
                }
            },
            {
                // A network, inventory, or consent error never blocks a PDF feature. A previously
                // cached consent decision may still permit ads for this session.
                updateState(consentInformation)
                initializeAdsIfAllowed(activity, consentInformation)
            },
        )
    }

    fun showPrivacyOptions(activity: Activity, onUnavailable: () -> Unit) {
        if (mutableState.value.privacyOptionsRequired) {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
                val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
                updateState(consentInformation)
                initializeAdsIfAllowed(activity, consentInformation)
                if (error != null) onUnavailable()
            }
        } else {
            onUnavailable()
        }
    }

    private fun updateState(consentInformation: ConsentInformation) {
        mutableState.value = AdMobState(
            canRequestAds = BuildConfig.ADMOB_ENABLED && consentInformation.canRequestAds(),
            privacyOptionsRequired = consentInformation.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED,
        )
    }

    private fun initializeAdsIfAllowed(
        activity: Activity,
        consentInformation: ConsentInformation,
    ) {
        if (!consentInformation.canRequestAds() || !initialized.compareAndSet(false, true)) return
        MobileAds.initialize(activity)
    }
}
