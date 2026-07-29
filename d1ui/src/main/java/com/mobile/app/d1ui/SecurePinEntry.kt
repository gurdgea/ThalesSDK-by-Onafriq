package com.mobile.app.d1ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.mobile.app.d1core.service.PinManagement
import com.thalesgroup.gemalto.d1.PINEntryUI
import com.thalesgroup.gemalto.d1.SecureEditText

class SecurePinEntryState internal constructor(
    val entry: SecureEditText,
    val confirm: SecureEditText,
) {
    var event by mutableStateOf<PINEntryUI.PINEvent?>(null)
        internal set

    internal var pinEntryUI: PINEntryUI? = null

    /** Only true on PIN_MATCH — drive the submit button from this, nothing else. */
    val canSubmit: Boolean get() = event == PINEntryUI.PINEvent.PIN_MATCH

    suspend fun submit(pin: PinManagement) {
        val ui = pinEntryUI ?: error("PIN entry has not started")
        pin.submitChangePin(ui)
    }
}

/**
 * Captures a new PIN in the SDK's own secure fields. The digits never reach this
 * process: match state arrives only through the SDK's event listener, which is
 * why [SecurePinEntryState.canSubmit] is the sole gate on submission.
 */
@Composable
fun SecurePinEntry(
    pin: PinManagement,
    cardId: String,
    pinLength: Int,
    modifier: Modifier = Modifier,
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
