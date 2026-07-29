package com.mobile.app.d1core.service

import com.mobile.app.d1core.internal.awaitCallback
import com.mobile.app.d1core.internal.awaitVoid
import com.mobile.app.d1core.session.D1Session
import com.thalesgroup.gemalto.d1.CardPINUI
import com.thalesgroup.gemalto.d1.ChangePINOptions
import com.thalesgroup.gemalto.d1.D1Task
import com.thalesgroup.gemalto.d1.EntryUI
import com.thalesgroup.gemalto.d1.PINEntryUI
import com.thalesgroup.gemalto.d1.SecureEditText
import com.thalesgroup.gemalto.d1.card.CardActivationMethod

class PinManagement internal constructor(
    private val session: D1Session,
    private val task: D1Task,
) {
    suspend fun displayPin(cardId: String, ui: CardPINUI) = session.withSession {
        awaitVoid { task.displayPhysicalCardPIN(cardId, ui, it) }
    }

    /**
     * Starts PIN capture. The digits stay inside the SDK's [SecureEditText]s;
     * match state arrives only via [listener], so drive the submit button from it.
     */
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

    /**
     * Change PIN has a stricter session window than any other API, so this
     * refreshes the session immediately before submitting rather than relying on
     * a retry after the user has already typed the new PIN.
     */
    suspend fun submitChangePin(pinEntryUI: PINEntryUI) {
        session.login()
        awaitVoid { pinEntryUI.submit(it) }
    }

    suspend fun activationMethod(cardId: String): CardActivationMethod = session.withSession {
        awaitCallback { task.getCardActivationMethod(cardId, it) }
    }

    /** Challenge is CVV or last 4 PAN digits, chosen per card product. */
    suspend fun activatePhysicalCard(cardId: String, entryUI: EntryUI) = session.withSession {
        awaitVoid { task.activatePhysicalCard(cardId, entryUI, it) }
    }
}
