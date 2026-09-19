package com.tustudio.tuproxy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tustudio.tuproxy.R
import com.tustudio.tuproxy.billing.BillingManager
import com.tustudio.tuproxy.engine.ConnEntry
import com.tustudio.tuproxy.engine.ConnectionLog
import com.tustudio.tuproxy.engine.ProxyEngine
import com.tustudio.tuproxy.services.ProxyService
import com.tustudio.tuproxy.utils.IPUtils
import com.tustudio.tuproxy.utils.formatBytes

private val Green = Color(0xFF3FB950)
private val Red = Color(0xFFF85149)

@Composable
fun ProxyDashboard() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val activity = context as? android.app.Activity
    val state by ProxyService.uiState.collectAsState()
    val connections by ConnectionLog.flow.collectAsState()
    val pro by BillingManager.pro.collectAsState()
    var ips by remember { mutableStateOf<List<String>>(emptyList()) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showDonate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ips = IPUtils.getAvailableIPv4Addresses(context)
    }

    val anyOn = state.running.isNotEmpty()
    val primaryIp = remember(ips) {
        ips.firstOrNull()?.substringBefore(" ")?.trim().orEmpty().ifEmpty { "0.0.0.0" }
    }
    fun copyAddr(addr: String) {
        clipboard.setText(AnnotatedString(addr))
        android.widget.Toast.makeText(context, "Copied: $addr", android.widget.Toast.LENGTH_SHORT)
            .show()
    }
    fun toast(msg: String) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
    }

    Scaffold(
        bottomBar = { if (!pro) AdBanner() },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 600.dp
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Header(anyOn = anyOn)
                    if (showPrivacy) {
                        PrivacyDialog(onDismiss = { showPrivacy = false })
                    }
                    if (showDonate) {
                        DonateDialog(
                            onDismiss = { showDonate = false },
                            onPick = { productId ->
                                showDonate = false
                                if (activity != null) {
                                    BillingManager.donate(activity, productId, ::toast)
                                } else {
                                    toast("Purchase unavailable right now.")
                                }
                            },
                        )
                    }

                    if (wide) {
                        // Tablet / landscape: two balanced columns.
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Ipv4Card(ips)
                                ProxyCard(
                                    primaryIp = primaryIp,
                                    running = state.running,
                                    onCopy = ::copyAddr,
                                )
                            }
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                if (anyOn) {
                                    TrafficCard(
                                        rxRate = state.rxRate, txRate = state.txRate,
                                        rxTotal = state.rxTotal, txTotal = state.txTotal,
                                    )
                                    ChartCard(history = state.history)
                                } else {
                                    IdleCard()
                                }
                            }
                        }
                    } else {
                        Ipv4Card(ips)
                        ProxyCard(
                            primaryIp = primaryIp,
                            running = state.running,
                            onCopy = ::copyAddr,
                        )
                        if (anyOn) {
                            TrafficCard(
                                rxRate = state.rxRate, txRate = state.txRate,
                                rxTotal = state.rxTotal, txTotal = state.txTotal,
                            )
                            ChartCard(history = state.history)
                        }
                    }

                    StatusLine(anyOn = anyOn, running = state.running)
                    if (connections.isNotEmpty()) {
                        ConnectionLogCard(connections)
                    }
                    Text(
                        "Free local proxy for testing & development.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        TextButton(onClick = { showPrivacy = true }) {
                            Text("Privacy Policy", fontSize = 12.sp)
                        }
                        TextButton(onClick = { showDonate = true }) {
                            Text("Support", fontSize = 12.sp)
                        }
                        if (!pro) {
                            TextButton(
                                onClick = {
                                    if (activity != null) {
                                        BillingManager.buyRemoveAds(activity, ::toast)
                                    } else {
                                        toast("Purchase unavailable right now.")
                                    }
                                }
                            ) {
                                Text("Remove ads", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(anyOn: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_app),
            contentDescription = "TuProxy logo",
            modifier = Modifier.size(46.dp).clip(CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("TuProxy", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "Simple Proxy Server",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (anyOn) "RUNNING" else "STOPPED",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (anyOn) Green else Color.Gray,
            modifier = Modifier
                .background(
                    (if (anyOn) Green else Color.Gray).copy(alpha = 0.15f),
                    shape = CircleShape,
                )
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun Ipv4Card(ips: List<String>) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
        Column(Modifier.padding(12.dp)) {
            SectionTitle("IPv4 addresses")
            if (ips.isEmpty()) {
                Text("Detecting…", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            } else {
                ips.forEach { ip ->
                    Text(ip, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            Text(
                "Host: 0.0.0.0 · no password",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProxyCard(
    primaryIp: String,
    running: Set<String>,
    onCopy: (String) -> Unit,
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionTitle("Proxy servers")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("All", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Switch(
                    checked = running.size == 3,
                    onCheckedChange = { on ->
                        listOf(
                            ProxyEngine.TYPE_HTTP,
                            ProxyEngine.TYPE_HTTPS,
                            ProxyEngine.TYPE_SOCKS,
                        ).forEach { ProxyService.update(context, it, on) }
                    },
                )
            }
            ProxyRow(
                label = "HTTP",
                port = ProxyEngine.HTTP_PORT,
                checked = ProxyEngine.TYPE_HTTP in running,
                addr = if (ProxyEngine.TYPE_HTTP in running) "$primaryIp:${ProxyEngine.HTTP_PORT}" else null,
                onToggle = { ProxyService.update(context, ProxyEngine.TYPE_HTTP, it) },
                onCopy = onCopy,
            )
            ProxyRow(
                label = "HTTPS",
                port = ProxyEngine.HTTPS_PORT,
                checked = ProxyEngine.TYPE_HTTPS in running,
                addr = if (ProxyEngine.TYPE_HTTPS in running) "$primaryIp:${ProxyEngine.HTTPS_PORT}" else null,
                onToggle = { ProxyService.update(context, ProxyEngine.TYPE_HTTPS, it) },
                onCopy = onCopy,
            )
            ProxyRow(
                label = "SOCKS5",
                port = ProxyEngine.SOCKS_PORT,
                checked = ProxyEngine.TYPE_SOCKS in running,
                addr = if (ProxyEngine.TYPE_SOCKS in running) "$primaryIp:${ProxyEngine.SOCKS_PORT}" else null,
                onToggle = { ProxyService.update(context, ProxyEngine.TYPE_SOCKS, it) },
                onCopy = onCopy,
            )
        }
    }
}

@Composable
private fun TrafficCard(rxRate: Long, txRate: Long, rxTotal: Long, txTotal: Long) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            SectionTitle("Traffic in / out")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Meter("↓ IN", "${formatBytes(rxRate)}/s", Green)
                Meter("↑ OUT", "${formatBytes(txRate)}/s", Red)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Total ↓ ${formatBytes(rxTotal)} · ↑ ${formatBytes(txTotal)}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChartCard(history: List<Pair<Long, Long>>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            SectionTitle("Per-second chart")
            TrafficChart(history)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Legend(Green, "In")
                Spacer(Modifier.width(12.dp))
                Legend(Red, "Out")
                Spacer(Modifier.weight(1f))
                Text(
                    if (history.isNotEmpty()) "live" else "waiting for traffic…",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun IdleCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionTitle("Status")
            Text(
                "All proxies are off.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "Turn on a toggle above to start serving. A status notification will keep you informed.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ConnectionLogCard(connections: List<ConnEntry>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle("Recent connections")
                TextButton(onClick = { ConnectionLog.clear() }) {
                    Text("Clear", fontSize = 12.sp)
                }
            }
            connections.take(12).forEach { e ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusDot(
                        when {
                            e.failed -> Red
                            e.done -> Color.Gray
                            else -> Green
                        }
                    )
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${e.type.uppercase()}  ${e.target}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "↓ ${formatBytes(e.rx)} · ↑ ${formatBytes(e.tx)} · ${formatTime(e.startMs)}" +
                                if (e.failed) " · failed" else if (e.done) "" else " · live",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    return try {
        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date(ms))
    } catch (_: Exception) {
        ""
    }
}

@Composable
private fun PrivacyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Privacy Policy", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "TuProxy relays network traffic locally on your device and " +
                    "collects no personal data, with no accounts and no analytics of its own.\n\n" +
                    "Ads (AdMob by Google) may collect the advertising ID and device " +
                    "info to serve and measure ads.\n\n" +
                    "The local proxy sees the hosts you connect to while it is ON; " +
                    "nothing is uploaded anywhere. Turn all toggles off to stop serving.\n\n" +
                    "Full text: PRIVACY_POLICY.md in the project repo and the Play listing.",
                fontSize = 13.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun DonateDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Support TuProxy", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "TuProxy is free. A one-time tip keeps it alive — you can tip again anytime.",
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(4.dp))
                DonateTierButton("☕ Small tip", BillingManager.PRODUCT_DONATE_SMALL, onPick)
                DonateTierButton("🍱 Medium tip", BillingManager.PRODUCT_DONATE_MEDIUM, onPick)
                DonateTierButton("🚀 Large tip", BillingManager.PRODUCT_DONATE_LARGE, onPick)
                Text(
                    "Prices are set in your local currency by Google Play.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun DonateTierButton(label: String, productId: String, onPick: (String) -> Unit) {
    TextButton(onClick = { onPick(productId) }) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatusLine(anyOn: Boolean, running: Set<String>) {    Text(
        if (anyOn) "RUNNING [${running.sorted().joinToString("+") { it.uppercase() }}]" else "STOPPED — all proxies off",
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (anyOn) Green else Color.Gray,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun ProxyRow(
    label: String,
    port: Int,
    checked: Boolean,
    addr: String?,
    onToggle: (Boolean) -> Unit,
    onCopy: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (checked) StatusDot(Green) else StatusDot(Color.Gray)
                Spacer(Modifier.width(6.dp))
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (checked) "  :$port" else "  (off)",
                    fontSize = 12.sp,
                    color = if (checked) MaterialTheme.colorScheme.primary else Color.Gray,
                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
                )
            }
            if (checked && addr != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(14.dp))
                    SelectionContainer {
                        Text(
                            addr,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = { onCopy(addr) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    ) {
                        Text("Copy", fontSize = 12.sp)
                    }
                }
            }
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(Modifier.width(8.dp).height(8.dp).background(color, shape = CircleShape))
}

@Composable
private fun Meter(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(14.dp).height(3.dp).background(color))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 11.sp)
    }
}

@Composable
private fun TrafficChart(history: List<Pair<Long, Long>>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(vertical = 4.dp)
    ) {
        val w = size.width
        val h = size.height
        val mid = h / 2f
        val pad = 4f
        // grid + center zero line
        drawLine(Color.LightGray, Offset(0f, pad), Offset(w, pad), strokeWidth = 1f)
        drawLine(Color.Gray, Offset(0f, mid), Offset(w, mid), strokeWidth = 1.5f)
        drawLine(Color.LightGray, Offset(0f, h - pad), Offset(w, h - pad), strokeWidth = 1f)
        if (history.size < 2) return@Canvas
        val maxV = (history.maxOfOrNull { maxOf(it.first, it.second) } ?: 0L)
            .coerceAtLeast(1L).toFloat()
        val half = mid - pad
        val n = history.size
        val inVals = List(HISTORY_POINTS - n) { 0f } + history.map { it.first.toFloat() }
        val outVals = List(HISTORY_POINTS - n) { 0f } + history.map { it.second.toFloat() }

        // IN: green upward + gradient fading to center line
        val inLine = Path()
        val inFill = Path()
        inVals.forEachIndexed { i, v ->
            val x = w * i / (HISTORY_POINTS - 1)
            val y = mid - (v / maxV) * half
            if (i == 0) {
                inLine.moveTo(x, y)
                inFill.moveTo(x, mid)
                inFill.lineTo(x, y)
            } else {
                inLine.lineTo(x, y)
                inFill.lineTo(x, y)
            }
        }
        inFill.lineTo(w, mid)
        inFill.close()
        drawPath(
            inFill,
            Brush.verticalGradient(
                colors = listOf(Green.copy(alpha = 0.55f), Green.copy(alpha = 0.05f)),
                startY = pad,
                endY = mid,
            ),
        )
        drawPath(inLine, Green, style = Stroke(width = 4f))

        // OUT: red downward + gradient fading to center line
        val outLine = Path()
        val outFill = Path()
        outVals.forEachIndexed { i, v ->
            val x = w * i / (HISTORY_POINTS - 1)
            val y = mid + (v / maxV) * half
            if (i == 0) {
                outLine.moveTo(x, y)
                outFill.moveTo(x, mid)
                outFill.lineTo(x, y)
            } else {
                outLine.lineTo(x, y)
                outFill.lineTo(x, y)
            }
        }
        outFill.lineTo(w, mid)
        outFill.close()
        drawPath(
            outFill,
            Brush.verticalGradient(
                colors = listOf(Red.copy(alpha = 0.05f), Red.copy(alpha = 0.55f)),
                startY = mid,
                endY = h - pad,
            ),
        )
        drawPath(outLine, Red, style = Stroke(width = 4f))
    }
}

private const val HISTORY_POINTS = 60
