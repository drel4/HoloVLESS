#!/bin/bash

set -o errexit
set -o pipefail
set -o nounset

# Set magic variables for current file & dir
__dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
__file="${__dir}/$(basename "${BASH_SOURCE[0]}")"
__base="$(basename ${__file} .sh)"

if [[ ! -d $NDK_HOME ]]; then
	echo "Android NDK: NDK_HOME not found. please set env \$NDK_HOME"
	exit 1
fi

TMPDIR=$(mktemp -d)
BADVPN_ARCHIVE="$__dir/third_party_sources/badvpn-e89e09098c63059d31ab5eff4edc6fa2fc76c716.tar.gz"
BADVPN_SHA256="1d08c49753c1cbca00cb30f48143fc7e05aea65f266c4cdad65874a89f32f91f"
LIBANCILLARY_ARCHIVE="$__dir/third_party_sources/libancillary-0.9.1.tar.gz"
LIBANCILLARY_SHA256="02657d9e5b0f25892fcdf21ff1f44a7db5c1d0bd8254c91885454e2880424050"

clear_tmp () {
  rm -rf $TMPDIR
}

verify_archive () {
  local archive="$1"
  local expected="$2"
  local actual

  actual=$(sha256sum "$archive" | awk '{print $1}')
  if [[ "$actual" != "$expected" ]]; then
    echo "Checksum mismatch for $archive" >&2
    exit 1
  fi
}

trap 'echo -e "Aborted, error $? in command: $BASH_COMMAND"; trap ERR; clear_tmp; exit 1' ERR INT
install -m644 $__dir/tun2socks.mk $TMPDIR/

pushd $TMPDIR
verify_archive "$BADVPN_ARCHIVE" "$BADVPN_SHA256"
verify_archive "$LIBANCILLARY_ARCHIVE" "$LIBANCILLARY_SHA256"
tar -xzf "$BADVPN_ARCHIVE"
tar -xzf "$LIBANCILLARY_ARCHIVE"
mv libancillary-0.9.1 libancillary
$NDK_HOME/ndk-build \
	NDK_PROJECT_PATH=. \
	APP_BUILD_SCRIPT=./tun2socks.mk \
	APP_ABI=all \
	APP_PLATFORM=android-19 \
	NDK_LIBS_OUT=$TMPDIR/libs \
	NDK_OUT=$TMPDIR/tmp \
	APP_SHORT_COMMANDS=false LOCAL_SHORT_COMMANDS=false -B -j4

install -v -m755 libs/armeabi-v7a/tun2socks  $__dir/../V2rayNG/app/src/main/jniLibs/armeabi-v7a/libtun2socks.so
install -v -m755 libs/arm64-v8a/tun2socks    $__dir/../V2rayNG/app/src/main/jniLibs/arm64-v8a/libtun2socks.so
install -v -m755 libs/x86/tun2socks          $__dir/../V2rayNG/app/src/main/jniLibs/x86/libtun2socks.so
install -v -m755 libs/x86_64/tun2socks       $__dir/../V2rayNG/app/src/main/jniLibs/x86_64/libtun2socks.so
popd

rm -rf $TMPDIR
