package com.mobile.app.d1core

import android.content.Context
import com.mobile.app.d1core.auth.ConfigIssuerTokenProvider
import com.mobile.app.d1core.auth.IssuerTokenProvider
import com.mobile.app.d1core.config.D1Config
import com.mobile.app.d1core.config.D1ConfigException
import com.mobile.app.d1core.error.D1Failure
import com.mobile.app.d1core.push.D1PushHandler
import com.mobile.app.d1core.service.CardControl
import com.mobile.app.d1core.service.ClickToPay
import com.mobile.app.d1core.service.DigitalCards
import com.mobile.app.d1core.service.Messaging
import com.mobile.app.d1core.service.PinManagement
import com.mobile.app.d1core.service.PushProvisioning
import com.mobile.app.d1core.service.SecureCardDisplay
import com.mobile.app.d1core.session.D1Session
import com.mobile.app.d1core.session.D1SessionState
import com.mobile.app.d1core.session.D1TaskGateway
import com.thalesgroup.gemalto.d1.D1Task
import com.thalesgroup.gemalto.d1.card.CardDataChangedListener
import kotlinx.coroutines.flow.StateFlow

class D1Client private constructor(
    val config: D1Config,
    private val gateway: D1TaskGateway,
    private val session: D1Session,
) {
    private val task: D1Task get() = gateway.task

    val state: StateFlow<D1SessionState> get() = session.state

    val secureCardDisplay: SecureCardDisplay by lazy { SecureCardDisplay(session, task) }
    val pushProvisioning: PushProvisioning by lazy { PushProvisioning(session, task) }
    val cardControl: CardControl by lazy { CardControl(session, task) }
    val digitalCards: DigitalCards by lazy { DigitalCards(session, task) }
    val pin: PinManagement by lazy { PinManagement(session, task) }
    val messaging: Messaging by lazy { Messaging(session, task) }
    val clickToPay: ClickToPay by lazy { ClickToPay(session, task) }
    val push: D1PushHandler by lazy { D1PushHandler(task) }

    /** Warnings are per-target failures; core init succeeded regardless. */
    suspend fun configure(): List<D1Failure> = session.configure()

    suspend fun login() = session.login()

    suspend fun logout() = session.logout()

    suspend fun logoutAll() = session.logoutAll()

    fun sdkVersions(): Map<String, String> = D1Task.getSDKVersions()

    fun appInstanceId(context: Context): String = D1Task.getAppInstanceID(context)

    /**
     * Fires only while the app is in the foreground, so also refresh on launch
     * and on resume rather than relying on it alone.
     */
    fun observeWalletChanges(listener: CardDataChangedListener) =
        task.registerCardDataChangedListener(listener)

    fun stopObservingWalletChanges() = task.unRegisterCardDataChangedListener()

    companion object {
        fun create(
            context: Context,
            config: D1Config = D1Config.fromBuildConfig(),
            contributors: List<D1ParamsContributor> = emptyList(),
            tokenProvider: IssuerTokenProvider = ConfigIssuerTokenProvider(config),
        ): D1Client {
            config.validate().takeIf { it.isNotEmpty() }?.let {
                throw D1ConfigException(config.environment, it)
            }

            val gateway = D1TaskGateway(context, config, contributors)
            val session = D1Session(gateway, tokenProvider, config.clientBindingEnabled)
            return D1Client(config, gateway, session)
        }

        fun reset(context: Context) = D1Task.reset(context)
    }
}
