package com.mobile.app.d1core.service

import com.mobile.app.d1core.internal.awaitCallback
import com.mobile.app.d1core.internal.awaitVoid
import com.mobile.app.d1core.session.D1Session
import com.thalesgroup.gemalto.d1.D1Task
import com.thalesgroup.gemalto.d1.card.CardAction
import com.thalesgroup.gemalto.d1.card.DigitalCard
import com.thalesgroup.gemalto.d1.card.OEMPayType
import com.thalesgroup.gemalto.d1.digitalcard.BindReason
import com.thalesgroup.gemalto.d1.digitalcard.UnbindReason
import com.thalesgroup.gemalto.d1.digitalcard.VerificationReason

/**
 * [InvalidTransition] is a real outcome, not an error: the SDK reports an
 * illegal state change (activating an already-active card) by succeeding at the
 * transport level and returning false.
 */
enum class DigitalCardUpdate { Applied, InvalidTransition }

class DigitalCards internal constructor(
    private val session: D1Session,
    private val task: D1Task,
) {
    suspend fun list(cardId: String): List<DigitalCard> = session.withSession {
        awaitCallback { task.getDigitalCardList(cardId, it) }
    }

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

    /** Completes in-app ID&V after the user was verified by the issuer app. */
    suspend fun activate(digitalCardId: String) = session.withSession {
        awaitVoid { task.d1PushWallet.activateDigitalCard(digitalCardId, it) }
    }

    suspend fun activate(digitalCardId: String, wallet: OEMPayType) = session.withSession {
        awaitVoid { task.d1PushWallet.activateDigitalCard(digitalCardId, wallet, it) }
    }

    suspend fun approveBinding(
        digitalCardId: String,
        bindingReference: String,
        reason: BindReason,
    ) = session.withSession {
        awaitVoid {
            task.digitalCardService.approveBinding(digitalCardId, bindingReference, reason, it)
        }
    }

    suspend fun unbindDevice(
        digitalCardId: String,
        bindingReference: String,
        reason: UnbindReason,
    ) = session.withSession {
        awaitVoid {
            task.digitalCardService.unbindDevice(digitalCardId, bindingReference, reason, it)
        }
    }

    suspend fun approveCardholderVerification(
        digitalCardId: String,
        reason: VerificationReason,
    ) = session.withSession {
        awaitVoid {
            task.digitalCardService.approveCardholderVerification(digitalCardId, reason, it)
        }
    }
}
