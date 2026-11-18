# HoloVLESS third-party notices

This file accompanies the HoloVLESS source and binary distributions. It is a
notice inventory, not a replacement for the complete license texts stored in
`LICENSE` and `third_party_licenses/`.

## Upstream application

- v2rayNG, upstream base commit `e9b1052ef78a71506d6b98f9099980b45e0ac15c`
  (tag 1.6.30), GPL-3.0. Copyright remains with the upstream contributors.

## Modern Go networking core

- AndroidLibV2rayLite wrapper, modified by HoloVLESS, GPL-3.0 as part of this
  work.
- XTLS/Xray-core `v1.260327.1-0.20261109052709-676eaf66f8f0`
  (Xray v26.11.9, commit `676eaf66f8f05d4d558ee7a74b4736623d3feeb9`), MPL-2.0.
- XTLS/reality, MPL-2.0 and BSD-3-Clause portions.
- Other statically linked Go modules are listed with their exact versions in
  `third_party_licenses/licenses-go-1.1/GO-MODULES.txt`. License, notice, and patent files
  copied from every module used by the Android/ARM build are retained beside
  that list.

The Xray copyleft components are distributed as part of the
GPL-3.0 HoloVLESS work without restricting access to their source. Their exact
source versions are retrievable through the checked-in `go.mod` and `go.sum`.

## Routing datasets

The unmodified `geoip.dat` and `geosite.dat` assets are from
Loyalsoldier/v2ray-rules-dat release `202611172204`:
https://github.com/Loyalsoldier/v2ray-rules-dat/releases/tag/202611172204
The upstream GPL-3.0 license and README with source-data credits are retained
in `third_party_licenses/geo/`. This includes attribution to MaxMind GeoLite2,
v2fly/domain-list-community, and the additional lists identified upstream.
The corresponding generation source and hashes are recorded in
`docs/releases/1.1.md`. The older Go inventory is retained for historical builds.

## Native tun2socks

- XTLS/BadVPN commit `e89e09098c63059d31ab5eff4edc6fa2fc76c716`,
  BSD-3-Clause. Copyright (c) 2009 Ambroz Bizjak.
- libancillary 0.9.1, BSD-3-Clause. Copyright Nicolas George.

The exact source archives are bundled under
`AndroidLibV2rayLite/third_party_sources/`. Their license texts are also in
`third_party_licenses/native/`.

## Android and JVM libraries

The following shipped dependency families are licensed under Apache-2.0:

- AndroidX Activity, Annotation, Arch Core, Collection, CoordinatorLayout,
  Core, CursorAdapter, CustomView, Data Binding View Binding, DocumentFile,
  DrawerLayout, Fragment, Interpolator, Legacy Support, Lifecycle, Loader,
  LocalBroadcastManager, Media, Multidex, Print, RecyclerView, SavedState,
  SlidingPaneLayout, SwipeRefreshLayout, Tracing, VersionedParcelable,
  ViewPager, and ViewPager2.
- Kotlin standard library, reflection, Android extensions runtime, and
  kotlinx.coroutines.
- Gson 2.8.6.
- RxJava 1.3.4 and RxAndroid 1.2.1.
- RxPermissions 0.9.4.
- Barcode Scanner 1.9.8 and ZXing Core 3.3.0.
- ToastCompat 1.1.0.

A complete Apache-2.0 text is retained as
`third_party_licenses/Apache-2.0.txt`. Copyright headers already present in
copied Android Open Source Project and helper source files remain intact.

MMKV 1.2.7 is distributed under its BSD-3-Clause license and includes
OpenSSL/SSLeay, Apache-2.0, zlib, and pybind11 notices. Tencent's complete
1.2.7 license and notice text is retained at
`third_party_licenses/android/MMKV-1.2.7-LICENSE.txt`.

JUnit is used only for tests and is not packaged in the APK.

## Launcher artwork

This release restores the original v2rayNG launcher artwork from the upstream
GPL-3.0 source. No separately licensed HoloVLESS artwork is included.

## License locations

- HoloVLESS/v2rayNG GPL-3.0: `LICENSE`
- Current Go module licenses: `third_party_licenses/licenses-go-1.1/`
- Routing dataset license and credits: `third_party_licenses/geo/`
- MPL-2.0 convenience copy: `third_party_licenses/MPL-2.0.txt`
- Apache-2.0 convenience copy: `third_party_licenses/Apache-2.0.txt`
- Native BSD notices: `third_party_licenses/native/`
- MMKV complete notice: `third_party_licenses/android/`
