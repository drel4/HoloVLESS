# HoloVLESS Holo UI milestone

Status: native-Holo UI and the API-19 modern-core integration are implemented.

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

Version 1.0.5 moves subscription parsing/persistence and selected-group decoding
to background workers. Imports stage valid profiles and publish the ID list
once per batch, removing the per-profile full-list rewrite. Empty or unusable
responses preserve existing profiles; BOM and whitespace are normalized
together. Group loads are cancellable so an older result cannot overwrite a
newer tab selection, and reordering merges with the latest stored IDs.

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
`artifacts/release/HoloVLESS-1.0.5-api19-armeabi-v7a-debug.apk` in the
workspace and has SHA-256
`6099e711b5a57d114c5d85d570e4a810a348262b989d6298ca220f41f92fc9f4`.

Version 1.0.5 was clean-built on 2026-10-14 from a Git source snapshot using
JDK 11.0.32+9, Gradle 6.7.1, AGP 4.2.2, Android platform 30 revision 3, and
build-tools 30.0.2. All 12 JVM tests passed, including a synthetic 5,000-profile
batch with one save callback, preserved order/group affiliation, mixed
VLESS/Hysteria2 input, and empty/unsupported-response preservation. The
5,000-profile parser test took 0.48 seconds on the server; this is not a phone
performance benchmark or native MMKV persistence measurement. No new live
device verification was available.

The server paths have moved: the current JDK is at
`/home/nyash/0drel/v2rayNG-toolchains/jdk11` and SDK at
`/home/nyash/android-sdk`. The clean source-snapshot build ran in
`/home/nyash/0drel/holovless-build-1.0.5/V2rayNG` with:

```sh
env JAVA_HOME=/home/nyash/0drel/v2rayNG-toolchains/jdk11 \
    ANDROID_SDK_ROOT=/home/nyash/android-sdk \
    ANDROID_HOME=/home/nyash/android-sdk \
    bash ./gradlew -Dorg.gradle.jvmargs=-Xmx2048m clean testDebugUnitTest assembleDebug --no-daemon
```

Public releases are hosted at https://github.com/drel4/HoloVLESS/releases;
GitHub's automatic tag archives provide the matching source without a
separately uploaded source ZIP.

The overflow menu exposes separate endpoint-ping and routed internet-check
actions, and a long press exposes the same actions for one profile. Internet
checks request `myip.wtf` through the chosen profile and enforce a hard timeout
before restoring the previous selection and VPN state. Generated proxy
configurations remain excluded from logs.
