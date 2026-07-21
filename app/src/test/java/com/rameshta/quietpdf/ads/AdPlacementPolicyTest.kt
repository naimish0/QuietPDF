package com.rameshta.quietpdf.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdPlacementPolicyTest {
    @Test
    fun bannerIsAllowedOnlyOnAnIdleConsentedConfiguredHome() {
        assertTrue(
            AdPlacementPolicy.showHomeBanner(
                isHome = true,
                documentIsClosed = true,
                operationsAreIdle = true,
                consentAllowsAds = true,
                isConfigured = true,
            ),
        )
    }

    @Test
    fun everySafetyGateIndependentlySuppressesTheBanner() {
        val allowed = listOf(true, true, true, true, true)
        allowed.indices.forEach { disabledIndex ->
            val values = allowed.toMutableList().also { it[disabledIndex] = false }
            assertFalse(
                AdPlacementPolicy.showHomeBanner(
                    isHome = values[0],
                    documentIsClosed = values[1],
                    operationsAreIdle = values[2],
                    consentAllowsAds = values[3],
                    isConfigured = values[4],
                ),
            )
        }
    }
}
