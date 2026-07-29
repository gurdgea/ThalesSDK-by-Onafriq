package com.mobile.app.d1pay

import org.junit.Assert.assertFalse
import org.junit.Test

class D1PayParamsContributorTest {

    /**
     * Guards the reason this class uses reflection: a direct reference to
     * D1PayConfigParams would not compile against the delivered AAR. If this
     * starts failing, a D1Pay-enabled AAR has landed and the contributor can be
     * rewritten as a direct call.
     */
    @Test
    fun `reports unavailable against a non-D1Pay AAR instead of throwing`() {
        assertFalse(D1PayParamsContributor().isAvailable)
    }
}
