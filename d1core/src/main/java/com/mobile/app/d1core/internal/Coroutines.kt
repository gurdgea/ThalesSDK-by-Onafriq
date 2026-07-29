package com.mobile.app.d1core.internal

import com.mobile.app.d1core.error.toFailure
import com.thalesgroup.gemalto.d1.D1Exception
import com.thalesgroup.gemalto.d1.D1Task
import com.thalesgroup.gemalto.d1.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal suspend fun <T : Any> awaitCallback(block: (D1Task.Callback<T>) -> Unit): T =
    suspendCancellableCoroutine { continuation ->
        block(object : D1Task.Callback<T> {
            override fun onSuccess(result: T?) {
                if (!continuation.isActive) return
                if (result == null) {
                    continuation.resumeWithException(
                        IllegalStateException("D1 returned no result")
                    )
                } else {
                    continuation.resume(result)
                }
            }

            override fun onError(exception: D1Exception) {
                if (continuation.isActive) continuation.resumeWithException(exception.toFailure())
            }
        })
    }

internal suspend fun awaitVoid(block: (D1Task.Callback<Void>) -> Unit): Unit =
    suspendCancellableCoroutine { continuation ->
        block(object : D1Task.Callback<Void> {
            override fun onSuccess(result: Void?) {
                if (continuation.isActive) continuation.resume(Unit)
            }

            override fun onError(exception: D1Exception) {
                if (continuation.isActive) continuation.resumeWithException(exception.toFailure())
            }
        })
    }

internal suspend fun <T : Any> Task<T>.awaitResult(): T = awaitCallback { execute(it) }

internal suspend fun Task<Void>.awaitVoidResult(): Unit = awaitVoid { execute(it) }

/** Resolves to the per-target failures; empty means every configured target succeeded. */
internal suspend fun awaitConfigure(
    block: (D1Task.ConfigCallback<Void>) -> Unit,
): List<D1Exception> = suspendCancellableCoroutine { continuation ->
    block(object : D1Task.ConfigCallback<Void> {
        override fun onSuccess(result: Void?) {
            if (continuation.isActive) continuation.resume(emptyList())
        }

        override fun onError(exceptions: List<D1Exception>) {
            if (continuation.isActive) continuation.resume(exceptions)
        }
    })
}
