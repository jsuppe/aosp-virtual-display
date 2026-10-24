#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
bt="$sdk/build-tools/${BUILD_TOOLS_VERSION:-35.0.0}"
jar="$sdk/platforms/android-${ANDROID_API:-35}/android.jar"
out="${VDISPLAY_OUT:-$root/out/poc}"
mkdir -p "$out"
out="$(cd "$out" && pwd)"
work="$(mktemp -d "$out/build.XXXXXX")"
trap 'rm -rf -- "$work"' EXIT
mkdir -p "$work/classes" "$work/dex" "$work/package"
"$bt/aapt2" link -o "$work/base.apk" -I "$jar" --manifest "$root/poc/AndroidManifest.xml"
mapfile -t sources < <(find "$root/poc/src" -name '*.java' -type f | sort)
javac -source 8 -target 8 -Xlint:-options -classpath "$jar" -d "$work/classes" "${sources[@]}"
mapfile -t classes < <(find "$work/classes" -name '*.class' -type f | sort)
"$bt/d8" --lib "$jar" --min-api 29 --output "$work/dex" "${classes[@]}"
cp "$work/base.apk" "$work/package/app.apk"
(cd "$work/dex" && zip -q -j "$work/package/app.apk" classes*.dex)
"$bt/zipalign" -f 4 "$work/package/app.apk" "$work/aligned.apk"
# Activity launch on an untrusted VD needs signature ACTIVITY_EMBEDDING.
# Supply the matched development base's platform signing files, never commit keys.
: "${VDISPLAY_SIGN_KEY:?Set VDISPLAY_SIGN_KEY to the matched platform PK8 key}"
: "${VDISPLAY_SIGN_CERT:?Set VDISPLAY_SIGN_CERT to its X509 certificate}"
"$bt/apksigner" sign --key "$VDISPLAY_SIGN_KEY" --cert "$VDISPLAY_SIGN_CERT" \
  --out "$out/VirtualDisplayPoc.apk" "$work/aligned.apk"
"$bt/apksigner" verify "$out/VirtualDisplayPoc.apk"
sha256sum "$out/VirtualDisplayPoc.apk"
