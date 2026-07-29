---
title: Error handling
layout: default
nav_order: 9
---

# Error handling
{: .no_toc }

Error codes, classification, and recovery.
{: .fs-6 .fw-300 }

1. TOC
{:toc}

---

## D1Exception

All SDK failures surface as `D1Exception`:

```kotlin
val message: String = exception.message        // full description
val code: ErrorCode = exception.errorCode      // classification
```

Use `message` in support tickets, along with the full stack trace and the value
of `D1Task.getAppInstanceID(context)`. Each API's reference documentation lists
the codes it can produce.

## Error codes

### Session and authorization

| Code | Meaning | Recovery |
|:---|:---|:---|
| `ERROR_NOT_LOGGED_IN` | Session expired | Re-authenticate and retry once |
| `ERROR_NOT_AUTHORIZED` | Operation not permitted | Check scopes in the issuer token |
| `ERROR_CANCELLED` | Operation cancelled | None required |

### Environment

| Code | Meaning | Recovery |
|:---|:---|:---|
| `ERROR_DEVICE_ENVIRONMENT_UNSAFE` | Device judged unsafe | Terminal. All APIs are disabled. |
| `ERROR_DEBUG_SDK_USED` | Debug AAR in a release build | Correct the build type separation |
| `ERROR_NOT_SUPPORTED` | Feature unavailable | Hide the feature |
| `ERROR_INVALID_ARGUMENT` | Invalid parameter | Check configuration values |

### Cards

| Code | Meaning |
|:---|:---|
| `ERROR_CARD_NOT_FOUND` | Unknown card identifier |
| `ERROR_CARD_NOT_MANAGED_BY_D1` | Card is outside D1's control |
| `ERROR_CARD_OPERATION_NOT_ALLOWED` | Operation invalid for the card state |
| `ERROR_CARD_OPERATION_INVALID_REASON` | Invalid reason supplied |
| `ERROR_CARD_NO_PENDING_IDV` | No verification is pending |
| `ERROR_NO_CARD_ACTIVATION_METHOD` | No activation method configured |
| `ERROR_NOT_ACTIVE` | Card is not active |

### Card settings

| Code | Meaning | Recovery |
|:---|:---|:---|
| `ERROR_CARD_SETTINGS_OPERATION_NOT_ALLOWED` | Control not offered by the card product | Hide the control |
| `ERROR_CARD_SETTINGS_INVALID_FORMAT` | Malformed country or currency code | Use ISO 3166-1 alpha-2 and ISO 4217 alpha |
| `ERROR_CARD_SETTINGS_INVALID_VALUE` | Value outside the permitted range | Validate before sending |

### PIN

| Code | Meaning |
|:---|:---|
| `ERROR_PIN_MISMATCH` | Entries do not match |
| `ERROR_PIN_INVALID` | PIN does not meet requirements |
| `ERROR_PIN_CHANGE_FORBIDDEN` | PIN change not permitted for this card |

### Wallets

| Code | Recovery |
|:---|:---|
| `ERROR_SPAY_NEED_TO_UPDATE` | Call `updateSamsungPay()` |
| `ERROR_SPAY_SETUP_NOT_COMPLETED` | Call `activateSamsungPay()` |
| `ERROR_SPAY_APP_NOT_FOUND` | Prompt the user to install Samsung Pay |
| `ERROR_SPAY_NOT_SUPPORTED` | Hide Samsung Pay UI |
| `ERROR_GPAY_NOT_SUPPORTED` | Hide Google Pay UI |
| `ERROR_SPAY`, `ERROR_GPAY` | General wallet failure |

### Other

`ERROR_CORE`, `ERROR_RISK`, `ERROR_CARD`, `ERROR_UI_COMPONENT_NOT_FOUND`,
`ERROR_PUSH_TOKEN_NOT_FOUND`, `ERROR_CLICKTOPAY`, and the `ERROR_AUTHN_*` and
`ERROR_D1PAY_*` families.

## Classification

Map SDK codes to application-level types once, at the SDK boundary, so callers
branch on meaning rather than inspecting codes:

