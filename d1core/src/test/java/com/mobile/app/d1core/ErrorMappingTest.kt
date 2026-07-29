package com.mobile.app.d1core

import com.mobile.app.d1core.error.D1PAY_PREFIX
import com.mobile.app.d1core.error.D1Failure
import com.mobile.app.d1core.error.HANDLED_CODE_NAMES
import com.mobile.app.d1core.error.failureFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMappingTest {

    private fun map(codeName: String) = failureFor(codeName, "boom", null, null)

    @Test
    fun `session expiry maps to NotLoggedIn`() {
        assertTrue(map("ERROR_NOT_LOGGED_IN") is D1Failure.NotLoggedIn)
    }

    @Test
    fun `unsafe device maps to DeviceUnsafe`() {
        assertTrue(map("ERROR_DEVICE_ENVIRONMENT_UNSAFE") is D1Failure.DeviceUnsafe)
    }

    @Test
    fun `card settings errors collapse to one branch`() {
        listOf(
            "ERROR_CARD_SETTINGS_OPERATION_NOT_ALLOWED",
            "ERROR_CARD_SETTINGS_INVALID_FORMAT",
            "ERROR_CARD_SETTINGS_INVALID_VALUE",
        ).forEach { assertTrue(it, map(it) is D1Failure.CardSettingsRejected) }
    }

    @Test
    fun `d1pay codes route by prefix so future codes are covered`() {
        assertTrue(map("ERROR_D1PAY_UNRECOVERABLE") is D1Failure.D1PayUnavailable)
        assertTrue(map("ERROR_D1PAY_SOMETHING_NEW") is D1Failure.D1PayUnavailable)
    }

    @Test
    fun `unrecognised codes fall through to Unknown and keep the message`() {
        val failure = map("ERROR_CORE")
        assertTrue(failure is D1Failure.Unknown)
        assertEquals("boom", failure.message)
    }

    @Test
    fun `a null code still yields a failure`() {
        assertTrue(failureFor(null, "boom", null, null) is D1Failure.Unknown)
    }

    /**
     * Reads the enum constant names without initialising the class: the SDK's
     * DexGuard static initialiser throws on the JVM. Field names are available
     * from the class file itself, so this catches typos in the mapping table.
     */
    @Test
    fun `every handled code name exists in the SDK enum`() {
        val enumClass = Class.forName(
            "com.thalesgroup.gemalto.d1.D1Exception\$ErrorCode",
            false,
            javaClass.classLoader,
        )
        val actual = enumClass.declaredFields.filter { it.isEnumConstant }.map { it.name }.toSet()

        assertTrue("enum constants not readable", actual.isNotEmpty())
        assertEquals(emptySet<String>(), HANDLED_CODE_NAMES - actual)
        assertTrue(actual.any { it.startsWith(D1PAY_PREFIX) })
    }
}
