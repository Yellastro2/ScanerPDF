package ru.aiscanner.docs.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.aiscanner.docs.data.backend.BackendSession

class BackendSessionTest {

    private val session = BackendSession(
        accessToken = "token",
        tokenExpiresAtMillis = 2_000L,
        purchaseId = "purchase",
        productId = "premium_monthly",
        subscriptionValidUntilMillis = 3_000L,
    )

    @Test
    fun `session validates token and subscription independently`() {
        assertTrue(session.hasValidToken(nowMillis = 1_000L))
        assertTrue(session.hasActiveSubscription(nowMillis = 1_000L))
        assertFalse(session.hasValidToken(nowMillis = 2_000L))
        assertTrue(session.hasActiveSubscription(nowMillis = 2_000L))
        assertFalse(session.hasActiveSubscription(nowMillis = 3_000L))
    }
}
