# HoloVLESS

A native-Holo VLESS client for Android 4.x, based on v2rayNG and powered by
modern [Xray-core](https://github.com/XTLS/Xray-core).

## HoloVLESS modern build

The active HoloVLESS build combines the API-19 modern-core experiment with a
platform `Theme.Holo` interface. It uses Xray-core v26.3.27 and supports the
tested VLESS REALITY, XHTTP, and post-quantum profile fields. The application
manifest remains at minSdk 17, while the modern Go AAR declares minSdk 19;
therefore this modern-core build is supported on Android 4.4/API 19 and newer.

The UI uses the Android platform ActionBar, dialogs, preferences, switches,
and other Holo widgets with the standard Holo-blue accent. It does not include
AppCompat, Material Components, CardView, or a Material toolbar. The existing
application icon has been replaced with the supplied HoloVLESS artwork.
The icon is copyright (c) 2026 minako9667
(https://t.me/NeruBurningROM) and is licensed under WTFPL-2.0; see
`ARTWORK_NOTICE.md`.

The modern-core/Holo build was installed and tested on a Sony Xperia ZR C5503
running Android 4.4.2. The supplied REALITY/XHTTP profile connected, Firefox
loaded `https://myip.wtf`, and the expected VPN ISP was reported. The legacy
baseline was also manually verified by the user on a Samsung Galaxy S5; the
combined modern-core/Holo build has not yet received a separate Samsung test.

See `docs/milestone6-modern-xray-api19.md` for the modern-core work and
`docs/holovless-holo-ui.md` for the Holo redesign and verification record.

HoloVLESS retains the upstream GNU General Public License version 3; see
`LICENSE`. It is not relicensed.

Public APK downloads must be accompanied by equivalent access to the matching
source archive at no additional charge. Modification dates, corresponding
source instructions, third-party notices, and retained dependency licenses are
documented in `NOTICE.md`, `THIRD_PARTY_NOTICES.md`, and
`third_party_licenses/`.

## Holo legacy fork baseline

This fork is based on upstream tag `1.6.30`, commit
`e9b1052ef78a71506d6b98f9099980b45e0ac15c`. It was selected because it is
the numerically newest stable `1.6.x` tag and its checked-in
`V2rayNG/app/build.gradle` declares `minSdkVersion 17`. Upstream tags
`1.6.29` and `1.6.30` point to the same commit.

The baseline compiles and targets Android API 30 with build-tools 30.0.2.
Its bundled `libv2ray.aar` manifest declares API 15 and the corresponding Go
module pins Xray-core 1.5.2. The AAR's Go JNI ELF note records API 16 and
Android NDK r21. The checked-in tun2socks build script specifies
`APP_PLATFORM=android-19`, matching the bundled tun2socks ELF note, which
records API 19 and Android NDK r22b. These native API-floor signals conflict,
so API 16 support is not claimed from manifest metadata alone. API 17 remains
the supported floor pending the separate API 16 build, imported-symbol, and
runtime experiment after the API 17 baseline has passed.

This fork retains the upstream GNU General Public License version 3; see
`LICENSE`. It is not relicensed.

### API 17 baseline build and device status

The command-line baseline build runs on Debian 13 with the following pinned
toolchain:

- Eclipse Temurin JDK 11.0.32+9
- Gradle 6.7.1 and Android Gradle Plugin 4.2.2
- Kotlin Gradle plugin 1.5.10 (Gradle embeds Kotlin 1.3.72)
- Android SDK command-line tools 12.0
- Android platform 30 revision 3 and build-tools 30.0.2
- Android NDK 22.1.7171670 (installed for parity with the bundled tun2socks
  binary; the baseline build uses the inherited binaries and does not invoke
  the NDK)

The remote source is cloned directly from upstream tag `1.6.30`. Tooling-only
files are copied from the local working tree to the server with PuTTY `pscp`,
and built APKs are copied back the same way. The reproducible baseline command
is:

```sh
env JAVA_HOME=/home/nyash/v2rayNG-toolchains/jdk11 \
    ANDROID_SDK_ROOT=/home/nyash/Android/Sdk \
    bash ./gradlew clean assembleDebug --no-daemon
```

The original JCenter endpoint no longer resolves MMKV 1.2.7 and RxPermissions
0.9.4 reliably. An Aliyun JCenter mirror is therefore included as a build
repository without changing either dependency. The retrieved AARs were
cross-checked against Huawei's mirror and matched exactly:

- MMKV 1.2.7 SHA-256:
  `470975a977e8a072839f9f7c4fa9b891ece7910723d96bab2662ee23e2bbae4a`
- RxPermissions 0.9.4 SHA-256:
  `074d485acf74d6a484471b5630d4b5d4551fb784d982e223078b7f2400c9e871`

The resulting `armeabi-v7a` debug APK has SHA-256
`9151530000f1ecc00c5e53470d8cd8c5a9f75ed063112d0b2c8eedeb49514031`,
declares minSdk 17/targetSdk 30, and installs and launches on a Sony Xperia ZR
C5503 running Android 4.4.2 (API 19). The bundled MMKV and Go JNI libraries
load there.

End-to-end VLESS verification passed on the Xperia using the replacement
legacy-compatible VLESS-over-TCP profile. The app's local-DNS option and an
explicit remote DoH value were used while diagnosing an initial DNS timeout, but
a subsequent retest worked without either setting, so they are not required for
the verified connection. Firefox then loaded `https://myip.wtf`, which reported
the expected ISP, Senko Digital LLC. The same build and profile were also
manually verified by the user on a Samsung Galaxy S5. The initial profile remains
unsupported because Xray-core 1.5.2 does not recognize its newer `xhttp`
transport and REALITY/PQ parameters; the inherited core was not upgraded for the
baseline.

[![API](https://img.shields.io/badge/API-17%2B-yellow.svg?style=flat)](https://developer.android.com/about/versions/jelly-bean#android-4.2)
[![Kotlin Version](https://img.shields.io/badge/Kotlin-1.5.10-blue.svg)](https://kotlinlang.org)
[![GitHub commit activity](https://img.shields.io/github/commit-activity/m/2dust/v2rayNG)](https://github.com/2dust/v2rayNG/commits/master)
[![CodeFactor](https://www.codefactor.io/repository/github/2dust/v2rayng/badge)](https://www.codefactor.io/repository/github/2dust/v2rayng)
[![GitHub Releases](https://img.shields.io/github/downloads/2dust/v2rayNG/latest/total?logo=github)](https://github.com/2dust/v2rayNG/releases)
[![Chat on Telegram](https://img.shields.io/badge/Chat%20on-Telegram-brightgreen.svg)](https://t.me/v2rayn)

<a href="https://play.google.com/store/apps/details?id=com.v2ray.ang">
<img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" width="165" height="64" />
</a>

### Usage

#### Geoip and Geosite
v2rayNG release already embedded domain file `geoip.dat` and `geosite.dat`. However it is (probably) not the latest and not the most complete list.
For power user, the embedded files can be easily replaced with the following steps:
1. Launch v2rayNG (v1.4.9+)
2. Find existing geoip.dat and geosite.dat in `Android/data/com.v2ray.ang/files/assets` (path may differ on some Android device)
3. Replace them with the latest [domain list](https://github.com/v2fly/domain-list-community) and [ip list](https://github.com/v2fly/geoip)
4. Enhanced version can be found in this [repo](https://github.com/Loyalsoldier/v2ray-rules-dat) (recommend to use `geosite:geolocation-!cn` for proxy dns and routing)
5. It is also possible to use third party dat file in the same folder, like [h2y](https://guide.v2fly.org/routing/sitedata.html#%E5%A4%96%E7%BD%AE%E7%9A%84%E5%9F%9F%E5%90%8D%E6%96%87%E4%BB%B6)

#### See more in our [wiki](https://github.com/2dust/v2rayNG/wiki)

### Development guide

Android project under V2rayNG folder can be compiled directly in Android Studio, or using Gradle wrapper. But the v2ray core inside the aar is (probably) outdated.  
The aar can be compiled from the Golang project under AndroidLibV2rayLite folder. For a quick start, read guide for [Go Mobile](https://github.com/golang/go/wiki/Mobile)
and [Makefiles for Go Developers](https://tutorialedge.net/golang/makefiles-for-go-developers/)

v2rayNG can run on Android Emulators, with minimum Android 5.0
