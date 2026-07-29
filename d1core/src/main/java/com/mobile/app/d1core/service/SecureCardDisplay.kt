package com.mobile.app.d1core.service

import com.mobile.app.d1core.internal.awaitResult
import com.mobile.app.d1core.internal.awaitVoidResult
import com.mobile.app.d1core.session.D1Session
import com.thalesgroup.gemalto.d1.D1Task
import com.thalesgroup.gemalto.d1.card.CardAsset
import com.thalesgroup.gemalto.d1.card.CardMetadata
import com.thalesgroup.gemalto.d1.securecarddisplay.CardDetails
import com.thalesgroup.gemalto.d1.securecarddisplay.CardDetailsUI

class SecureCardDisplay internal constructor(
    private val session: D1Session,
    private val task: D1Task,
) {
    suspend fun metadata(cardId: String): CardMetadata = session.withSession {
        task.secureCardDisplayService.getCardMetadata(cardId).awaitResult()
    }

    /** Hits the backend once, then serves from a local cache. */
    suspend fun assets(metadata: CardMetadata): List<CardAsset> = session.withSession {
        metadata.getAssetList().awaitResult()
    }

    /**
     * Option 1 — the app renders. Card data arrives as `byte[]` so it can be
     * wiped; [block] must not retain it or convert it to a String that outlives
     * the call. The details are wiped before this returns, including on throw.
     */
    suspend fun <R> withCardDetails(cardId: String, block: (CardDetails) -> R): R =
        session.withSession {
            val details = task.secureCardDisplayService.getCardDetails(cardId).awaitResult()
            try {
                block(details)
            } finally {
                details.wipe()
            }
        }

    /** Option 2 — the SDK renders into [ui]; no card data reaches this process. */
    suspend fun displayCardDetails(cardId: String, ui: CardDetailsUI) = session.withSession {
        task.secureCardDisplayService.displayCardDetails(cardId, ui).awaitVoidResult()
    }

    /** Always refetches from the server; it never re-renders a cached value. */
    suspend fun reveal(ui: CardDetailsUI) = session.withSession {
        ui.showCardDetails().awaitVoidResult()
    }

    fun mask(ui: CardDetailsUI) = ui.maskCardDetails()
}
