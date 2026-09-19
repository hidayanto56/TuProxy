package com.tustudio.tuproxy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tustudio.tuproxy.ui.ProxyDashboard
import com.tustudio.tuproxy.ui.TuProxyTheme
import com.tustudio.tuproxy.billing.BillingManager
import com.tustudio.tuproxy.utils.ReviewHelper
import com.google.android.gms.ads.MobileAds

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // AdMob: async init; banner degrades to a slim placeholder when offline.
        MobileAds.initialize(this) {}
        BillingManager.init(this)
        requestNotificationPermission()
        setContent {
            TuProxyTheme {
                ProxyDashboard()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        BillingManager.refreshPro()
        ReviewHelper.onAppForeground(this)
    }

    /** Android 13+ requires runtime opt-in for status notifications. */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001
            )
        }
    }
}
