---
title: Push Provisioning
layout: default
parent: Services
nav_order: 2
---

# Push Provisioning
{: .no_toc }

Adding cards to digital wallets and token requestors.
{: .fs-6 .fw-300 }

1. TOC
{:toc}

---

## Overview

Push provisioning has three stages:

1. Read the card's digitization state to decide what UI to show.
2. Push the card to the wallet.
3. Handle the wallet's activity result.

Wallet-side approvals must be complete before any of this works on a device. See
[Prerequisites](../prerequisites.html).

## Digitization state

Read the state on app start and whenever a card detail screen opens.

| State | UI |
|:---|:---|
| `NOT_DIGITIZED` | Show *Add to Google Pay* / *Add to Samsung Pay* |
| `PENDING_IDV` | Show *Activate card* |
| `DIGITIZED` | Hide provisioning actions |

Query by last four PAN digits where possible. This form resolves on the device
with no network call:

```kotlin
suspend fun digitizationStateByLast4(
    wallet: OEMPayType,
    last4: String,
): CardDigitizationState = session.withSession {
    task.pushProvisioningService.getCardDigitizationState(wallet, last4).awaitResult()
}
```

Query by card identifier when the last four digits are unavailable:

```kotlin
suspend fun digitizationStateByCardId(
    cardId: String,
    wallet: OEMPayType,
): CardDigitizationState = session.withSession {
    task.pushProvisioningService.getCardDigitizationState(cardId, wallet).awaitResult()
}
```

{: .important }
> The two overloads take arguments in different orders: `(wallet, last4)` and
> `(cardId, wallet)`.

Use `com.thalesgroup.gemalto.d1.pushprovisioning.CardDigitizationState`. A
same-named type exists in `com.thalesgroup.gemalto.d1.card` for the legacy
`d1PushWallet` API.

## Adding a card to a wallet

Call this only when the state is `NOT_DIGITIZED`:

```kotlin
suspend fun addToWallet(
    cardId: String,
    wallet: OEMPayType,
    activity: Activity,
    requestCode: Int,
): Any = session.withSession {
    task.pushProvisioningService
        .addDigitalCardToOEM(cardId, wallet, activity, requestCode)
        .awaitResult()
}
```

D1 validates the user and card data, builds the wallet and TSP payload, invokes
the wallet application, and returns the result.

Forward the wallet's result to the SDK:

```kotlin
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    task.handleCardResult(requestCode, resultCode, data)
}
```

### Platform constraints

- Google Wallet requires Android 9 or later.
- Google Pay is unavailable on emulators and on devices without Google Play
  services. Test provisioning on physical hardware.
- Mastercard limits `cardHolderName` to 27 characters. Longer values fail
  eligibility checks.
- The device's wallet environment must match the D1 environment. A Sandbox app
  against a production wallet returns `CheckEligibility error ... code: 14`.

## Token requestors

For non-wallet token requestors such as e-commerce providers and Click to Pay,
list eligible requestors for the card:

```kotlin
suspend fun tokenRequestors(cardId: String): List<TokenRequestor> = session.withSession {
    task.pushProvisioningService.getTokenRequestorList(cardId).awaitResult()
}
```

`TokenRequestor` exposes `id`, `name`, `assets`, and `provisioningMethods`.

Push the card and launch the returned URL:

```kotlin
suspend fun addToScheme(
    cardId: String,
    tokenRequestor: TokenRequestor,
    callbackUrl: String,
    termsAccepted: Boolean,
): String = session.withSession {
    task.pushProvisioningService
        .addDigitalCardToScheme(cardId, tokenRequestor, callbackUrl, termsAccepted)
        .awaitResult()
}
```

```kotlin
context.startActivity(Intent(Intent.ACTION_VIEW, deepLink.toUri()))
```

`callbackUrl` is your application's custom scheme. Register it in the manifest so
the token requestor can return control to your app. See
[Installation](../installation.html).

The Mastercard Click to Pay token requestor identifier is `50123197928`.

{: .important }
> Token requestor names and logos cannot be retrieved reliably at runtime. Map
> the token requestor identifiers relevant to your portfolio to display names and
> artwork within the app.

## In-app identification and verification

When tokenization requires step-up authentication and the user chooses to verify
in your app, the wallet launches your activity with a payload.

Validate the calling package before processing:

```kotlin
if ("com.google.android.gms" == callingPackage ||
    "com.samsung.android.spay" == callingPackage) {
    // proceed
} else {
    // abort
}
```

The payload is base64-encoded JSON in `Intent.EXTRA_TEXT`. Field names differ by
scheme:

| Scheme | Digital card identifier | PAN last four |
|:---|:---|:---|
| Visa | `tokenReferenceID` | `panLast4` |
| Mastercard | `tokenUniqueReference` | `accountPanSuffix` |

Branch on the presence of `tokenReferenceID`. Visa payloads also carry
`tokenRequestorID`: `40010075001` for Google Pay and `40010043095` for Samsung
Pay.

Display the PAN last four so the user can confirm which card they are approving,
then activate:

```kotlin
suspend fun activate(digitalCardId: String) = session.withSession {
    awaitVoid { task.d1PushWallet.activateDigitalCard(digitalCardId, it) }
}
```

Return a result to the wallet in every case, including failure:

```kotlin
val resultIntent = Intent().putExtra(
    "BANKING_APP_ACTIVATION_RESPONSE",
    if (exception == null) "approved" else "failure",
)
activity.setResult(Activity.RESULT_OK, resultIntent)
activity.finish()
```

{: .warning }
> A wallet that receives no result waits indefinitely. Always call `setResult`
> and `finish`.

Parsing the payload requires a JSON library. The Thales sample application uses
`jackson-core` and `jackson-databind`.

## Samsung Pay availability

Samsung Pay reports availability problems as configuration errors. Handle them
through `pushProvisioningService`:

```kotlin
fun activateSamsungPay()   // throws D1Exception
fun updateSamsungPay()     // throws D1Exception
```

See [Initialization](../initialization.html) for the mapping from configuration
error codes to these calls.

## Diagnosing state mismatches

A card that reports `NOT_DIGITIZED` after a successful digitization usually
indicates TSP portal configuration rather than an application defect. Verify the
package name and issuer name registered with the TSP.
