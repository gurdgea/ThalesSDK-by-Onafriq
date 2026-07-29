package com.mobile.app.d1core.service

import com.mobile.app.d1core.internal.awaitCallback
import com.mobile.app.d1core.internal.awaitVoid
import com.mobile.app.d1core.session.D1Session
import com.thalesgroup.gemalto.d1.D1Task
import com.thalesgroup.gemalto.d1.messaging.Message

class Messaging internal constructor(
    private val session: D1Session,
    private val task: D1Task,
) {
    suspend fun register() = session.withSession {
        awaitVoid { task.messagingService.registerMessageNotification(it) }
    }

    suspend fun unregister() = session.withSession {
        awaitVoid { task.messagingService.unregisterMessageNotification(it) }
    }

    suspend fun messages(): List<Message> = session.withSession {
        awaitCallback { task.messagingService.getMessageList(it) }
    }

    suspend fun markRead(messageIds: List<String>) = session.withSession {
        awaitVoid { task.messagingService.markMessageListAsRead(messageIds, it) }
    }
}
