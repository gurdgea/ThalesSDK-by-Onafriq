package com.mobile.app.d1core.session

import com.mobile.app.d1core.error.D1Failure

sealed interface D1SessionState {
    data object Idle : D1SessionState
    data object Configuring : D1SessionState

    /** Core init succeeded. [warnings] holds targets that failed independently. */
    data class Configured(val warnings: List<D1Failure>) : D1SessionState

    data object LoggingIn : D1SessionState
    data object LoggedIn : D1SessionState

    /** Terminal for this process — the SDK has disabled every API. */
    data class Blocked(val reason: D1Failure) : D1SessionState

    data class ConfigureFailed(val reason: D1Failure) : D1SessionState
}
