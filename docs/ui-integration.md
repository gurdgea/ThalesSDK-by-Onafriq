---
title: UI integration
layout: default
nav_order: 8
---

# UI integration
{: .no_toc }

Hosting the SDK's secure views, including in Jetpack Compose.
{: .fs-6 .fw-300 }

1. TOC
{:toc}

---

## Secure view types

Several D1 APIs operate on `View` instances that the SDK writes to or reads from
directly. The value never enters application state, which is the security
property these types provide.

| Type | Base class | Used by |
|:---|:---|:---|
| `securecarddisplay.DisplayTextView` | `TextView` | `CardDetailsUI` |
| `PINDisplayTextView` | `TextView` | `CardPINUI` |
| `SecureEditText` | `EditText` | `changePIN`, `EntryUI` |

Because the SDK owns the value, there is no Compose-native equivalent. Host these
views with `AndroidView` and keep card and PIN data out of Compose state.

## Module structure

Keep the SDK wrapper free of UI dependencies by placing Compose bridges in their
own module:

```
:app  ──►  :d1ui  ──►  :d1core
```

`:d1core` exposes suspending service wrappers. `:d1ui` provides composables that
own the view instances and their lifecycle.

## Card details

Create the four views once and build the `CardDetailsUI` from them. State holds
only whether the details are currently revealed.

```kotlin
class SecureCardDetailsState internal constructor(
    val ui: CardDetailsUI,
    val pan: DisplayTextView,
    val expiry: DisplayTextView,
    val cvv: DisplayTextView,
    val cardHolderName: DisplayTextView,
) {
    var isRevealed by mutableStateOf(false)
        internal set

    internal val views = listOf(pan, expiry, cvv, cardHolderName)
}

@Composable
fun rememberSecureCardDetailsState(
    textColor: Color = Color.Unspecified,
    panSeparator: String = " ",
    panMask: String = "*",
    expiryFormat: String = "MM/yy",
): SecureCardDetailsState {
    val context = LocalContext.current
    val state = remember(context) {
        val pan = DisplayTextView(context)
        val expiry = DisplayTextView(context)
        val cvv = DisplayTextView(context)
        val holder = DisplayTextView(context)
        SecureCardDetailsState(
            ui = CardDetailsUI.getInstance(pan, expiry, cvv, holder).apply {
                setPanSeparatorCharacter(panSeparator)
                setPanMaskCharacter(panMask)
                setExpiryDateFormat(expiryFormat)
            },
            pan = pan, expiry = expiry, cvv = cvv, cardHolderName = holder,
        )
    }

    DisposableEffect(state) {
        onDispose { state.ui.wipe() }
    }

    return state
}
```

Import `com.thalesgroup.gemalto.d1.securecarddisplay.DisplayTextView`.

Fetch on composition and mask automatically after a timeout:

```kotlin
@Composable
fun SecureCardDetails(
    display: SecureCardDisplay,
    cardId: String,
    state: SecureCardDetailsState = rememberSecureCardDetailsState(),
    autoMaskAfterMillis: Long = 60_000L,
    onError: (Throwable) -> Unit = {},
    content: @Composable (SecureCardDetailsState) -> Unit,
) {
    LaunchedEffect(cardId, state) {
        runCatching { display.displayCardDetails(cardId, state.ui) }
            .onSuccess { state.isRevealed = true }
            .onFailure(onError)
    }

    LaunchedEffect(state.isRevealed, autoMaskAfterMillis) {
        if (!state.isRevealed || autoMaskAfterMillis <= 0L) return@LaunchedEffect
        delay(autoMaskAfterMillis)
        display.mask(state.ui)
        state.isRevealed = false
    }

    content(state)
}

@Composable
fun SecureCardField(view: DisplayTextView, modifier: Modifier = Modifier) {
    AndroidView(factory = { view }, modifier = modifier)
}
```

Usage:

```kotlin
SecureCardDetails(display = client.secureCardDisplay, cardId = cardId) { state ->
    SecureCardField(state.pan)
    SecureCardField(state.expiry)
    SecureCardField(state.cvv)
    SecureCardField(state.cardHolderName)

    Button(onClick = { scope.launch { state.reveal(client.secureCardDisplay) } }) {
        Text(if (state.isRevealed) "Refresh" else "Reveal")
    }
}
```

{: .important }
> Create the view outside the `AndroidView` factory and return the existing
> instance. Constructing it inside the factory produces a new view on
> recomposition, leaving `CardDetailsUI` holding a detached reference.

## PIN entry

Two `SecureEditText` fields, with match state hoisted from the SDK's listener:

```kotlin
class SecurePinEntryState internal constructor(
    val entry: SecureEditText,
    val confirm: SecureEditText,
) {
    var event by mutableStateOf<PINEntryUI.PINEvent?>(null)
        internal set

    internal var pinEntryUI: PINEntryUI? = null

    val canSubmit: Boolean get() = event == PINEntryUI.PINEvent.PIN_MATCH

    suspend fun submit(pin: PinManagement) {
        val ui = pinEntryUI ?: error("PIN entry has not started")
        pin.submitChangePin(ui)
    }
}

@Composable
fun SecurePinEntry(
    pin: PinManagement,
    cardId: String,
    pinLength: Int,
    content: @Composable (SecurePinEntryState) -> Unit,
) {
    val context = LocalContext.current
    val state = remember(context) {
        SecurePinEntryState(SecureEditText(context), SecureEditText(context))
    }

    DisposableEffect(cardId, pinLength) {
        state.pinEntryUI = pin.beginChangePin(
            cardId = cardId,
            entry = state.entry,
            confirm = state.confirm,
            pinLength = pinLength,
        ) { event, _ -> state.event = event }

        onDispose {
            state.pinEntryUI?.wipe()
            state.pinEntryUI = null
            state.event = null
        }
    }

    content(state)
}

@Composable
fun SecurePinField(field: SecureEditText, modifier: Modifier = Modifier) {
    AndroidView(factory = { field }, modifier = modifier)
}
```

`changePIN` is not a suspending call and requires teardown, so start it in
`DisposableEffect` and wipe in `onDispose`.

## Card activation and PIN display

```kotlin
class CardActivationEntryState internal constructor(val field: SecureEditText) {
    val entryUI: EntryUI = EntryUI(field)
}

@Composable
fun rememberCardActivationEntry(): CardActivationEntryState {
    val context = LocalContext.current
    return remember(context) { CardActivationEntryState(SecureEditText(context)) }
}

class CardPinDisplayState internal constructor(val view: PINDisplayTextView) {
    val ui: CardPINUI = CardPINUI(view)
}

@Composable
fun rememberCardPinDisplay(): CardPinDisplayState {
    val context = LocalContext.current
    return remember(context) { CardPinDisplayState(PINDisplayTextView(context)) }
}
```

## Styling

These are platform views, so Compose theming does not apply. Set attributes
directly:

```kotlin
if (textColor != Color.Unspecified) {
    val argb = textColor.toArgb()
    remember(state, argb) { state.views.forEach { it.setTextColor(argb) }; argb }
}
```

`DisplayTextView` and `PINDisplayTextView` accept any `TextView` attribute;
`SecureEditText` accepts any `EditText` attribute.

## Guidelines

- Do not place SDK-rendered values in Compose state.
- Call `wipe()` from `onDispose` for `CardDetailsUI`, `PINEntryUI`, and
  `CardPINUI`.
- Hoist only booleans and events, such as reveal state and `PINEvent`.
- Drive submit controls from SDK events rather than from field observation.
- Avoid placing secure views inside `LazyColumn` items, which recreate views
  during scrolling.
