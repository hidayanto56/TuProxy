package com.example.tuproxy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.tuproxy.ui.ProxyDashboard
import com.example.tuproxy.ui.TuProxyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TuProxyTheme {
                ProxyDashboard()
            }
        }
    }
}
