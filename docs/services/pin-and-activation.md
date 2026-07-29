---
title: PIN and activation
layout: default
parent: Services
nav_order: 4
---

# PIN and card activation
{: .no_toc }

PIN display, PIN change, and physical card activation.
{: .fs-6 .fw-300 }

1. TOC
{:toc}

---

## Overview

These services apply to physical cards. D1 does not store PINs; it transciphers
PIN blocks between issuer keys and device keys, and the PIN is never persisted on
the device.

All three features depend on backend endpoints that D1 calls outbound. See
[Prerequisites](../prerequisites.html) for the endpoint list and supported PIN
block formats.

## Displaying a PIN

```kotlin
suspend fun displayPin(cardId: String, ui: CardPINUI) = session.withSession {
    awaitVoid { task.displayPhysicalCardPIN(cardId, ui, it) }
}
```

`CardPINUI` wraps a `PINDisplayTextView`:

```kotlin
val ui = CardPINUI(pinDisplayTextView)
```

`PINDisplayTextView` extends `TextView`, so font, size, and colour are styled
using standard view attributes. The PIN is displayed for a limited time in a
secure view, and the exchange is encrypted end to end.

Call `wipe()` on the `CardPINUI` when the screen is dismissed.

## Changing a PIN

The SDK captures PIN entry in two `SecureEditText` fields. The application never
receives the digits, and match state is delivered only through the event
listener.

```kotlin
fun beginChangePin(
    cardId: String,
    entry: SecureEditText,
    confirm: SecureEditText,
    pinLength: Int,
    listener: PINEntryUI.PINEventListener,
): PINEntryUI = task.changePIN(
    cardId,
    entry,
    confirm,
    ChangePINOptions(pinLength),
    listener,
)
```

`ChangePINOptions` carries the expected PIN length, readable through
`getPinLength()`.

### Handling entry events

| Event | Meaning | Action |
|:---|:---|:---|
| `FIRST_ENTRY_FINISH` | First field complete | Move focus to the confirmation field |
| `PIN_MATCH` | Both entries match | Enable submission |
| `PIN_MISMATCH` | Entries differ | Disable submission and show an error |

Drive the submit control from these events:

```kotlin
val canSubmit: Boolean get() = event == PINEntryUI.PINEvent.PIN_MATCH
```

### Submitting

Change PIN enforces a shorter session window than any other API. Refresh the
session immediately before submitting rather than relying on renewal after a
failure:

```kotlin
suspend fun submitChangePin(pinEntryUI: PINEntryUI) {
    session.login()
    awaitVoid { pinEntryUI.submit(it) }
}
```

Call `wipe()` on the `PINEntryUI` when the screen is dismissed.

For Seccos-format PIN blocks, D1 also calls the PIN change counter endpoint on
your backend.

## Activating a physical card

The card product determines the activation challenge. Retrieve it before
presenting the entry field so it can be labelled correctly:

```kotlin
suspend fun activationMethod(cardId: String): CardActivationMethod = session.withSession {
    awaitCallback { task.getCardActivationMethod(cardId, it) }
}
```

| Value | Challenge |
|:---|:---|
| `CVV` | Card verification value |
| `LAST4` | Last four PAN digits |
| `NOTHING` | No challenge required |

Capture the challenge in a `SecureEditText` wrapped by an `EntryUI`:

```kotlin
val entryUI = EntryUI(secureEditText)

suspend fun activatePhysicalCard(cardId: String, entryUI: EntryUI) = session.withSession {
    awaitVoid { task.activatePhysicalCard(cardId, entryUI, it) }
}
```

D1 validates the challenge against the card's registration data and forwards the
activation request to your backend.

## Secure view lifecycle

`PINEntryUI`, `CardPINUI`, and `CardDetailsUI` all expose `wipe()`. Call it when
the hosting screen is destroyed. In Compose, use `DisposableEffect`; see
[UI integration](../ui-integration.html).
