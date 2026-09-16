package com.example.tuproxy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tuproxy.engine.ProxyEngine
import com.example.tuproxy.services.ProxyService
import com.example.tuproxy.utils.IPUtils
import com.example.tuproxy.utils.formatBytes

private val Green = Color(0xFF3FB950)
private val Red = Color(0xFFF85149)

@Composable
fun ProxyDashboard() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val state by ProxyService.uiState.collectAsState()
    var ips by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        ips = IPUtils.getAvailableIPv4Addresses(context)
    }

    val anyOn = state.running.isNotEmpty()
    val primaryIp = remember(ips) {
        ips.firstOrNull()?.substringBefore(" ")?.trim().orEmpty().ifEmpty { "0.0.0.0" }
    }
    fun copyAddr(addr: String) {
        clipboard.setText(AnnotatedString(addr))
        android.widget.Toast.makeText(context, "Disalin: $addr", android.widget.Toast.LENGTH_SHORT)
            .show()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Text("TuProxy", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
            Column(Modifier.padding(12.dp)) {
                SectionTitle("IPv4")
                if (ips.isEmpty()) {
                    Text("Mendeteksi…", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                } else {
                    ips.forEach { ip ->
                        Text(ip, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Text(
                    "Host: 0.0.0.0 · tanpa password",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionTitle("Proxy")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Semua", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = state.running.size == 3,
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
                    checked = ProxyEngine.TYPE_HTTP in state.running,
                    addr = if (ProxyEngine.TYPE_HTTP in state.running) "$primaryIp:${ProxyEngine.HTTP_PORT}" else null,
                    onToggle = { ProxyService.update(context, ProxyEngine.TYPE_HTTP, it) },
                    onCopy = ::copyAddr,
                )
                ProxyRow(
                    label = "HTTPS",
                    port = ProxyEngine.HTTPS_PORT,
                    checked = ProxyEngine.TYPE_HTTPS in state.running,
                    addr = if (ProxyEngine.TYPE_HTTPS in state.running) "$primaryIp:${ProxyEngine.HTTPS_PORT}" else null,
                    onToggle = { ProxyService.update(context, ProxyEngine.TYPE_HTTPS, it) },
                    onCopy = ::copyAddr,
                )
                ProxyRow(
                    label = "SOCKS5",
                    port = ProxyEngine.SOCKS_PORT,
                    checked = ProxyEngine.TYPE_SOCKS in state.running,
                    addr = if (ProxyEngine.TYPE_SOCKS in state.running) "$primaryIp:${ProxyEngine.SOCKS_PORT}" else null,
                    onToggle = { ProxyService.update(context, ProxyEngine.TYPE_SOCKS, it) },
                    onCopy = ::copyAddr,
                )
            }
        }

        if (anyOn) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    SectionTitle("Traffic In / Out")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Meter("↓ IN", "${formatBytes(state.rxRate)}/s", Green)
                        Meter("↑ OUT", "${formatBytes(state.txRate)}/s", Red)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Total ↓ ${formatBytes(state.rxTotal)} · ↑ ${formatBytes(state.txTotal)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    SectionTitle("Grafik per detik")
                    TrafficChart(state.history)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Legend(Green, "In")
                        Spacer(Modifier.width(12.dp))
                        Legend(Red, "Out")
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (state.history.isNotEmpty()) "live" else "menunggu trafik…",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Text(
            if (anyOn) "RUNNING [${state.running.sorted().joinToString("+") { it.uppercase() }}]" else "STOPPED",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (anyOn) Green else Color.Gray,
        )
        }
    }
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
        // grid + garis nol tengah
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

        // IN: hijau ke atas + gradient memudar ke garis tengah
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

        // OUT: merah ke bawah + gradient memudar ke garis tengah
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
