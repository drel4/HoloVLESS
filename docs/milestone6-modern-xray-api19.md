# Milestone 6: modern Xray-core API 19 experiment

Status: stopped after the first real-device runtime failure, as required by the
Milestone 6 fallback rule. The Phase 2 baseline remains the supported build.

## Inputs and isolated branch

- Branch: `experiment/modern-xray-api19`
- Xray release: `v26.3.27` (Go module alias `v1.260327.0`, commit
  `d2758a023cd7f4174a5a5fa4ff66e487d4342ba0`)
- Go: 1.26.5, installed under the remote user's home directory
- gomobile: `v0.0.0-20260803200217-62cee1672c8e`
- Android SDK: platform 30 and build-tools 30.0.2
- Android NDK: 22.1.7171670
- AAR target: `android/arm`, API 19
- Java binding compiler: the baseline Temurin JDK 11

The current Xray `SystemDialer` interface adds `DestIpAddress`. The wrapper's
implementation returns its already resolved and cached proxy-server address.
No app-facing Java binding API was changed.

## Build results

The wrapper packages compile with Go 1.26.5. The inherited network test has a
pre-existing panic because its fixture does not initialize `resolveChan`, so the
compile-only check was:

```sh
go test -run '^$' ./...
```

The successful AAR bind used:

```sh
gomobile bind -v -target=android/arm -androidapi 19 \
  -o libv2ray-api19.aar github.com/2dust/AndroidLibV2rayLite
```

JDK 11 must be first on `PATH` during binding. Classes emitted by the server's
default JDK 21 triggered a null-pointer failure in the baseline AGP 4.2.2 D8
version while dexing `go/Seq$GoRefQueue$1.class`. Rebinding with JDK 11 avoided
that tooling incompatibility.

The AAR declares minSdk 19 and contains only
`jni/armeabi-v7a/libgojni.so`. Its SHA-256 is
`7f5d6f7fcc90e67f1c02219c5ff177a9979042ec66477d869ba79bafadc14ed9`.
The experimental app retains minSdk 17 and uses a branch-local manifest merger
override for this API-19-only device test.

The resulting `armeabi-v7a` debug APK has SHA-256
`3e33a0eff87460f56860edc5502a353b65b65a2b7a957dd3fc574a4060256ef2`.

## Xperia result

The APK installed and launched on the Sony Xperia ZR C5503 running Android
4.4.2/API 19. Dalvik loaded the new `libgojni.so`, demonstrating that the
modern Go runtime reached application startup on this ARM32/API 19 device.

The original modern profile was explicitly selected, replacing the previously
selected legacy TCP test profile. Starting the VPN reached current Xray's JSON
loader, which recognized the `xhttp` network and `reality` security values but
stopped with this error:

```text
infra/conf: REALITY: Empty "realitySettings".
```

Therefore the legacy app's imported profile does not generate the Reality
settings required by the current core. The connection never reached the
`myip.wtf` verification step. Per the milestone instruction, no further attempt
was made to extend or force the legacy profile generator; the experiment was
stopped and the working Phase 2 baseline was restored.
