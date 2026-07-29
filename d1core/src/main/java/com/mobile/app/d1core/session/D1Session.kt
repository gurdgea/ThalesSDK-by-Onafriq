package com.mobile.app.d1core.session

import com.mobile.app.d1core.auth.IssuerTokenProvider
import com.mobile.app.d1core.error.D1Failure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class D1Session(
    private val gateway: D1Gateway,
    private val tokenProvider: IssuerTokenProvider,
    private val clientBindingEnabled: Boolean,
) {
    private val _state = MutableStateFlow<D1SessionState>(D1SessionState.Idle)
    val state: StateFlow<D1SessionState> = _state.asStateFlow()

    private val loginLock = Mutex()
    private var blocked: D1Failure? = null

    suspend fun configure(): List<D1Failure> {
        blocked?.let { throw it }
        _state.value = D1SessionState.Configuring

        val failures = try {
            gateway.configure()
        } catch (failure: D1Failure) {
            if (failure is D1Failure.DeviceUnsafe) latch(failure)
            else _state.value = D1SessionState.ConfigureFailed(failure)
            throw failure
        }

        failures.firstOrNull { it is D1Failure.DeviceUnsafe }?.let {
            latch(it)
            return failures
        }

        _state.value = D1SessionState.Configured(failures)
        return failures
    }

    suspend fun login() = loginLock.withLock {
        blocked?.let { throw it }
        _state.value = D1SessionState.LoggingIn

        val payload = if (clientBindingEnabled) gateway.bindingHash() else null
        val token = tokenProvider.issuerToken(payload)
        try {
            gateway.login(token)
        } catch (failure: D1Failure) {
            if (failure is D1Failure.DeviceUnsafe) latch(failure)
            throw failure
        } finally {
            token.fill(0)
        }
        _state.value = D1SessionState.LoggedIn
    }

    suspend fun logout() {
        runCatching { gateway.logout() }
        if (blocked == null) _state.value = D1SessionState.Idle
    }

    suspend fun logoutAll() {
        runCatching { gateway.logoutAll() }
        if (blocked == null) _state.value = D1SessionState.Idle
    }

    /**
     * Runs [block], re-authenticating once if the session has expired.
     * A second expiry propagates: the SDK applies a much shorter window to
     * sensitive APIs, and retrying forever would mask a real failure.
     */
    suspend fun <T> withSession(block: suspend () -> T): T {
        blocked?.let { throw it }
        return try {
            guarded(block)
        } catch (expired: D1Failure.NotLoggedIn) {
            login()
            guarded(block)
        }
    }

    private suspend fun <T> guarded(block: suspend () -> T): T = try {
        block()
    } catch (unsafe: D1Failure.DeviceUnsafe) {
        latch(unsafe)
        throw unsafe
    }

    private fun latch(failure: D1Failure) {
        blocked = failure
        _state.value = D1SessionState.Blocked(failure)
    }
}
