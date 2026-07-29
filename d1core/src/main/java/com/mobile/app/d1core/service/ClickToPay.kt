package com.mobile.app.d1core.service

import com.mobile.app.d1core.internal.awaitCallback
import com.mobile.app.d1core.session.D1Session
import com.thalesgroup.gemalto.d1.D1Task
import com.thalesgroup.gemalto.d1.clicktopay.BillingAddress
import com.thalesgroup.gemalto.d1.clicktopay.ConsumerInfo
import com.thalesgroup.gemalto.d1.clicktopay.ProfileResult
import com.thalesgroup.gemalto.d1.clicktopay.Status

class ClickToPay internal constructor(
    private val session: D1Session,
    private val task: D1Task,
) {
    suspend fun enrol(
        cardId: String,
        consumer: ConsumerInfo,
        cardHolderName: String,
        billingAddress: BillingAddress,
    ): Status = session.withSession {
        awaitCallback {
            task.clickToPayService.enrol(cardId, consumer, cardHolderName, billingAddress, it)
        }
    }

    suspend fun profiles(): ProfileResult = session.withSession {
        awaitCallback { task.clickToPayService.getProfiles(it) }
    }

    suspend fun updateCard(
        cardId: String,
        cardHolderName: String,
        billingAddress: BillingAddress,
    ): Status = session.withSession {
        awaitCallback {
            task.clickToPayService.updateCard(cardId, cardHolderName, billingAddress, it)
        }
    }

    suspend fun updateConsumer(
        consumer: ConsumerInfo,
        billingAddress: BillingAddress,
    ): Status = session.withSession {
        awaitCallback { task.clickToPayService.updateConsumer(consumer, billingAddress, it) }
    }

    suspend fun optOutCard(cardId: String): Status = session.withSession {
        awaitCallback { task.clickToPayService.optOutCard(cardId, it) }
    }

    suspend fun optOutConsumer(): Status = session.withSession {
        awaitCallback { task.clickToPayService.optOutConsumer(it) }
    }
}
