---
title: Services
layout: default
nav_order: 7
has_children: true
---

# Services
{: .no_toc }

Calling conventions shared by every D1 service.
{: .fs-6 .fw-300 }

1. TOC
{:toc}

---

## Prerequisites

Every service requires:

- The end user and card registered in D1
- `configure()` completed
- `login()` succeeded

## Service handles

SDK 4.4.0 exposes services as handles on `D1Task`:

| Handle | Services |
|:---|:---|
| `secureCardDisplayService` | Card metadata, card details, SDK-rendered display |
| `pushProvisioningService` | Digitization state, wallet provisioning, token requestors |
| `cardService` | Card list, settings, limits, transaction history, lifecycle |
| `digitalCardService` | Device binding, cardholder verification |
| `messagingService` | Message list, read state, notification registration |
| `clickToPayService` | Enrolment, profiles, opt-out |
| `d1PushWallet` | Digital card activation, Samsung Pay availability |

Some operations remain directly on `D1Task`, including `getDigitalCardList`,
`updateDigitalCard`, `displayPhysicalCardPIN`, `changePIN`, and
`activatePhysicalCard`.

## Calling conventions

The SDK exposes two shapes. Newer service methods return `Task<T>`; others accept
a callback:

```java
// Task-based
Task<CardMetadata> getCardMetadata(String cardId)

// Callback-based
void getCardMetadata(String cardId, D1Task.Callback<CardMetadata> callback)
```

`Task<T>` supports both asynchronous and blocking execution:

```java
void execute(D1Task.Callback<T> callback)
T execute() throws D1Exception
```

## Coroutine bridge

Bridge both shapes to suspending functions. `kotlinx-coroutines` is a required
dependency; it is not bundled with the SDK.

```kotlin
internal suspend fun <T : Any> awaitCallback(
    block: (D1Task.Callback<T>) -> Unit,
): T = suspendCancellableCoroutine { continuation ->
    block(object : D1Task.Callback<T> {
        override fun onSuccess(result: T?) {
            if (!continuation.isActive) return
            if (result == null) {
                continuation.resumeWithException(IllegalStateException("D1 returned no result"))
            } else {
                continuation.resume(result)
            }
        }

        override fun onError(exception: D1Exception) {
            if (continuation.isActive) continuation.resumeWithException(exception.toFailure())
        }
    })
}

internal suspend fun awaitVoid(
    block: (D1Task.Callback<Void>) -> Unit,
): Unit = suspendCancellableCoroutine { continuation ->
    block(object : D1Task.Callback<Void> {
        override fun onSuccess(result: Void?) {
            if (continuation.isActive) continuation.resume(Unit)
        }

        override fun onError(exception: D1Exception) {
            if (continuation.isActive) continuation.resumeWithException(exception.toFailure())
        }
    })
}

internal suspend fun <T : Any> Task<T>.awaitResult(): T = awaitCallback { execute(it) }

internal suspend fun Task<Void>.awaitVoidResult(): Unit = awaitVoid { execute(it) }
```

{: .important }
> Use a separate bridge for `Callback<Void>`. Void callbacks always deliver
> `null` on success, so a generic bridge that treats `null` as an error would
> reject every successful void operation.

## Structuring service wrappers

Wrap each service so session renewal is applied consistently:

```kotlin
class SecureCardDisplay internal constructor(
    private val session: D1Session,
    private val task: D1Task,
) {
    suspend fun metadata(cardId: String): CardMetadata = session.withSession {
        task.secureCardDisplayService.getCardMetadata(cardId).awaitResult()
    }
}
```

`withSession` handles session expiry and unsafe-device conditions. See
[Authentication](authentication.html).

Expose the wrappers through a single facade:

```kotlin
class D1Client {
    val secureCardDisplay: SecureCardDisplay
    val pushProvisioning: PushProvisioning
    val cardControl: CardControl
    val digitalCards: DigitalCards
    val pin: PinManagement
    val messaging: Messaging
    val clickToPay: ClickToPay
    val push: D1PushHandler
}
```

## In this section

| Page | Covers |
|:---|:---|
| [Secure Card Display](services/secure-card-display.html) | Card metadata, card details, dynamic CVV2 |
| [Push Provisioning](services/push-provisioning.html) | Digitization state, wallet provisioning, token requestors, in-app ID&V |
| [Card management](services/card-management.html) | Digital cards, transaction controls, limits, history |
| [PIN and activation](services/pin-and-activation.html) | PIN display, PIN change, physical card activation |
| [Messaging and push](services/messaging.html) | Push notifications, issuer messages, Click to Pay |
