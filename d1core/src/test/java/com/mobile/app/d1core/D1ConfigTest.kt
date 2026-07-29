package com.mobile.app.d1core

import com.mobile.app.d1core.config.D1Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class D1ConfigTest {

    private fun config(
        serviceUrl: String = "https://example.test",
        issuerId: String = "issuer",
        digitalCardUrl: String = "https://cards.example.test",
        modulus: String = "00B1",
        exponent: String = "010001",
        consumerId: String = "consumer",
    ) = D1Config(
        environment = "staging",
        serviceUrl = serviceUrl,
        issuerId = issuerId,
        digitalCardUrl = digitalCardUrl,
        rsaModulusHex = modulus,
        rsaExponentHex = exponent,
        applicationProfileId = null,
        consumerId = consumerId,
        cardId = null,
        visaClientAppId = null,
        clientBindingEnabled = true,
        secureLogEnabled = true,
        issuerAccessToken = null,
    )

    @Test
    fun `complete config reports no missing keys`() {
        assertTrue(config().validate().isEmpty())
    }

    @Test
    fun `reports every missing key at once rather than the first`() {
        val missing = config(
            serviceUrl = "",
            issuerId = "REPLACE_ME_ISSUER_ID",
            consumerId = "",
        ).validate()

        assertEquals(listOf("D1_SERVICE_URL", "ISSUER_ID", "CONSUMER_ID"), missing)
    }

    @Test
    fun `treats REPLACE_ME placeholders as missing`() {
        assertEquals(listOf("ISSUER_ID"), config(issuerId = "REPLACE_ME_ISSUER_ID").validate())
    }

    @Test
    fun `rejects malformed rsa material`() {
        val missing = config(modulus = "XYZ", exponent = "").validate()
        assertEquals(
            listOf("D1_SERVICE_RSA_MODULUS", "D1_SERVICE_RSA_EXPONENT"),
            missing,
        )
    }
}
