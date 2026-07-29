package com.mobile.app.d1pay

import android.content.Context
import com.mobile.app.d1core.D1ParamsContributor
import com.thalesgroup.gemalto.d1.D1Params

/**
 * Contributes the D1Pay configuration when the SDK provides it.
 *
 * The delivered 4.4.0 AAR (`d1-debug-4.4.0.aar`) has no `d1pay` package — it is
 * the non-D1Pay build — so `D1PayConfigParams` cannot be referenced directly
 * without breaking compilation. Resolving it reflectively keeps this code in the
 * build today and makes it live the moment a D1Pay-enabled AAR is dropped in,
 * with no source change.
 *
 * When that happens, replace the body of [params] with the direct call:
 * `return D1PayConfigParams.getInstance()`.
 */
class D1PayParamsContributor : D1ParamsContributor {

    override fun params(context: Context): D1Params? = configParams()

    val isAvailable: Boolean get() = configParamsClass != null

    private fun configParams(): D1Params? {
        val type = configParamsClass ?: return null
        return runCatching {
            type.getMethod("getInstance").invoke(null) as? D1Params
        }.getOrNull()
    }

    private companion object {
        private const val CLASS_NAME = "com.thalesgroup.gemalto.d1.d1pay.D1PayConfigParams"

        val configParamsClass: Class<*>? by lazy {
            runCatching { Class.forName(CLASS_NAME) }.getOrNull()
        }
    }
}
