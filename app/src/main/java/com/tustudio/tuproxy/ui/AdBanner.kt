package com.tustudio.tuproxy.ui

import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tustudio.tuproxy.R
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.AdSize.SMART_BANNER

/**
 * Non-intrusive anchored banner at the very bottom of the screen.
 * Uses SMART_BANNER — auto‑adapts width to device screen, so layout never jumps
 * on phones of any size or orientation. Fixed 50dp height; collapses to a slim
 * placeholder when the ad fails to load (offline / no fill), so the layout never jumps.
 * Replace @string/admob_banner_id with your production unit before release.
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var loadFailed by remember { mutableStateOf(false) }
    val adUnitId = remember { context.getString(R.string.admob_banner_id) }
    val unitId = adUnitId

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Ad",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        if (loadFailed) {
            // Slim idle placeholder — keeps bottom spacing stable, no animation.
            androidx.compose.foundation.layout.Spacer(
                Modifier.fillMaxWidth().height(50.dp)
            )
        } else {
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(50.dp),
                factory = { ctx ->
                    AdView(ctx).apply {
                        setAdSize(SMART_BANNER)
                        this.adUnitId = unitId
                        visibility = View.VISIBLE
                        adListener = object : AdListener() {
                            override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                                loadFailed = true
                            }
                        }
                        loadAd(AdRequest.Builder().build())
                    }
                },
                update = { _ -> }
            )
        }
    }
}
