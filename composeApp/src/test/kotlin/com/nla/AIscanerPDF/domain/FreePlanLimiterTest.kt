package com.nla.AIscanerPDF.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.aiscanner.docs.domain.logic.FreePlanLimiter
import ru.aiscanner.docs.domain.model.FreePlanLimits
import ru.aiscanner.docs.domain.model.SubscriptionStatus

class FreePlanLimiterTest {

    private val limiter = FreePlanLimiter(
        FreePlanLimits(maxPagesPerPdf = 5, freeOcrOperations = 3, freeAiOperations = 1, adsEnabled = false),
    )

    @Test
    fun `free user can export pdf within page limit`() {
        assertTrue(limiter.canExportPdf(SubscriptionStatus.Free, 5))
        assertFalse(limiter.canExportPdf(SubscriptionStatus.Free, 6))
    }

    @Test
    fun `premium user bypasses pdf page limit`() {
        assertTrue(limiter.canExportPdf(SubscriptionStatus.Premium(null), 100))
    }

    @Test
    fun `free ocr operations are limited`() {
        assertTrue(limiter.canRunOcr(SubscriptionStatus.Free, 2))
        assertFalse(limiter.canRunOcr(SubscriptionStatus.Free, 3))
        assertTrue(limiter.canRunOcr(SubscriptionStatus.Premium(null), 100))
    }

    @Test
    fun `free ai operations are limited`() {
        assertTrue(limiter.canRunAi(SubscriptionStatus.Free, 0))
        assertFalse(limiter.canRunAi(SubscriptionStatus.Free, 1))
        assertTrue(limiter.canRunAi(SubscriptionStatus.Premium(null), 100))
    }
}
