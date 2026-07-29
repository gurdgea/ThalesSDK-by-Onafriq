package com.mobile.app.d1core.error

import com.thalesgroup.gemalto.d1.D1Exception
import com.thalesgroup.gemalto.d1.D1Exception.ErrorCode

fun D1Exception.toFailure(): D1Failure {
    val code = runCatching { errorCode }.getOrNull()
    val name = runCatching { code?.name }.getOrNull()
    return failureFor(name, message ?: name ?: "Unknown D1 error", code, this)
}

/**
 * Keyed on the code *name* rather than the enum constant so it stays a pure
 * function: the SDK is DexGuard-obfuscated and its static initializers throw
 * outside an Android runtime, which would make this untestable on the JVM.
 */
internal fun failureFor(
    codeName: String?,
    message: String,
    code: ErrorCode?,
    cause: Throwable?,
): D1Failure = when (codeName) {
    NOT_LOGGED_IN -> D1Failure.NotLoggedIn(code, cause)
    DEVICE_UNSAFE -> D1Failure.DeviceUnsafe(code, cause)
    DEBUG_SDK_USED -> D1Failure.DebugSdkInRelease(code, cause)
    NOT_AUTHORIZED -> D1Failure.NotAuthorized(code, cause)
    CANCELLED -> D1Failure.Cancelled(code, cause)
    CARD_NOT_FOUND -> D1Failure.CardNotFound(code, cause)
    in CARD_SETTINGS_CODES -> D1Failure.CardSettingsRejected(message, code, cause)
    in PIN_CODES -> D1Failure.PinRejected(message, code, cause)
    in WALLET_CODES -> D1Failure.WalletUnavailable(message, code, cause)
    INVALID_ARGUMENT -> D1Failure.ConfigInvalid(message, code, cause)
    else -> when {
        codeName?.startsWith(D1PAY_PREFIX) == true ->
            D1Failure.D1PayUnavailable(message, code, cause)

        else -> D1Failure.Unknown(message, code, cause)
    }
}

internal const val NOT_LOGGED_IN = "ERROR_NOT_LOGGED_IN"
internal const val DEVICE_UNSAFE = "ERROR_DEVICE_ENVIRONMENT_UNSAFE"
internal const val DEBUG_SDK_USED = "ERROR_DEBUG_SDK_USED"
internal const val NOT_AUTHORIZED = "ERROR_NOT_AUTHORIZED"
internal const val CANCELLED = "ERROR_CANCELLED"
internal const val CARD_NOT_FOUND = "ERROR_CARD_NOT_FOUND"
internal const val INVALID_ARGUMENT = "ERROR_INVALID_ARGUMENT"
internal const val D1PAY_PREFIX = "ERROR_D1PAY"

internal val CARD_SETTINGS_CODES = setOf(
    "ERROR_CARD_SETTINGS_OPERATION_NOT_ALLOWED",
    "ERROR_CARD_SETTINGS_INVALID_FORMAT",
    "ERROR_CARD_SETTINGS_INVALID_VALUE",
)

internal val PIN_CODES = setOf(
    "ERROR_PIN_MISMATCH",
    "ERROR_PIN_INVALID",
    "ERROR_PIN_CHANGE_FORBIDDEN",
)

internal val WALLET_CODES = setOf(
    "ERROR_GPAY",
    "ERROR_GPAY_NOT_SUPPORTED",
    "ERROR_SPAY",
    "ERROR_SPAY_NOT_SUPPORTED",
    "ERROR_SPAY_APP_NOT_FOUND",
    "ERROR_SPAY_NEED_TO_UPDATE",
    "ERROR_SPAY_SETUP_NOT_COMPLETED",
)

internal val HANDLED_CODE_NAMES: Set<String> =
    setOf(
        NOT_LOGGED_IN, DEVICE_UNSAFE, DEBUG_SDK_USED, NOT_AUTHORIZED,
        CANCELLED, CARD_NOT_FOUND, INVALID_ARGUMENT,
    ) + CARD_SETTINGS_CODES + PIN_CODES + WALLET_CODES
