package com.mobile.app.thalessdktest.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mobile.app.d1core.push.D1PushHandler
import com.mobile.app.thalessdktest.di.D1Locator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Inert until `google-services.json` is added: the `google-services` plugin is
 * deliberately not applied, because applying it without that file fails the build.
 */
class D1FirebaseService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Deprecated by firebase-messaging 25.x, but it remains the only hook for
    // token renewal and the D1 guide still prescribes registering the token here.
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        val client = D1Locator.client(this).getOrNull() ?: return
        scope.launch {
            // Prefix HMS tokens with "HMS:" so D1 routes to the right push service.
            runCatching { client.push.updatePushToken(token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (!D1PushHandler.isD1Notification(data)) return

        val client = D1Locator.client(this).getOrNull() ?: return
        scope.launch {
            runCatching { client.push.processNotification(data) }
        }
    }

    override fun onDestroy() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }
}
