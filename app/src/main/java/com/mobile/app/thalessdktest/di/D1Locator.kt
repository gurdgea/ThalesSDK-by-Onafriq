package com.mobile.app.thalessdktest.di

import android.content.Context
import com.mobile.app.d1core.D1Client
import com.mobile.app.d1core.config.D1Config
import com.mobile.app.d1core.config.D1ConfigException
import com.mobile.app.d1pay.D1PayParamsContributor

object D1Locator {

    private var cached: Result<D1Client>? = null

    val d1Pay = D1PayParamsContributor()

    /**
     * Held as a [Result] so a misconfigured environment surfaces on a screen
     * instead of crashing the process at startup — every value in
     * `config/<env>.properties` ships as a placeholder.
     */
    fun client(context: Context): Result<D1Client> = cached ?: runCatching {
        D1Client.create(
            context = context.applicationContext,
            contributors = listOf(d1Pay),
        )
    }.also { cached = it }

    fun configOrNull(): D1Config? = cached?.getOrNull()?.config
}
