---
title: Card management
layout: default
parent: Services
nav_order: 3
---

# Card management
{: .no_toc }

Digital cards, transaction controls, spending limits, and history.
{: .fs-6 .fw-300 }

1. TOC
{:toc}

---

## Digital cards

List every token issued against a card, across all wallets and token requestors:

```kotlin
suspend fun list(cardId: String): List<DigitalCard> = session.withSession {
    awaitCallback { task.getDigitalCardList(cardId, it) }
}
```

| Property | Description |
|:---|:---|
| `cardID` | Digital card identifier |
| `state` | Current state |
| `scheme` | Payment scheme |
| `last4` | Last four digits |
| `expiryDate` | Token expiry |
| `deviceName`, `deviceType`, `deviceID` | Device the token resides on |
| `tokenRequestorID`, `tokenRequestorName` | Token requestor |
| `isOnCurrentDevice` | Whether the token is on this device |
| `deviceBindingList` | Device bindings, Visa CTF |

### Lifecycle actions

```kotlin
enum class DigitalCardUpdate { Applied, InvalidTransition }

suspend fun update(
    cardId: String,
    digitalCard: DigitalCard,
    action: CardAction,
): DigitalCardUpdate = session.withSession {
    val applied: Boolean = awaitCallback {
        task.updateDigitalCard(cardId, digitalCard, action, it)
    }
    if (applied) DigitalCardUpdate.Applied else DigitalCardUpdate.InvalidTransition
}
```

`CardAction` values are `SUSPEND`, `RESUME`, and `DELETE`.

{: .important }
> The callback delivers `onSuccess(false)` when the requested transition is not
> valid for the card's current state — for example, suspending an already
> suspended card. This is a successful call with a negative result, not an
> error. Handle it separately from `onError` so the UI does not report success.

Activation after step-up authentication uses `d1PushWallet.activateDigitalCard`.
See [Push Provisioning](push-provisioning.html).

### Device binding

Visa Card Token Framework bindings are managed through `digitalCardService`:

```kotlin
suspend fun approveBinding(digitalCardId: String, bindingReference: String, reason: BindReason)
suspend fun unbindDevice(digitalCardId: String, bindingReference: String, reason: UnbindReason)
suspend fun approveCardholderVerification(digitalCardId: String, reason: VerificationReason)
```

Each binding exposes `bindingReference`, `deviceName`, and `bindingStatus`
(`APPROVED`, `DECLINED`, `CHALLENGED`).

## Card settings

Retrieve settings before changing them:

```kotlin
suspend fun settings(cardId: String): CardSettings = session.withSession {
    awaitCallback { task.cardService.getCardSettings(cardId, it) }
}
```

`CardSettings` contains `control` (domain controls) and `limit` (spending
limits).

{: .warning }
> Always start from the object returned by `getCardSettings()`. A
> `CardSettings` constructed in application code overwrites server-side values
> and is rejected with `ERROR_CARD_SETTINGS_OPERATION_NOT_ALLOWED`,
> `ERROR_CARD_SETTINGS_INVALID_FORMAT`, or `ERROR_CARD_SETTINGS_INVALID_VALUE`.

Cache the returned object and modify it in place:

```kotlin
private var cached: CardSettings? = null

fun setControl(cardId: String, mutate: (CardSettings) -> Unit) = guard {
    val settings = cached ?: error("Call getCardSettings() before updating")
    mutate(settings)
    client.cardControl.updateControls(cardId, settings.control)
}
```

## Domain controls

Controls that the card product does not offer are returned as `null`. A `null`
value is not an error; it means the control does not exist for that card. Omit
the corresponding UI rather than rendering it disabled.

```kotlin
@Composable
fun OptionalSwitchRow(
    label: String,
    checked: Boolean?,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    if (checked == null) return
    Row(modifier) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
```

