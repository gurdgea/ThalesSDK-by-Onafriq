package com.mobile.app.d1core.session

import android.content.Context
import com.mobile.app.d1core.D1ParamsContributor
import com.mobile.app.d1core.config.D1Config
import com.mobile.app.d1core.error.D1Failure
import com.mobile.app.d1core.error.toFailure
import com.mobile.app.d1core.internal.awaitConfigure
import com.mobile.app.d1core.internal.awaitVoid
import com.thalesgroup.gemalto.d1.ConfigParams
import com.thalesgroup.gemalto.d1.D1Exception
import com.thalesgroup.gemalto.d1.D1Params
import com.thalesgroup.gemalto.d1.D1Task
import com.thalesgroup.gemalto.d1.card.OEMPayType

internal class D1TaskGateway(
    context: Context,
    private val config: D1Config,
    private val contributors: List<D1ParamsContributor>,
) : D1Gateway {

    private val appContext = context.applicationContext

    val task: D1Task by lazy {
        D1Task.Builder()
            .setContext(appContext)
            .setD1ServiceURL(config.serviceUrl)
            .setIssuerID(config.issuerId)
            .setDigitalCardURL(config.digitalCardUrl)
            .setD1ServiceRSAModulus(config.rsaModulus)
            .setD1ServiceRSAExponent(config.rsaExponent)
            .apply {
                config.applicationProfileId?.let { setApplicationProfileId(it) }
                if (!config.secureLogEnabled) disableLogService()
            }
            .build()
    }

    override suspend fun configure(): List<D1Failure> {
        val params = buildList {
            add(ConfigParams.buildConfigCore(config.consumerId))
            add(
                ConfigParams.buildConfigCard(
                    OEMPayType.GOOGLE_PAY,
                    null,
                    config.visaClientAppId,
                )
            )
            contributors.forEach { contributor ->
                contributor.params(appContext)?.let(::add)
            }
        }

        return awaitConfigure { callback ->
            task.configure(callback, *params.toTypedArray<D1Params>())
        }.map(D1Exception::toFailure)
    }

    override fun bindingHash(): String? = try {
        task.bindingHash
    } catch (exception: D1Exception) {
        throw exception.toFailure()
    }

    override suspend fun login(token: ByteArray) = awaitVoid { task.login(token, it) }

    override suspend fun logout() = awaitVoid { task.logout(it) }

    override suspend fun logoutAll() = awaitVoid { task.logoutAll(it) }
}
