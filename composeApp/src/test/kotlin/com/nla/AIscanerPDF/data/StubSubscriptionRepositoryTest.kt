package com.nla.AIscanerPDF.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.aiscanner.docs.data.repository.StubSubscriptionRepository
import ru.aiscanner.docs.domain.model.PurchaseResult
import ru.aiscanner.docs.domain.model.SubscriptionStatus

class StubSubscriptionRepositoryTest {

    @Test
    fun `initial status is free`() = runTest {
        val repo = StubSubscriptionRepository()
        assertEquals(SubscriptionStatus.Free, repo.subscriptionStatus.first())
    }

    @Test
    fun `products contain monthly and yearly`() = runTest {
        val products = StubSubscriptionRepository().loadProducts()
        assertEquals(
            setOf("premium_monthly", "premium_yearly"),
            products.map { it.productId }.toSet(),
        )
    }

    @Test
    fun `purchase is unavailable before rustore integration`() = runTest {
        val result = StubSubscriptionRepository().purchase("premium_monthly")
        assertTrue(result is PurchaseResult.Error)
    }
}
