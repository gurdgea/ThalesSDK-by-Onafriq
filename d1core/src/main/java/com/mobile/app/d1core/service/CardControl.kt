package com.mobile.app.d1core.service

import com.mobile.app.d1core.internal.awaitCallback
import com.mobile.app.d1core.internal.awaitVoid
import com.mobile.app.d1core.session.D1Session
import com.thalesgroup.gemalto.d1.D1Task
import com.thalesgroup.gemalto.d1.card.cardservice.Card
import com.thalesgroup.gemalto.d1.card.cardservice.CardControlSettings
import com.thalesgroup.gemalto.d1.card.cardservice.CardLimitSettings
import com.thalesgroup.gemalto.d1.card.cardservice.CardSettings
import com.thalesgroup.gemalto.d1.card.cardservice.CardTransactionRecord
import com.thalesgroup.gemalto.d1.card.cardservice.StateReason
import com.thalesgroup.gemalto.d1.card.cardservice.TransactionQuery

class CardControl internal constructor(
    private val session: D1Session,
    private val task: D1Task,
) {
    suspend fun cards(): List<Card> = session.withSession {
        awaitCallback { task.cardService.getCardList(it) }
    }

    /**
     * The only legitimate source of a [CardSettings]. Never construct one:
     * a self-built object overwrites backend state and the SDK rejects it.
     * Controls that come back null are not offered by the card product —
     * hide the row rather than rendering a disabled switch.
     */
    suspend fun settings(cardId: String): CardSettings = session.withSession {
        awaitCallback { task.cardService.getCardSettings(cardId, it) }
    }

    suspend fun updateControls(cardId: String, controls: CardControlSettings) =
        session.withSession {
            awaitVoid { task.cardService.updateCardControlSettings(cardId, controls, it) }
        }

    suspend fun updateLimits(cardId: String, limits: CardLimitSettings) = session.withSession {
        awaitVoid { task.cardService.updateCardLimitSettings(cardId, limits, it) }
    }

    /** Capped by D1 at 50 records over the last 30 days. */
    suspend fun transactions(
        cardId: String,
        query: TransactionQuery = TransactionQuery(),
    ): List<CardTransactionRecord> = session.withSession {
        awaitCallback { task.cardService.getTransactionHistory(cardId, query, it) }
    }

    suspend fun suspendCard(cardId: String) = session.withSession {
        awaitVoid { task.cardService.suspendCard(cardId, it) }
    }

    suspend fun resumeCard(cardId: String) = session.withSession {
        awaitVoid { task.cardService.resumeCard(cardId, it) }
    }

    /** Returns the replacement card id. */
    suspend fun replaceCard(cardId: String, reason: StateReason): String = session.withSession {
        awaitCallback { task.cardService.replaceCard(cardId, reason, it) }
    }
}
