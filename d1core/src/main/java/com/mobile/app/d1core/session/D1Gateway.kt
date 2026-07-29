package com.mobile.app.d1core.session

import com.mobile.app.d1core.error.D1Failure

/**
 * The SDK boundary. Exists so session policy stays testable: D1 classes are
 * DexGuard-obfuscated and cannot be loaded outside an Android runtime.
 */
internal interface D1Gateway {
    /** Returns per-target failures; empty means every configured target succeeded. */
    suspend fun configure(): List<D1Failure>

    fun bindingHash(): String?

    suspend fun login(token: ByteArray)

    suspend fun logout()

    suspend fun logoutAll()
}