| Control | Availability |
|:---|:---|
| `setOnlinePaymentEnabled` | Always |
| `setAbroadPaymentEnabled` | Always |
| `setDeniedCurrencyList` | Always |
| `Merchant.setGamblingMerchantEnabled` | Always |
| `Merchant.setAdultMerchantEnabled` | Always |
| `Merchant.setRiskyMerchantEnabled` | Always |
| `setContactlessEnabled` | Only when `isContactlessEnabled` is non-null |
| `setMagneticStripeEnabled` | Only when `isMagneticStripeEnabled` is non-null |
| `setATMWithdrawalEnabled` | Only when `isATMWithdrawalEnabled` is non-null |

Apply changes and persist them:

```kotlin
suspend fun updateControls(cardId: String, controls: CardControlSettings) =
    session.withSession {
        awaitVoid { task.cardService.updateCardControlSettings(cardId, controls, it) }
    }
```

{: .important }
> From Kotlin, call the setter methods explicitly —
> `controls.setContactlessEnabled(value)` — rather than using property
> assignment. Several of these accessors do not resolve to assignable Kotlin
> properties.

`CardControlSettings` also exposes:

| Member | Description |
|:---|:---|
| `deniedCurrencyList` | ISO 4217 alpha currency codes |
| `geography.regionList` | `Region` values |
| `geography.countryList` | ISO 3166-1 alpha-2 country codes |
| `merchant` | Gambling, adult, and risky merchant flags |
| `restore()` | Reverts local modifications |

Use `Locale.getISOCountries()` and `android.icu.util.Currency` to produce valid
codes. Invalid formats are rejected with `ERROR_CARD_SETTINGS_INVALID_FORMAT`.

Require additional user authentication before applying control changes.

## Spending limits

Three limit types exist:

| Type | Modifiable by |
|:---|:---|
| Card limits | End user through the SDK, and issuer through the D1 API |
| Max limits | Issuer only. Caps card limits. |
| Security limits | Not exposed in the API or SDK |

Limits apply to two categories, **purchases** and **withdrawals**, across daily,
weekly, monthly, and yearly periods. Weekly, monthly, and yearly periods may be
fixed or rolling.

D1 maintains a cumulative current amount per category and period, readable
through the SDK. Exceeding a card limit declines the authorization with reason
`VELOCITY_CHECK_FAIL`.

```kotlin
suspend fun updateLimits(cardId: String, limits: CardLimitSettings) = session.withSession {
    awaitVoid { task.cardService.updateCardLimitSettings(cardId, limits, it) }
}
```

`CardLimitSettings` is constructed with a currency and exposes daily, weekly,
monthly, and yearly purchase and withdrawal limits.

{: .important }
> Limits are inherited from the card product when a card is created or
> registered. Existing limits can be updated, but limits cannot be added or
> removed afterwards.

## Card lifecycle

```kotlin
suspend fun suspendCard(cardId: String)
suspend fun resumeCard(cardId: String)
suspend fun replaceCard(cardId: String, reason: StateReason): String
```

`replaceCard` returns the identifier of the replacement card.
`StateReason.getReplaceReasons()` returns the subset of reasons valid for
replacement, including `CARD_LOST`, `CARD_STOLEN`, `CARD_BROKEN`,
`CARD_NOT_RECEIVED`, and `FRAUD`.

## Transaction history

```kotlin
suspend fun transactions(
    cardId: String,
    query: TransactionQuery = TransactionQuery(),
): List<CardTransactionRecord> = session.withSession {
    awaitCallback { task.cardService.getTransactionHistory(cardId, query, it) }
}
```

`TransactionQuery` has a no-argument constructor and no filter options in SDK
4.4.0.

History is limited to **50 transactions over the last 30 days**. Design the UI so
these limits are evident rather than appearing as missing data.

`CardTransactionRecord` exposes:

| Property | Description |
|:---|:---|
| `id` | Transaction identifier |
| `transactionType` | Type |
| `transactionDate` | Date |
| `transactionStatus` | Status |
| `declinedReason`, `declinedDetail` | Decline information |
| `amount`, `billingAmount`, `conversionRate` | Amounts |
| `merchant` | Merchant details |
| `accountNumber` | Associated account |
| `digitalCardID`, `tokenRequestorID` | Token that was used |
