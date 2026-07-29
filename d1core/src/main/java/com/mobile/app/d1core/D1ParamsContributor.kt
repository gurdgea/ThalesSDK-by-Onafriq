package com.mobile.app.d1core

import android.content.Context
import com.thalesgroup.gemalto.d1.D1Params

/**
 * Contributes an extra [D1Params] to `D1Task.configure`. Lets optional features
 * live in their own module and stay absent when their SDK component is not in
 * the delivered AAR; return null to contribute nothing.
 */
fun interface D1ParamsContributor {
    fun params(context: Context): D1Params?
}