```kotlin
sealed class D1Failure(
    message: String,
    val code: ErrorCode?,
    cause: Throwable?,
) : Exception(message, cause) {

    class NotLoggedIn(code: ErrorCode?, cause: Throwable?) : D1Failure(...)
    class DeviceUnsafe(code: ErrorCode?, cause: Throwable?) : D1Failure(...)
    class DebugSdkInRelease(code: ErrorCode?, cause: Throwable?) : D1Failure(...)
    class NotAuthorized(code: ErrorCode?, cause: Throwable?) : D1Failure(...)
    class Cancelled(code: ErrorCode?, cause: Throwable?) : D1Failure(...)
    class CardNotFound(code: ErrorCode?, cause: Throwable?) : D1Failure(...)
    class CardSettingsRejected(message: String, code: ErrorCode?, cause: Throwable?) : D1Failure(...)
    class PinRejected(message: String, code: ErrorCode?, cause: Throwable?) : D1Failure(...)
    class WalletUnavailable(message: String, code: ErrorCode?, cause: Throwable?) : D1Failure(...)
    class D1PayUnavailable(message: String, code: ErrorCode?, cause: Throwable?) : D1Failure(...)
    class ConfigInvalid(message: String, code: ErrorCode?, cause: Throwable?) : D1Failure(...)
    class Unknown(message: String, code: ErrorCode?, cause: Throwable?) : D1Failure(...)
}
```

Implement the mapping as a pure function keyed on the code name. This keeps it
testable without an Android runtime and routes codes added in later SDK versions
by prefix rather than requiring an exhaustive list:

```kotlin
fun D1Exception.toFailure(): D1Failure {
    val code = runCatching { errorCode }.getOrNull()
    val name = runCatching { code?.name }.getOrNull()
    return failureFor(name, message ?: name ?: "Unknown D1 error", code, this)
}

internal fun failureFor(
    codeName: String?,
    message: String,
    code: ErrorCode?,
    cause: Throwable?,
): D1Failure = when (codeName) {
    "ERROR_NOT_LOGGED_IN" -> D1Failure.NotLoggedIn(code, cause)
    "ERROR_DEVICE_ENVIRONMENT_UNSAFE" -> D1Failure.DeviceUnsafe(code, cause)
    in CARD_SETTINGS_CODES -> D1Failure.CardSettingsRejected(message, code, cause)
    in PIN_CODES -> D1Failure.PinRejected(message, code, cause)
    in WALLET_CODES -> D1Failure.WalletUnavailable(message, code, cause)
    else -> when {
        codeName?.startsWith("ERROR_D1PAY") == true ->
            D1Failure.D1PayUnavailable(message, code, cause)
        else -> D1Failure.Unknown(message, code, cause)
    }
}
```

See [Testing](testing.html) for verifying the mapping table.

## Recovery patterns

### Session expiry

Expected behaviour, not an error condition. Renew and retry once:

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
```

### Unsafe device

Terminal for the process. Record the condition and stop issuing calls:

```kotlin
private suspend fun <T> guarded(block: suspend () -> T): T = try {
    block()
} catch (unsafe: D1Failure.DeviceUnsafe) {
    latch(unsafe)
    throw unsafe
}
```

Present a blocking message; no D1 functionality is available.

### Partial configuration failure

`configure()` reports each target independently. Act on entries individually
rather than treating any failure as fatal. See
[Initialization](initialization.html).

### Successful calls with negative results

Some operations report a failed outcome through a successful callback.
`updateDigitalCard` delivers `onSuccess(false)` for an invalid state transition.
Model these separately from errors:

```kotlin
enum class DigitalCardUpdate { Applied, InvalidTransition }
```

### Absent optional values

A `null` control in `CardSettings` means the card product does not offer that
control. Omit the UI rather than presenting it as unavailable.

## Diagnostics

```kotlin
D1Task.getSDKVersions()             // component versions
D1Task.getAppInstanceID(context)    // include in support tickets
task.bindingHash                    // client binding payload
D1Task.reset(context)               // clears local D1 state
```
