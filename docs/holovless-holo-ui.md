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
- Icon: original GPL-3.0 v2rayNG launcher artwork for the interim release
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
When subscriptions exist, native Holo ActionBar tabs filter the list into
ungrouped configurations and one tab per subscription domain. The tab strip is
hidden when there are no subscriptions; subscription management and updating
remain available from the overflow menu.
The last selected group is persisted and restored at startup. Full profile
metadata is decoded only for that group; opening another group decodes it once
and keeps it cached for later switches. A lightweight persisted group index
avoids loading every profile just to filter the list. Existing installations
populate this index once after upgrading, while new imports update it
immediately. Switching tabs never requests the subscription again.
The overflow “Update subscription” action is hidden on the ungrouped tab and
targets only the subscription represented by the selected group tab.
The bottom status and action controls are one flat edge-to-edge Holo footer:
tap while stopped to connect, tap while running to test the connection, and
hold while running to disconnect.

Server rows use the compact current-v2rayNG information hierarchy shown in the
provided reference: name, endpoint, then protocol at the far left and latency
at the far right on one line. Feedback and promotion entries, including the
old promotional list footer, were removed. The VLESS editor now opens safely,
recognizes XHTTP and REALITY, and retains existing REALITY key material when
saving.

The content window uses a visible inverted dark Holo gradient while the footer
uses a flat solid surface. The ActionBar subtitle contains only the application
version, and a complete Russian resource set covers all 160 English string and
string-array entries. A native overflow-menu entry displays the GPL source
offer, warranty disclaimer, and third-party license summary; the complete
notices and retained license texts ship in the matching source archive.
Per-app proxy enablement, allow/bypass mode, the selected package set, and
custom user routes are now written directly to the same MMKV settings store
read by the VPN service and configuration generator. Per-app filtering remains
an Android 5.0+ feature because the required platform VPN APIs are unavailable
on Android 4.x, and the settings description states this requirement. Custom
route entries use readable dark text on their platform editor surface; the
screen and save confirmation both instruct users to reconnect the VPN after
changing routes.

Version 1.0.4 adds `hysteria2://` and `hy2://` subscription/clipboard import
using the bundled Xray core's native Hysteria2 implementation. Generated
outbounds support authentication, TLS/SNI and ALPN, insecure mode, certificate
pinning, Salamander obfuscation, port hopping, and optional Brutal bandwidth
parameters. Hysteria2 profiles can be selected, connected, tested, shared, and
removed. The legacy generic profile form cannot safely represent all Hysteria2
fields, so editing directs the user to re-import an updated link.

Source and runtime-classpath audits found no AppCompat, Material Components,
CardView, FABProgressCircle, or Blacksquircle editor dependency. AndroidX
Fragment 1.3.6 remains because the existing routing fragments use the Activity
Result API; it does not pull AppCompat.

## Verification

The remote build passed:

```text
./gradlew clean testDebugUnitTest assembleDebug --no-daemon
BUILD SUCCESSFUL
```

The current `armeabi-v7a` debug APK is
`artifacts/release/HoloVLESS-1.0.4-api19-armeabi-v7a-debug.apk` in the
workspace and has SHA-256
`2767698675cb79e23468f0e6bfd73f3cd20aa353ec7d1dbd2e6377418a1035cf`.

The settings-persistence, lazy-group-cache, Hysteria2, and latest settings-UI
updates were clean-built on 2026-09-10, but have not received a new device run
because live testing was not available. Hysteria2 link parsing and JSON
generation are covered by unit tests, but live connectivity remains
runtime-unverified. The on-device results below describe the preceding 1.0.3
build.

On the Sony Xperia ZR C5503 (Android 4.4.2/API 19), the APK installed and
launched with OEM-rendered native Holo controls. The exact supplied modern
REALITY/XHTTP/PQ profile was selected, Xray and tun2socks stayed running,
Firefox loaded `https://myip.wtf`, and the expected ISP `Senko Digital LLC`
was shown. Tap-to-connect, tap-to-test, and hold-to-disconnect were
each exercised on the footer, and the VLESS editor opened without a runtime
exception. The subscription editor and per-app selector use dark native Holo
form surfaces. A supplied HTTPS subscription with a UTF-8 BOM was downloaded,
decoded, imported, and retained after an app restart on KitKat. Unsupported
protocol lines were ignored while supported VLESS and Trojan entries were
imported. The supplied subscription produced a domain-named group tab, while
the configurations tab retained only the two ungrouped profiles. The overflow
menu exposes separate endpoint ping and routed internet-check actions, and a
long press exposes the same actions for one profile. Internet checks request
`myip.wtf` through each profile and enforce a hard timeout before restoring the
previous selection and VPN state. Local DNS remained off, Remote DNS remained blank, and
the separate stock VPN DNS field remained `1.1.1.1`.

The generated proxy configuration remains excluded from logs. The provided
profile is not committed to the repository or reproduced in this document.

Screenshots are retained under the workspace's `artifacts/holo` directory,
including the main list, platform settings, import menu, connected state,
browser ISP result, compact rows, Russian UI, and Holo gradient.
