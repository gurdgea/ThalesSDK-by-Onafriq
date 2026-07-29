package com.mobile.app.thalessdktest

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.mobile.app.d1core.D1Client
import com.mobile.app.thalessdktest.di.D1Locator
import com.mobile.app.thalessdktest.nav.D1App
import com.mobile.app.thalessdktest.ui.theme.ThalesSDKTestTheme
import com.thalesgroup.gemalto.d1.card.CardDataChangedListener

class MainActivity : ComponentActivity() {

    private val client: D1Client? by lazy { D1Locator.client(this).getOrNull() }

    private val walletChanges = CardDataChangedListener {
        // Wallet-side change: refresh token status. Only delivered in the foreground.
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()

        setContent {
            ThalesSDKTestTheme {
                D1App()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // registerCardDataChangedListener only fires in the foreground, so the
        // listener is re-registered here rather than once at configure time.
        runCatching { client?.observeWalletChanges(walletChanges) }
    }

    override fun onPause() {
        runCatching { client?.stopObservingWalletChanges() }
        super.onPause()
    }

    /**
     * Required by the SDK. Without forwarding here, Google Pay push provisioning
     * silently never completes.
     */
    @Deprecated("Required by the D1 SDK, which has no ActivityResult API equivalent")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        runCatching { client?.pushProvisioning?.handleWalletResult(requestCode, resultCode, data) }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
