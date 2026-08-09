# HoloVLESS legal and source notice

HoloVLESS is a modified version of v2rayNG. The upstream project is copyright
its respective contributors and is licensed under the GNU General Public
License version 3. HoloVLESS remains licensed as a whole under GPL-3.0; the
complete license text is in `LICENSE`.

## Modification notice

This fork was modified on 2026-08-09 and 2026-08-10. Material changes include
the Android 4.x native-Holo interface, HoloVLESS branding and launcher assets,
Russian localization, the API-19 modern Xray-core binding, VLESS
REALITY/XHTTP/PQ profile support, generated-configuration log redaction,
subscription navigation, and reproducible native dependency packaging.

HoloVLESS is provided without warranty, as described by GPL-3.0 sections 15
and 16. The HoloVLESS project does not provide a warranty on behalf of the
upstream v2rayNG, Xray-core, or third-party contributors.

## Corresponding Source

Every distributed APK must have an equivalently accessible HoloVLESS source
archive next to it at no additional charge. The source archive must identify
the same release or commit as the APK and must retain `LICENSE`, this notice,
`ARTWORK_NOTICE.md`, `THIRD_PARTY_NOTICES.md`, `third_party_licenses/`, and the
build documentation.

The preferred source for the application and its Go wrapper is contained in
this repository. `AndroidLibV2rayLite/go.mod` and `go.sum` pin the complete Go
module graph used by the modern `libv2ray.aar`; running `go mod download`
retrieves those exact module versions. The exact Android/ARM binding command
and toolchain versions are recorded in `docs/milestone6-modern-xray-api19.md`.

The tun2socks source inputs are included directly under
`AndroidLibV2rayLite/third_party_sources/`. `compile-tun2socks.sh` verifies
their SHA-256 checksums before building, and `tun2socks.mk` records the complete
native source list and Android API floor.

## Third-party components

HoloVLESS incorporates separately copyrighted components under GPL-3.0,
MPL-2.0, Apache-2.0, MIT, BSD, and other compatible open-source licenses.
Their notices and license texts are in `THIRD_PARTY_NOTICES.md` and
`third_party_licenses/`. Those component licenses continue to apply to their
respective source files and notices.

## Launcher artwork

The HoloVLESS launcher icon is copyright (c) 2026 minako9667
(https://t.me/NeruBurningROM) and is licensed separately under WTFPL-2.0. It
may be copied, modified, and redistributed under that license. The affected
assets are recorded in `ARTWORK_NOTICE.md`, and the complete license text is
in `third_party_licenses/WTFPL-2.0.txt`.
