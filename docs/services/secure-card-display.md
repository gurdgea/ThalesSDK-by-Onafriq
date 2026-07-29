---
title: Secure Card Display
layout: default
parent: Services
nav_order: 1
---

# Secure Card Display
{: .no_toc }

Displaying card data without exposing it to the application process.
{: .fs-6 .fw-300 }

1. TOC
{:toc}

---

## Card metadata

Metadata is non-sensitive and safe to hold in application state.

```kotlin
suspend fun metadata(cardId: String): CardMetadata = session.withSession {
    task.secureCardDisplayService.getCardMetadata(cardId).awaitResult()
}
```

| Property | Type | Notes |
|:---|:---|:---|
| `last4Pan` | String | Last four PAN digits |
| `scheme` | `Scheme` | Card scheme |
| `expiryDate` | String | Expiry date |
| `state` | `State` | Card state |
| `stateReason` | `StateReason` | D1-issued cards only |
| `ongoingOperation` | `OngoingOperation` | D1-issued cards only |

Enumerate a consumer's cards with `cardService.getCardList()`.

### Card artwork

```kotlin
suspend fun assets(metadata: CardMetadata): List<CardAsset> = session.withSession {
    metadata.getAssetList().awaitResult()
}
```

The first call retrieves assets from the D1 backend and caches them locally, so
subsequent calls are inexpensive and can be made on every render.

`getAssetList` is overloaded with both a `Task` and a callback form. Call it as a
method rather than as a property.

## Card details

Card details are sensitive. Two approaches are available.

### Option 1: the application renders

The SDK returns the values and your code renders them.

```kotlin
suspend fun <R> withCardDetails(cardId: String, block: (CardDetails) -> R): R =
    session.withSession {
        val details = task.secureCardDisplayService.getCardDetails(cardId).awaitResult()
        try {
            block(details)
        } finally {
            details.wipe()
        }
    }
```

`CardDetails` exposes `pan`, `expiryDate`, `cvv`, and `cardHolderName`, all as
`ByteArray`, plus `wipe()`.

A scoped accessor guarantees `wipe()` runs, including when the caller throws.

Values are byte arrays so they can be cleared. Converting one to a `String`
creates an immutable copy that cannot be wiped, so convert as late as possible
and keep the result as narrow as possible:

```kotlin
withCardDetails(cardId) { details ->
    val last4 = details.pan.takeLast(4).toByteArray().toString(Charsets.UTF_8)
    val expiry = details.expiryDate.toString(Charsets.UTF_8)
    "•••• $last4  exp $expiry"
}
```

{: .warning }
> Card data must never reach logging, analytics, or crash reporting. Audit
> breadcrumb and log configuration before release.

### Option 2: the SDK renders

The SDK writes directly into views you supply and returns no card data.
Preferred where supported.

```kotlin
suspend fun displayCardDetails(cardId: String, ui: CardDetailsUI) = session.withSession {
    task.secureCardDisplayService.displayCardDetails(cardId, ui).awaitVoidResult()
}
```

Build the `CardDetailsUI` from four `DisplayTextView` instances:

```kotlin
val ui = CardDetailsUI.getInstance(panView, expiryView, cvvView, holderView).apply {
    setPanSeparatorCharacter(" ")
    setPanMaskCharacter("*")
    setExpiryDateFormat("MM/yy")
}
```

| Method | Default | Purpose |
|:---|:---|:---|
| `setPanSeparatorCharacter` | Space | Separator inserted every four digits |
| `setPanMaskCharacter` | `*` | Character used when masked |
| `setExpiryDateFormat` | `MM/YY` | Expiry date format |
| `maskCardDetails` | — | Masks the displayed values |
| `showCardDetails` | — | Retrieves and displays the values |
| `wipe` | — | Clears the views |

{: .important }
> Use `com.thalesgroup.gemalto.d1.securecarddisplay.DisplayTextView`. The SDK
> also declares `com.thalesgroup.gemalto.d1.DisplayTextView`, which is a
> different type and is not accepted by `CardDetailsUI.getInstance`.

For hosting these views in Compose, see [UI integration](../ui-integration.html).

**Scheme support.** Option 2 supports Visa and Mastercard. Amex differs in PAN
length and rendering; use Option 1 for Amex cards.

### Masking and revealing

```kotlin
fun mask(ui: CardDetailsUI) = ui.maskCardDetails()

suspend fun reveal(ui: CardDetailsUI) = session.withSession {
    ui.showCardDetails().awaitVoidResult()
}
```

`showCardDetails()` retrieves values from the server on every call and never
re-displays a cached value. Each retrieval also generates a new dynamic CVV2.

Apply a timer of roughly one minute that calls `maskCardDetails()`
automatically.

## Dynamic CVV2

Dynamic CVV2 is a single-use CVV that hardens card-not-present transactions. A
new value is generated whenever card credentials are displayed, so displaying
card details through Secure Card Display requires no additional SDK calls.

A dynamic CVV2 can also be generated server-side through the `getCardCredentials`
D1 API.

A card has at most one active dynamic CVV2; generating a new one deletes the
previous value. A value is active while all of the following hold:

- It has been generated
- It has not expired
- It has not been used in an authorization
- The declined-authorization count has not reached the configured maximum

Two settings are configured per card product at onboarding:

| Setting | Description |
|:---|:---|
| Dynamic CVV expiry delay | Period before D1 deletes the active value |
| Number of tries | Declined authorizations before deletion. Default `1`. |

Because generation is automatic, the client-side work is in the user experience:
communicate clearly that the code is single-use and time-limited.

## Obfuscation

If you subclass `DisplayTextView` and use ProGuard or R8 rather than DexGuard,
an additional keep rule is required. See [Deployment](../deployment.html).
