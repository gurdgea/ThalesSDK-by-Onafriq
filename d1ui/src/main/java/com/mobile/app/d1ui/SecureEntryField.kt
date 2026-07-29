package com.mobile.app.d1ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.thalesgroup.gemalto.d1.CardPINUI
import com.thalesgroup.gemalto.d1.EntryUI
import com.thalesgroup.gemalto.d1.PINDisplayTextView
import com.thalesgroup.gemalto.d1.SecureEditText

class CardActivationEntryState internal constructor(val field: SecureEditText) {
    val entryUI: EntryUI = EntryUI(field)
}

/** Captures the activation challenge — CVV or last 4 PAN digits, per card product. */
@Composable
fun rememberCardActivationEntry(): CardActivationEntryState {
    val context = LocalContext.current
    return remember(context) { CardActivationEntryState(SecureEditText(context)) }
}

@Composable
fun CardActivationField(state: CardActivationEntryState, modifier: Modifier = Modifier) {
    AndroidView(factory = { state.field }, modifier = modifier)
}

class CardPinDisplayState internal constructor(val view: PINDisplayTextView) {
    val ui: CardPINUI = CardPINUI(view)
}

@Composable
fun rememberCardPinDisplay(): CardPinDisplayState {
    val context = LocalContext.current
    return remember(context) { CardPinDisplayState(PINDisplayTextView(context)) }
}

/** Shows a physical card's PIN in an SDK-owned view for a limited time. */
@Composable
fun CardPinDisplay(state: CardPinDisplayState, modifier: Modifier = Modifier) {
    AndroidView(factory = { state.view }, modifier = modifier)
}
