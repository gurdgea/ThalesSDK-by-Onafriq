package com.mobile.app.d1core.error

import com.thalesgroup.gemalto.d1.D1Exception

sealed class D1Failure(
    message: String,
    val code: D1Exception.ErrorCode?,
    cause: Throwable?,
) : Exception(message, cause) {

    /** Expected control flow, not an error: re-authenticate and retry. */
    class NotLoggedIn(code: D1Exception.ErrorCode?, cause: Throwable?) :
        D1Failure("D1 session expired", code, cause)

    /** Terminal for the session — the SDK disables every API. */
    class DeviceUnsafe(code: D1Exception.ErrorCode?, cause: Throwable?) :
        D1Failure("Device environment deemed unsafe by D1", code, cause)

    class DebugSdkInRelease(code: D1Exception.ErrorCode?, cause: Throwable?) :
        D1Failure("Debug D1 AAR used in a release build", code, cause)

    class NotAuthorized(code: D1Exception.ErrorCode?, cause: Throwable?) :
        D1Failure("Not authorized for this D1 operation", code, cause)

    class Cancelled(code: D1Exception.ErrorCode?, cause: Throwable?) :
        D1Failure("Operation cancelled", code, cause)

    class CardNotFound(code: D1Exception.ErrorCode?, cause: Throwable?) :
        D1Failure("Card not found", code, cause)

    class CardSettingsRejected(message: String, code: D1Exception.ErrorCode?, cause: Throwable?) :
        D1Failure(message, code, cause)

    class PinRejected(message: String, code: D1Exception.ErrorCode?, cause: Throwable?) :
        D1Failure(message, code, cause)

    class WalletUnavailable(message: String, code: D1Exception.ErrorCode?, cause: Throwable?) :
        D1Failure(message, code, cause)

    class D1PayUnavailable(message: String, code: D1Exception.ErrorCode?, cause: Throwable?) :
        D1Failure(message, code, cause)

    class ConfigInvalid(message: String, code: D1Exception.ErrorCode?, cause: Throwable?) :
        D1Failure(message, code, cause)

    class Unknown(message: String, code: D1Exception.ErrorCode?, cause: Throwable?) :
        D1Failure(message, code, cause)
}
