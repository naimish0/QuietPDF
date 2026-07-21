package com.rameshta.quietpdf.ads

object AdPlacementPolicy {
    fun showHomeBanner(
        isHome: Boolean,
        documentIsClosed: Boolean,
        operationsAreIdle: Boolean,
        consentAllowsAds: Boolean,
        isConfigured: Boolean,
    ): Boolean = isHome && documentIsClosed && operationsAreIdle && consentAllowsAds && isConfigured
}
