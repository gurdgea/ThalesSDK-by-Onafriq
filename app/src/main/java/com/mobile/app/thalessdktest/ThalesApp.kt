package com.mobile.app.thalessdktest

import android.app.Application
import com.mobile.app.thalessdktest.di.D1Locator

class ThalesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Build eagerly so configuration problems are known before the first screen.
        D1Locator.client(this)
    }
}
