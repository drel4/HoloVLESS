# HoloVLESS Holo UI milestone

Status: the Holo redesign and modern-core combination passed its Xperia ZR
on-device gate on 2026-08-09.

## Foundation

- UI branch: `feature/holovless-holo-ui`
- Modern-core base: `56ed81a2` (`Stop logging generated proxy configurations`)
- Xray-core: v26.3.27, built for Android/ARM API 19
- App manifest minSdk: 17; the modern Go AAR raises this build's effective
  supported floor to API 19
- App name: HoloVLESS
- Icon: existing inherited icon, unchanged pending a new design
- License: inherited GPL-3.0, unchanged

## Before and after

The inherited UI used AppCompat/Material-era structure: a toolbar, navigation
drawer, floating action button, cards, Material tabs, AppCompat preferences,
and a third-party code editor. The redesigned UI uses the Android platform
`Theme.Holo` dark theme and Holo-blue (`#33B5E5`) accent, native ActionBar and
overflow menus, flat high-contrast server rows, rectangular controls, native
dialogs and preferences, platform form widgets, and a platform multiline
monospace editor.

The server list, selection, sharing, editing, deletion, connect/disconnect
status, add/import menu, settings, subscriptions, routing, per-app proxy, and
logging entry points remain available. Optional live upload/download speed is
kept in the service notification and follows its existing preference; it is no
longer duplicated on the main screen. Notification updates retain one stable
small icon instead of swapping between differently padded traffic icons.
The bottom status and action controls are one flat edge-to-edge Holo footer:
tap while stopped to connect, tap while running to test the connection, and
hold while running to disconnect.

Server rows use the compact current-v2rayNG information hierarchy shown in the
provided reference: name, endpoint, then protocol at the far left and latency
at the far right on one line. Feedback and promotion entries, including the
old promotional list footer, were removed. The VLESS editor now opens safely,
recognizes XHTTP and REALITY, and retains existing REALITY key material when
saving.

The window uses a visible dark Holo gradient, the ActionBar subtitle contains
only the application version, and a complete Russian resource set covers all
158 English string and string-array entries.

Source and runtime-classpath audits found no AppCompat, Material Components,
CardView, FABProgressCircle, or Blacksquircle editor dependency. AndroidX
Fragment 1.3.6 remains because the existing routing fragments use the Activity
Result API; it does not pull AppCompat.

## Verification

The remote build passed:

```text
./gradlew testDebugUnitTest assembleDebug --no-daemon
BUILD SUCCESSFUL
```

The final `armeabi-v7a` debug APK is
`artifacts/holo/HoloVLESS-modern-api19-debug.apk` in the workspace and has
SHA-256 `c678116559b5dfbd894f6164dd9e917018ff0dc54e8744ef4a22db5d44a6224f`.

On the Sony Xperia ZR C5503 (Android 4.4.2/API 19), the APK installed and
launched with OEM-rendered native Holo controls. The exact supplied modern
REALITY/XHTTP/PQ profile was selected, Xray and tun2socks stayed running,
Firefox loaded `https://myip.wtf`, and the expected ISP `Senko Digital LLC`
was shown. Tap-to-connect, tap-to-test, and hold-to-disconnect were
each exercised on the footer, and the VLESS editor opened without a runtime
exception. Local DNS remained off, Remote DNS remained blank, and
the separate stock VPN DNS field remained `1.1.1.1`.

The generated proxy configuration remains excluded from logs. The provided
profile is not committed to the repository or reproduced in this document.

Screenshots are retained under the workspace's `artifacts/holo` directory,
including the main list, platform settings, import menu, connected state,
browser ISP result, compact rows, Russian UI, and Holo gradient.
