# TuProxy — Google Play publish checklist

## 1. Replace AdMob test IDs (required)
Test IDs ship by default so the app builds and the banner never crashes:

- `app/src/main/res/values/strings.xml` → `admob_app_id`, `admob_banner_id`
- `AndroidManifest.xml` reads the App ID via `@string/admob_app_id`

Steps:
1. Create an account at https://apps.admob.com → Apps → Add app → Android → package `com.tustudio.tuproxy` (or your final package).
2. Create a **Banner** ad unit, copy the App ID (`ca-app-pub-XXXX~YYYY`) and banner unit (`ca-app-pub-XXXX/YYYY`).
3. Put them in `strings.xml`, rebuild. Verify with a real device; test IDs show "Test Ad".

Policy: banner is anchored at the very bottom (`Scaffold.bottomBar`, fixed 50dp + "Ad" label), never covers controls, collapses to a slim placeholder on load failure. No interstitial, no rewarded ads.

## 2. Final package name (do this BEFORE first upload)
Play treats the package as permanent. Current: `com.tustudio.tuproxy`.
Rename in `app/build.gradle.kts` (`applicationId`, `namespace`) + Kotlin packages + `ProxyService` actions if you change it.

## 3. Signing (required for upload)
Play accepts **AAB** signed with your upload key. Debug builds use the default debug key; do NOT upload those.

```bash
# one-time: create upload keystore (keep the .jks + passwords SECRET, never commit)
keytool -genkeypair -v -keystore ~/tuproxy-upload.jks -alias tuproxy \
  -keyalg RSA -keysize 2048 -validity 9125

# configure signing via env (no secrets in repo):
export TUPROXY_STORE_FILE=~/tuproxy-upload.jks
export TUPROXY_STORE_PASSWORD=...
export TUPROXY_KEY_ALIAS=tuproxy
export TUPROXY_KEY_PASSWORD=...
./build.sh bundle   # → app/build/outputs/bundle/release/app-release.aab
```

`app/build.gradle.kts` reads these env vars when present; without them the release build stays unsigned (fine for local testing, rejected by Play).

## 4. Build the upload artifact
```bash
./build.sh bundle    # AAB for Play (preferred)
./build.sh release   # signed APK for sideload testing
./build.sh debug     # local dev
```
Current version: `versionCode = 2`, `versionName = "1.1"`. Bump `versionCode` (+1) on every Play upload.

## 5. Multi-resolution readiness (done in code)
- Compose + `Scaffold(bottomBar)` + `BoxWithConstraints`: single column on phones (< 600dp), two columns on tablets/landscape (≥ 600dp).
- Scrollable content, `supports-screens: anyDensity + small→xlarge`, `adjustResize`, sp/dp only, no fixed-pixel layouts.
- Launcher icons generated for mdpi→xxxhdpi (`mipmap-*/ic_launcher[_round].png`) + adaptive icon (`mipmap-anydpi-v26`) from `logo-lite.png`; in-app header uses `drawable/logo_app.png`.
- Test on: small phone (360×640), standard (412×915), tablet (800×1280), landscape. Play pre-launch report will also cover this automatically.

## 6. Notifications (running vs stopped)
- Running: ongoing foreground notification `TuProxy [HTTP+…] is running` with live ↓/↑ rates + **Stop** action.
- Stopped: dismissible `TuProxy is stopped — tap to start` so status is always visible.
- Android 13+: runtime `POST_NOTIFICATIONS` prompt on first launch (`MainActivity`).

## 7. Play Console data-safety + content forms
- Data safety: declare **No data collected/shared** (proxy relays bytes locally; AdMob collects device/ad identifiers — declare AdMob / advertising ID if you keep ads).
- Content: no user-generated content, no login, target audience 13+ (network tool). Provide a privacy-policy URL if you keep AdMob (required when ads collect identifiers).
- Permissions declared: INTERNET, ACCESS_NETWORK_STATE, FOREGROUND_SERVICE(+DATA_SYNC), WAKE_LOCK, POST_NOTIFICATIONS, AD_ID. All are used and visible in the listing; foreground-service type `dataSync` matches the proxy use.

## 8. Store listing assets (create in Play Console)
- Icon: `logo-lite.png` (icon-only, no text — best at small sizes). `logo-full.png` works as feature-graphic reference, not as icon.
- Screenshots: phone (min 2), 7-inch + 10-inch tablet recommended, landscape one recommended.
- Feature graphic 1024×500, short + full description in English.

## 9. Pre-upload sanity
- [ ] Real AdMob IDs in, test ad label gone on release
- [ ] `versionCode` bumped
- [ ] `./build.sh bundle` succeeds, install check on Android 9 + 14
- [ ] Toggles, copy buttons, chart, rotation, dark theme, notification Stop action verified
- [ ] QS tile: add via notification shade edit, toggle on/off, state follows service
- [ ] Connection log populates during real proxy use; Clear works
- [ ] In-App Review triggers on 3rd/10th/25th open (Play quota applies)
- [ ] Remove-ads product `tuproxy_remove_ads` created in Play Console if selling Pro
- [ ] Privacy policy URL live; link it in Console + data-safety form
- [ ] No secrets in repo (`grep -r "sk-or" --exclude-dir=.git .` empty, `.env` untracked)
