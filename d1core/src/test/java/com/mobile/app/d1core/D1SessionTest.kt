package com.mobile.app.d1core

import com.mobile.app.d1core.auth.IssuerTokenProvider
import com.mobile.app.d1core.error.D1Failure
import com.mobile.app.d1core.session.D1Gateway
import com.mobile.app.d1core.session.D1Session
import com.mobile.app.d1core.session.D1SessionState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private fun notLoggedIn() = D1Failure.NotLoggedIn(null, null)
private fun deviceUnsafe() = D1Failure.DeviceUnsafe(null, null)

private class FakeGateway(
    var configureResult: () -> List<D1Failure> = { emptyList() },
    var binding: String? = "binding-payload",
) : D1Gateway {
    var loginCount = 0
    var lastToken: ByteArray? = null
    var logoutCount = 0

    override suspend fun configure(): List<D1Failure> = configureResult()
    override fun bindingHash(): String? = binding
    override suspend fun login(token: ByteArray) {
        loginCount++
        lastToken = token
    }

    override suspend fun logout() {
        logoutCount++
    }

    override suspend fun logoutAll() {
        logoutCount++
    }
}

private fun session(
    gateway: D1Gateway,
    clientBinding: Boolean = false,
    tokenProvider: IssuerTokenProvider = IssuerTokenProvider { "token".toByteArray() },
) = D1Session(gateway, tokenProvider, clientBinding)

class D1SessionTest {

    @Test
    fun `retries exactly once when the session has expired`() = runTest {
        val gateway = FakeGateway()
        val subject = session(gateway)
        var attempts = 0

        val result = subject.withSession {
            attempts++
            if (attempts == 1) throw notLoggedIn()
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(2, attempts)
        assertEquals(1, gateway.loginCount)
    }

    @Test
    fun `a second expiry propagates instead of retrying again`() = runTest {
        val gateway = FakeGateway()
        val subject = session(gateway)
        var attempts = 0

        try {
            subject.withSession {
                attempts++
                throw notLoggedIn()
            }
            fail("expected NotLoggedIn to propagate")
        } catch (expected: D1Failure.NotLoggedIn) {
            // expected
        }

        assertEquals(2, attempts)
        assertEquals(1, gateway.loginCount)
    }

    @Test
    fun `does not re-login when the call succeeds`() = runTest {
        val gateway = FakeGateway()
        assertEquals("ok", session(gateway).withSession { "ok" })
        assertEquals(0, gateway.loginCount)
    }

    @Test
    fun `unsafe device latches and short-circuits later calls`() = runTest {
        val gateway = FakeGateway()
        val subject = session(gateway)

        try {
            subject.withSession { throw deviceUnsafe() }
            fail("expected DeviceUnsafe")
        } catch (expected: D1Failure.DeviceUnsafe) {
            // expected
        }

        assertTrue(subject.state.value is D1SessionState.Blocked)

        var ran = false
        try {
            subject.withSession { ran = true }
            fail("expected the latched failure to short-circuit")
        } catch (expected: D1Failure.DeviceUnsafe) {
            // expected
        }
        assertTrue("block must not run once blocked", !ran)
    }

    @Test
    fun `configure surfaces per-target failures without failing outright`() = runTest {
        val walletDown = D1Failure.WalletUnavailable("gpay down", null, null)
        val gateway = FakeGateway(configureResult = { listOf(walletDown) })
        val subject = session(gateway)

        val warnings = subject.configure()

        assertEquals(listOf(walletDown), warnings)
        assertEquals(D1SessionState.Configured(listOf(walletDown)), subject.state.value)
    }

    @Test
    fun `configure blocks when a target reports an unsafe device`() = runTest {
        val gateway = FakeGateway(configureResult = { listOf(deviceUnsafe()) })
        val subject = session(gateway)

        subject.configure()

        assertTrue(subject.state.value is D1SessionState.Blocked)
    }

    @Test
    fun `binding payload is requested only when client binding is enabled`() = runTest {
        var seen: String? = "unset"
        val provider = IssuerTokenProvider { payload ->
            seen = payload
            "token".toByteArray()
        }

        session(FakeGateway(), clientBinding = false, tokenProvider = provider).login()
        assertEquals(null, seen)

        session(FakeGateway(), clientBinding = true, tokenProvider = provider).login()
        assertEquals("binding-payload", seen)
    }

    @Test
    fun `token is wiped after login`() = runTest {
        val gateway = FakeGateway()
        session(gateway, tokenProvider = { "token".toByteArray() }).login()

        assertTrue(gateway.lastToken!!.all { it == 0.toByte() })
    }

    @Test
    fun `login reaches LoggedIn`() = runTest {
        val subject = session(FakeGateway())
        subject.login()
        assertEquals(D1SessionState.LoggedIn, subject.state.value)
    }
}
