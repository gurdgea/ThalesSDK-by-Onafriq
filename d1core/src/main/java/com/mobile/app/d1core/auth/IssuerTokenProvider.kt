package com.mobile.app.d1core.auth

import com.mobile.app.d1core.config.D1Config
import com.mobile.app.d1core.error.D1Failure

/**
 * Supplies the issuer access token that [com.thalesgroup.gemalto.d1.D1Task.login]
 * exchanges for a D1 session.
 *
 * [bindingPayload] is the value of `D1Task.getBindingHash()`; when client binding
 * is enabled the backend must embed it verbatim as the JWT `cbp` claim.
 */
fun interface IssuerTokenProvider {
    suspend fun issuerToken(bindingPayload: String?): ByteArray
}

class ConfigIssuerTokenProvider(private val config: D1Config) : IssuerTokenProvider {

    override suspend fun issuerToken(bindingPayload: String?): ByteArray {
        val token = config.issuerAccessToken ?: throw D1Failure.ConfigInvalid(
            "ISSUER_ACCESS_TOKEN is blank in config/${config.environment}.properties. " +
                "Paste a token minted by the issuer backend, or supply a real " +
                "IssuerTokenProvider that fetches one.",
            null,
            null,
        )

        if (bindingPayload != null) throw D1Failure.ConfigInvalid(
            "Client binding is enabled, so the token must carry the SDK binding " +
                "payload as its 'cbp' claim. A static ISSUER_ACCESS_TOKEN cannot: " +
                "the payload is generated per device, per session. Either set " +
                "CLIENT_BINDING_ENABLED=false in config/${config.environment}.properties " +
                "or supply an IssuerTokenProvider backed by the issuer backend.",
            null,
            null,
        )

        return token.toByteArray(Charsets.UTF_8)
    }
}
