package com.rameshta.quietpdf.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdPlacementPolicyTest {
    @Test
    fun bannerIsAllowedOnAnIdleConsentedConfiguredNavigationScreen() {
        assertTrue(
            AdPlacementPolicy.showBanner(
                screenAllowsBanner = true,
                documentIsClosed = true,
                consentAllowsAds = true,
                isConfigured = true,
            ),
        )
    }

    @Test
    fun everySafetyGateIndependentlySuppressesTheBanner() {
        val allowed = listOf(true, true, true, true)
        allowed.indices.forEach { disabledIndex ->
            val values = allowed.toMutableList().also { it[disabledIndex] = false }
            assertFalse(
                AdPlacementPolicy.showBanner(
                    screenAllowsBanner = values[0],
                    documentIsClosed = values[1],
                    consentAllowsAds = values[2],
                    isConfigured = values[3],
                ),
            )
        }
    }
}
