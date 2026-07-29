package com.mobile.app.d1core.push

import com.mobile.app.d1core.internal.awaitCallback
import com.mobile.app.d1core.internal.awaitVoid
import com.thalesgroup.gemalto.d1.D1Task
import com.thalesgroup.gemalto.d1.PushResponseKey

class D1PushHandler internal constructor(private val task: D1Task) {

    /**
     * Call `login()` first when Click to Pay or the Messaging Service is in use.
     * Prefix HMS tokens with `HMS:` so D1 routes to the right push service.
     */
    suspend fun updatePushToken(token: String) = awaitVoid { task.updatePushToken(token, it) }

    suspend fun processNotification(data: Map<String, String>): Map<PushResponseKey, String> =
        awaitCallback { task.processNotification(data, it) }

    companion object {
        private val SENDERS = setOf("CPS", "MG", "TNS")
        private const val TOPIC = "D1_NOTIFICATION"

        /** Pre-filter so only D1's own messages are handed to the SDK. */
        fun isD1Notification(data: Map<String, String>): Boolean =
            data["sender"] in SENDERS || data["topic"] == TOPIC
    }
}
