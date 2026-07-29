package com.mobile.app.d1core.config

import com.mobile.app.d1core.BuildConfig
import com.mobile.app.d1core.util.hexToBytes

data class D1Config(
    val environment: String,
    val serviceUrl: String,
    val issuerId: String,
    val digitalCardUrl: String,
    val rsaModulusHex: String,
    val rsaExponentHex: String,
    val applicationProfileId: String?,
    val consumerId: String,
    val cardId: String?,
    val visaClientAppId: String?,
    val clientBindingEnabled: Boolean,
    val secureLogEnabled: Boolean,
    val issuerAccessToken: String?,
) {
    val rsaModulus: ByteArray get() = rsaModulusHex.hexToBytes()
    val rsaExponent: ByteArray get() = rsaExponentHex.hexToBytes()

    fun validate(): List<String> = buildList {
        if (serviceUrl.isBlank() || serviceUrl.isPlaceholder()) add("D1_SERVICE_URL")
        if (issuerId.isBlank() || issuerId.isPlaceholder()) add("ISSUER_ID")
        if (digitalCardUrl.isBlank() || digitalCardUrl.isPlaceholder()) add("DIGITAL_CARD_URL")
        if (consumerId.isBlank() || consumerId.isPlaceholder()) add("CONSUMER_ID")
        if (!rsaModulusHex.isValidHex()) add("D1_SERVICE_RSA_MODULUS")
        if (!rsaExponentHex.isValidHex()) add("D1_SERVICE_RSA_EXPONENT")
    }

    companion object {
        fun fromBuildConfig(): D1Config = D1Config(
            environment = BuildConfig.ENV_NAME,
            serviceUrl = BuildConfig.D1_SERVICE_URL,
            issuerId = BuildConfig.ISSUER_ID,
            digitalCardUrl = BuildConfig.DIGITAL_CARD_URL,
            rsaModulusHex = BuildConfig.D1_SERVICE_RSA_MODULUS,
            rsaExponentHex = BuildConfig.D1_SERVICE_RSA_EXPONENT,
            applicationProfileId = BuildConfig.APPLICATION_PROFILE_ID.nullIfBlank(),
            consumerId = BuildConfig.CONSUMER_ID,
            cardId = BuildConfig.CARD_ID.nullIfBlank(),
            visaClientAppId = BuildConfig.VISA_CLIENT_APP_ID.nullIfBlank(),
            clientBindingEnabled = BuildConfig.CLIENT_BINDING_ENABLED,
            secureLogEnabled = BuildConfig.SECURE_LOG_ENABLED,
            issuerAccessToken = BuildConfig.ISSUER_ACCESS_TOKEN.nullIfBlank(),
        )
    }
}

class D1ConfigException(
    val environment: String,
    val missingKeys: List<String>,
) : IllegalStateException(
    "Incomplete D1 configuration in config/$environment.properties. " +
        "Supply real onboarding values for: ${missingKeys.joinToString()}"
)

private fun String.nullIfBlank(): String? = trim().ifBlank { null }

private fun String.isPlaceholder(): Boolean = startsWith("REPLACE_ME")

private fun String.isValidHex(): Boolean =
    isNotBlank() && length % 2 == 0 && all { it.digitToIntOrNull(16) != null }
