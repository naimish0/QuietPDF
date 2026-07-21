package com.rameshta.quietpdf.ads

object AdPlacementPolicy {
    fun showBanner(
        screenAllowsBanner: Boolean,
        documentIsClosed: Boolean,
        consentAllowsAds: Boolean,
        isConfigured: Boolean,
    ): Boolean = screenAllowsBanner && documentIsClosed && consentAllowsAds && isConfigured
}
