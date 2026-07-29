---
title: Initialization
layout: default
nav_order: 5
---

# Initialization
{: .no_toc }

Creating and configuring `D1Task`.
{: .fs-6 .fw-300 }

1. TOC
{:toc}

---

## Overview

`D1Task` is the SDK's single entry point. Initialization has two stages:

1. **Build** the task with onboarding parameters.
2. **Configure** it with the feature set your app uses.

Initialize as early as possible — in `Application.onCreate()` or on first
activity launch. No D1 service is available until `configure()` completes and
[login](authentication.html) succeeds.

## Building the task

```kotlin
val task = D1Task.Builder()
    .setContext(context.applicationContext)
    .setD1ServiceURL(config.serviceUrl)
    .setIssuerID(config.issuerId)
    .setDigitalCardURL(config.digitalCardUrl)
    .setD1ServiceRSAModulus(config.rsaModulus)
    .setD1ServiceRSAExponent(config.rsaExponent)
    .apply {
        config.applicationProfileId?.let { setApplicationProfileId(it) }
        if (!config.secureLogEnabled) disableLogService()
    }
    .build()
```

| Builder method | Purpose |
|:---|:---|
| `setContext` | Application context |
| `setD1ServiceURL` | D1 Service Server URL |
| `setIssuerID` | Issuer identifier |
| `setDigitalCardURL` | Digital card operations URL |
| `setD1ServiceRSAModulus` | RSA modulus as `ByteArray` |
| `setD1ServiceRSAExponent` | RSA exponent as `ByteArray` |
| `setApplicationProfileId` | Optional application profile |
| `disableLogService` | Disables the SDK log service |

Logging is enabled by default; call `disableLogService()` to turn it off.

## Configuration parameters

Select the parameters that match the services you use:

| Services | Required parameters |
|:---|:---|
| Secure Card Display, physical cards | `buildConfigCore(consumerId)` |
| Tokenization, push provisioning | `buildConfigCore(consumerId)` and `buildConfigCard(...)` |

```kotlin
val coreConfig = ConfigParams.buildConfigCore(config.consumerId)

val cardConfig = ConfigParams.buildConfigCard(
    OEMPayType.GOOGLE_PAY,
    null,                      // serviceId: Samsung Pay only
    config.visaClientAppId,    // Visa only; defaults to issuerID when null
)
```

`buildConfigCard` has two forms:

```java
buildConfigCard(OEMPayType, String serviceId, String visaClientAppId)
buildConfigCard(Activity, OEMPayType, String serviceId, String visaClientAppId)
```

Prefer the three-argument form. The `Activity` argument has been unnecessary
since SDK 3.2.0; pass the activity to `addDigitalCardToOEM()` instead.

Configure one `D1Params` per wallet you support:

```kotlin
val gpayConfig = ConfigParams.buildConfigCard(OEMPayType.GOOGLE_PAY, null, visaClientAppId)
val spayConfig = ConfigParams.buildConfigCard(OEMPayType.SAMSUNG_PAY, serviceId, visaClientAppId)
```

## Configuring

`configure()` accepts a callback and a variable number of parameter objects:

```kotlin
suspend fun configure(): List<D1Failure> {
    val params = buildList {
        add(ConfigParams.buildConfigCore(config.consumerId))
        add(ConfigParams.buildConfigCard(OEMPayType.GOOGLE_PAY, null, config.visaClientAppId))
        contributors.forEach { it.params(appContext)?.let(::add) }
    }

    return awaitConfigure { callback ->
        task.configure(callback, *params.toTypedArray<D1Params>())
    }.map(D1Exception::toFailure)
}
```

### Per-target results

`ConfigCallback.onError` delivers a `List<D1Exception>`. Each configured wallet
succeeds or fails independently, and a failure in one must not prevent the
others from working.

Bridge the callback so success and partial failure are both ordinary return
values:

```kotlin
internal suspend fun awaitConfigure(
    block: (D1Task.ConfigCallback<Void>) -> Unit,
): List<D1Exception> = suspendCancellableCoroutine { continuation ->
    block(object : D1Task.ConfigCallback<Void> {
        override fun onSuccess(result: Void?) {
            if (continuation.isActive) continuation.resume(emptyList())
        }

        override fun onError(exceptions: List<D1Exception>) {
            if (continuation.isActive) continuation.resume(exceptions)
        }
    })
}
```

An empty list means every target configured successfully. A non-empty list is a
set of per-target warnings to act on individually.

### Handling configuration results

Several results are recoverable in the app:

| Error code | Action |
|:---|:---|
| `ERROR_SPAY_NEED_TO_UPDATE` | Call `updateSamsungPay()` |
| `ERROR_SPAY_SETUP_NOT_COMPLETED` | Call `activateSamsungPay()` |
| `ERROR_SPAY_APP_NOT_FOUND` | Prompt the user to install Samsung Pay |
| `ERROR_SPAY_NOT_SUPPORTED` | Hide Samsung Pay UI |
| `ERROR_GPAY_NOT_SUPPORTED` | Hide Google Pay UI |
| `ERROR_D1PAY_UNRECOVERABLE` | Call `D1PayWallet.reset(context)` |
| `ERROR_INVALID_ARGUMENT` | Check the Samsung Service ID and portal registration |
| `ERROR_DEVICE_ENVIRONMENT_UNSAFE` | Block the session |

`ERROR_DEVICE_ENVIRONMENT_UNSAFE` disables every API for the process lifetime.
Record it and stop issuing calls:

```kotlin
suspend fun configure(): List<D1Failure> {
    blocked?.let { throw it }
    _state.value = D1SessionState.Configuring

    val failures = gateway.configure()

    failures.firstOrNull { it is D1Failure.DeviceUnsafe }?.let {
        latch(it)
        return failures
    }

    _state.value = D1SessionState.Configured(failures)
    return failures
}
```

See [Error handling](error-handling.html) for the full model.

## Activity results

Google Pay provisioning returns its result through the legacy activity result
callback. Forward it to the SDK:

```kotlin
@Deprecated("Required by the D1 SDK, which has no ActivityResult API equivalent")
@Suppress("DEPRECATION")
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    client?.pushProvisioning?.handleWalletResult(requestCode, resultCode, data)
}
```

{: .warning }
> Without this forwarding, push provisioning never completes. There is no
> callback, error, or timeout — the operation simply does not finish.

There is no `ActivityResultContract` equivalent, because the SDK launches the
wallet itself.

## Wallet state changes

Register a listener to keep the UI aligned with wallet-side changes such as the
active wallet switching, the selected card changing, or tokens being added or
removed:

```kotlin
override fun onResume() {
    super.onResume()
    client?.observeWalletChanges(walletChanges)
}

override fun onPause() {
    client?.stopObservingWalletChanges()
    super.onPause()
}
```

{: .important }
> These events are delivered only while the app is in the foreground. Also
> refresh token status on launch and on resume; the listener alone leaves stale
> state after any period in the background.

## Managing instances

Some integrations initialize D1 in more than one place, such as an NFC payment
service, producing multiple `D1Task` instances. Release earlier instances and
use the most recent.

If `consumerId` becomes available before an activity does, call `configure()`
with `buildConfigCore(consumerId)` alone, and configure card parameters later.

## Next

Continue to [Authentication](authentication.html).
