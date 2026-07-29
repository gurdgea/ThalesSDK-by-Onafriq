package com.mobile.app.d1core.service

import android.app.Activity
import android.content.Intent
import com.mobile.app.d1core.internal.awaitResult
import com.mobile.app.d1core.session.D1Session
import com.thalesgroup.gemalto.d1.D1Task
import com.thalesgroup.gemalto.d1.card.OEMPayType
import com.thalesgroup.gemalto.d1.pushprovisioning.CardDigitizationState
import com.thalesgroup.gemalto.d1.pushprovisioning.TokenRequestor

class PushProvisioning internal constructor(
    private val session: D1Session,
    private val task: D1Task,
) {
    /** Resolved on-device with no network call — prefer this form. */
    suspend fun digitizationStateByLast4(
        wallet: OEMPayType,
        last4: String,
    ): CardDigitizationState = session.withSession {
        task.pushProvisioningService.getCardDigitizationState(wallet, last4).awaitResult()
    }

    suspend fun digitizationStateByCardId(
        cardId: String,
        wallet: OEMPayType,
    ): CardDigitizationState = session.withSession {
        task.pushProvisioningService.getCardDigitizationState(cardId, wallet).awaitResult()
    }

    /** Only call when the state is NOT_DIGITIZED. */
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

    suspend fun tokenRequestors(cardId: String): List<TokenRequestor> = session.withSession {
        task.pushProvisioningService.getTokenRequestorList(cardId).awaitResult()
    }

    /** Returns a deep link the caller must launch to hand off to the requestor. */
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

    /**
     * Must be called from `Activity.onActivityResult`. Without it Google Pay
     * provisioning never completes and fails silently.
     */
    fun handleWalletResult(requestCode: Int, resultCode: Int, data: Intent?) =
        task.handleCardResult(requestCode, resultCode, data)
}
