package com.mobile.app.d1ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.mobile.app.d1core.service.SecureCardDisplay
import com.thalesgroup.gemalto.d1.securecarddisplay.CardDetailsUI
import com.thalesgroup.gemalto.d1.securecarddisplay.DisplayTextView
import kotlinx.coroutines.delay

const val DEFAULT_AUTO_MASK_MILLIS = 60_000L

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
            pan = pan,
            expiry = expiry,
            cvv = cvv,
            cardHolderName = holder,
        )
    }

    if (textColor != Color.Unspecified) {
        val argb = textColor.toArgb()
        remember(state, argb) { state.views.forEach { it.setTextColor(argb) }; argb }
    }

    DisposableEffect(state) {
        onDispose { state.ui.wipe() }
    }

    return state
}

/**
 * Renders card details by handing the SDK its own views. PAN, expiry, CVV and
 * cardholder name are written straight into them and never enter Compose state,
 * so this process holds no copy to leak, log, or fail to wipe.
 *
 * Visa and Mastercard only. Amex PAN length and rendering differ and are not
 * supported here — use [SecureCardDisplay.withCardDetails] for those.
 */
@Composable
fun SecureCardDetails(
    display: SecureCardDisplay,
    cardId: String,
    state: SecureCardDetailsState = rememberSecureCardDetailsState(),
    autoMaskAfterMillis: Long = DEFAULT_AUTO_MASK_MILLIS,
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

/** Hosts one SDK-owned text view. */
@Composable
fun SecureCardField(view: DisplayTextView, modifier: Modifier = Modifier) {
    AndroidView(factory = { view }, modifier = modifier)
}

/** Refetches from the server; it never re-renders a cached value. */
suspend fun SecureCardDetailsState.reveal(display: SecureCardDisplay) {
    display.reveal(ui)
    isRevealed = true
}

fun SecureCardDetailsState.mask(display: SecureCardDisplay) {
    display.mask(ui)
    isRevealed = false
}
