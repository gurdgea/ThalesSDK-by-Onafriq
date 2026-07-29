---
title: Authentication
layout: default
nav_order: 6
---

# Authentication
{: .no_toc }

Login, session management, and client binding.
{: .fs-6 .fw-300 }

1. TOC
{:toc}

---

## Overview

D1 services require an authenticated session. Your backend mints a short-lived
JWT — the issuer access token — which the SDK exchanges for that session.

The sequence is:

1. Authenticate the end user in your app.
2. Call `getBindingHash()` if client binding is enabled.
3. Send the binding payload to your backend unmodified.
4. The backend returns a JWT containing the payload as the `cbp` claim.
5. Call `login()` with the token.

## Token provider

Define an interface for token retrieval so the implementation can change without
affecting call sites:

```kotlin
fun interface IssuerTokenProvider {
    suspend fun issuerToken(bindingPayload: String?): ByteArray
}
```

`bindingPayload` carries the value returned by `getBindingHash()`. Include the
parameter from the start even if client binding is initially disabled, so
enabling it later does not require changing every login path.

A configuration-backed implementation is sufficient during development:

```kotlin
class ConfigIssuerTokenProvider(private val config: D1Config) : IssuerTokenProvider {

    override suspend fun issuerToken(bindingPayload: String?): ByteArray {
        val token = config.issuerAccessToken ?: throw D1Failure.ConfigInvalid(
            "ISSUER_ACCESS_TOKEN is blank in config/${config.environment}.properties.",
            null, null,
        )

        if (bindingPayload != null) throw D1Failure.ConfigInvalid(
            "Client binding is enabled, so the token must carry the SDK binding " +
                "payload as its 'cbp' claim. Set CLIENT_BINDING_ENABLED=false or " +
                "supply a backend-backed provider.",
            null, null,
        )

        return token.toByteArray(Charsets.UTF_8)
    }
}
```

{: .important }
> A static token cannot be used with client binding. The payload is generated per
> device and per session, so it cannot be embedded in a token pasted into
> configuration. Disable client binding for local development, or supply a
> provider that calls your backend.

## Logging in

```kotlin
suspend fun login() {
    val payload = if (clientBindingEnabled) gateway.bindingHash() else null
    val token = tokenProvider.issuerToken(payload)
    try {
        gateway.login(token)
    } finally {
        token.fill(0)
    }
    _state.value = D1SessionState.LoggedIn
}
```

The token is a `ByteArray` so it can be cleared. Zero it once the call
completes, including when the call fails.

The SDK also accepts a list of tokens for multi-issuer sessions:

```java
void login(byte[] token, D1Task.Callback<Void> callback)
void login(List<byte[]> tokens, D1Task.Callback<Void> callback)
```

## Session lifetime

| Scope | Validity |
|:---|:---|
| Default session | Approximately one hour |
| Sensitive operations | A few minutes |
| Change PIN | Shortest window of any API |

An expired session surfaces as `ERROR_NOT_LOGGED_IN`. Treat it as an expected
branch rather than a failure, and implement renewal before any feature that
depends on it.

```kotlin
suspend fun <T> withSession(block: suspend () -> T): T {
    blocked?.let { throw it }
    return try {
        guarded(block)
    } catch (expired: D1Failure.NotLoggedIn) {
        login()
        guarded(block)
    }
}

private suspend fun <T> guarded(block: suspend () -> T): T = try {
    block()
} catch (unsafe: D1Failure.DeviceUnsafe) {
    latch(unsafe)
    throw unsafe
}
```

Route every service call through this wrapper. Retry once only: a second expiry
immediately after a successful login indicates a real problem, and repeated
retries would hide it.

### Change PIN

Change PIN enforces a shorter window than other APIs. A renewal triggered after
the user has entered a new PIN arrives too late, so refresh the session
immediately before submitting:

```kotlin
suspend fun submitChangePin(pinEntryUI: PINEntryUI) {
    session.login()
    awaitVoid { pinEntryUI.submit(it) }
}
```

## Client binding

Client binding ties a session to the device. The SDK signs each request with
device keys and D1 verifies the origin. Key pairs are short-lived, and the public
key exchange happens during login, so binding exists only for an authenticated
session.

When enabled, call `getBindingHash()` before requesting a token and pass the
result to your backend without modification:

```kotlin
val payload = task.bindingHash
```

The backend includes it as the `cbp` claim.

## Token format

Header:

```json
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "iss1_kid"
}
```

Payload:

| Claim | Type | Required | Description |
|:---|:---|:--|:---|
| `exp` | integer | Yes | Expiry, Unix seconds |
| `scope` | string | Yes | Space-separated scopes agreed at onboarding |
| `aud` | string or array | Yes | `https://{D1-client-api-domain}/oidc/{issuerId}` |
| `jti` | string | Yes | Unique token identifier |
| `iss` | string | Yes | Issuer identifier |
| `sub` | string | Yes | `consumerId`; space-separated for multiple |
| `iat` | integer | Yes | Issued-at timestamp |
| `cbp` | string | Conditional | Client binding payload, unmodified |

```json
{
  "jti": "M9JHKtLdfXu782EH3hMf_",
  "sub": "testuser",
  "iat": 1626836247,
  "exp": 1627441047,
  "scope": "digibank:mobilebanking digibank:ecommerce",
  "iss": "tenant1",
  "aud": "https://client-api.d1.thalescloud.io/oidc/tenant1"
}
```

Set `iss` to `issuerId` when using client binding, and for both single- and
multi-issuer tokens when the public key is provisioned in the tenant
configuration. When it is not provisioned, single-issuer tokens must set `iss` to
the `jwks_uri` value from `/.well-known/openid-configuration` so D1 can retrieve
the key.

**Signing algorithms.** Recommended: `ES256`, `ES384`, `ES512`. Also supported:
`RS256`, `RS512`, `PS256`, `PS384`, `PS512`, `EdDSA (Ed25519)`.

Use short expiry times, and never reuse a token across login attempts.

## Logging out

```kotlin
suspend fun logout()      // revokes tokens for this D1Task and issuerId
suspend fun logoutAll()   // revokes all tokens for all D1Task instances
```

Call one of these before signing the user out of your app. Use `logoutAll()`
when the app maintains more than one `D1Task` instance.

## Next

Continue to [Services](services.html).
