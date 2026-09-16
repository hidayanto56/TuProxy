# AGENT.md — TuProxy

Single-page Android app (minSdk 28 / Android 9) yang menyalakan proxy
HTTP, HTTPS (CONNECT), dan SOCKS5 langsung di perangkat. Host `0.0.0.0`,
tanpa auth. Satu halaman berisi: daftar IPv4, 3 toggle + port, meter
traffic In/Out, grafik per-detik, berjalan di background via Foreground
Service + notifikasi live.

## Struktur

```
settings.gradle.kts            # repo Gradle (google + mavenCentral)
build.gradle.kts               # AGP 8.5.2 + Kotlin 1.9.24 (apply false)
app/build.gradle.kts           # minSdk 28, target/compile 34, Compose BOM
app/src/main/AndroidManifest.xml
app/src/main/java/com/example/tuproxy/
  MainActivity.kt              # setContent { ProxyDashboard() }
  ui/ProxyDashboard.kt         # satu-satunya layar (toggle, meter, grafik Canvas)
  services/ProxyService.kt     # ForegroundService, ticker 1s, notifikasi live,
                               # uiState: StateFlow<ProxyUiState>
  engine/ProxyEngine.kt        # ServerSocket 0.0.0.0: HTTP 8080, HTTPS 8443,
                               # SOCKS5 1080; HTTP CONNECT tunnel; SOCKS5 RFC1928
                               # no-auth (CMD CONNECT saja); thread pool daemon
  engine/TrafficStats.kt       # counter global: rx = dari klien (IN),
                               # tx = ke klien (OUT)
  utils/IPUtils.kt             # semua IPv4 non-loopback (Wi-Fi/LAN/Tailscale/…)
  utils/Format.kt              # formatBytes()
```

## Aturan main

- Engine = `java.net` murni (ServerSocket). JANGAN pakai Ktor/Netty di
  Android (berat, boros baterai, rawan OOM/kill).
- Service hidup hanya jika ≥1 proxy ON; semua OFF → `stopSelf()`,
  `TrafficStats.reset()`, `uiState` kembali nol (meter + grafik reset).
- Grafik/meter hanya tick saat ada proxy ON (ticker milik Service).
- Notifikasi: `↓ rx/s ↑ tx/s`, update 1×/detik, `setOnlyAlertOnce(true)`.
- `foregroundServiceType="dataSync"` + permission
  `FOREGROUND_SERVICE_DATA_SYNC` (wajib untuk targetSdk 34).
- UI baca state via `ProxyService.uiState` (satu proses, tanpa Binder/IPC).
  Aksi toggle via `ProxyService.update(ctx, type, enabled)` (intent
  `ACTION_UPDATE` + `startForegroundService`).
- Ikon launcher adaptif bawaan (`res/mipmap-anydpi-v26/` + vektor), tanpa
  dependensi asset eksternal.

## Build

```bash
./build.sh           # debug (default) → app/build/outputs/apk/debug/app-debug.apk
./build.sh release   # release tanpa minify
./build.sh install   # install ke device (adb)
./build.sh clean     # bersihkan build
# atau langsung: ./gradlew :app:assembleDebug
```

`build.sh` mengecek JDK 17+, membuat `local.properties` dari
`ANDROID_HOME` bila belum ada, lalu memanggil `gradlew`.

Butuh: JDK 17+, Android SDK (platform 34, build-tools), `sdk.dir` di
`local.properties` atau env `ANDROID_HOME`.

## Uji cepat di HP

1. Install, nyalakan toggle HTTP.
2. Dari laptop satu Wi-Fi: `curl -x http://<IP-HP>:8080 http://example.com -v`
3. SOCKS: `curl --socks5-hostname <IP-HP>:1080 http://example.com -v`
4. Meter + grafik naik; notifikasi update tiap detik; matikan semua
   toggle → notifikasi hilang, meter/grafik nol.
