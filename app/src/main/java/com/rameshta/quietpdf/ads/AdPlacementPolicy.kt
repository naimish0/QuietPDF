package com.rameshta.quietpdf.ads

object AdPlacementPolicy {
    fun showBanner(
        screenAllowsBanner: Boolean,
        documentIsClosed: Boolean,
        consentAllowsAds: Boolean,
        isConfigured: Boolean,
    ): Boolean = screenAllowsBanner && documentIsClosed && consentAllowsAds && isConfigured

    fun showHomeNative(
        isHome: Boolean,
        documentIsClosed: Boolean,
        operationsAreIdle: Boolean,
        consentAllowsAds: Boolean,
        isConfigured: Boolean,
    ): Boolean = isHome && documentIsClosed && operationsAreIdle && consentAllowsAds && isConfigured
}
