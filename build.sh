#!/bin/bash
# Build and sign the APK without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
#
# Settings come from the environment, or from kiosk.env next to this script:
#   KIOSK_URL    dashboard to show (required), e.g. https://glance.example.com/
#   KIOSK_ZOOM   page zoom, default 0.9 (lower shows more)
#   KIOSK_DEBUG  1 enables WebView remote debugging over adb, default 0
set -euo pipefail
cd "$(dirname "$0")"
[[ -r kiosk.env ]] && source kiosk.env
: "${KIOSK_URL:?set KIOSK_URL (or put it in kiosk.env), e.g. KIOSK_URL=https://glance.example.com/}"
KIOSK_ZOOM=${KIOSK_ZOOM:-0.9}
[[ ${KIOSK_DEBUG:-0} == 1 ]] && DEBUG=true || DEBUG=false
[[ $KIOSK_URL == https://* || $KIOSK_URL == http://* ]] || { echo "KIOSK_URL must start with http(s)://" >&2; exit 1; }
[[ $KIOSK_ZOOM =~ ^[0-9]*\.?[0-9]+$ ]] || { echo "KIOSK_ZOOM must be a number" >&2; exit 1; }

SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
BT="$SDK/build-tools/36.0.0"
JAR="$SDK/platforms/android-36/android.jar"
OUT=build
rm -rf "$OUT" && mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/config/values"

esc() { sed -e 's/&/\&amp;/g' -e 's/</\&lt;/g' -e "s/'/\\\\'/g" <<<"$1"; }
cat > "$OUT/config/values/config.xml" <<XML
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="url" translatable="false">$(esc "$KIOSK_URL")</string>
    <string name="zoom" translatable="false">$KIOSK_ZOOM</string>
    <bool name="debug">$DEBUG</bool>
</resources>
XML

"$BT/aapt2" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2" compile --dir "$OUT/config" -o "$OUT/config.zip"
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml \
    --java "$OUT/gen" -o "$OUT/unsigned.apk" "$OUT/res.zip" "$OUT/config.zip"
javac --release 17 -Xlint:-options -classpath "$JAR" -d "$OUT/classes" \
    $(find src "$OUT/gen" -name '*.java')
"$BT/d8" --min-api 26 --lib "$JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
(cd "$OUT/dex" && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

# Local signing key, created on first build and kept beside the project (git-ignored).
# Keep it: an APK signed with a different key cannot be installed over this one.
KS=glance.keystore
[[ -f $KS ]] || keytool -genkeypair -keystore $KS -storepass android -keypass android \
    -alias glance -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=Glance Kiosk" >/dev/null 2>&1
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --out glance-kiosk.apk "$OUT/aligned.apk"
echo "built $(pwd)/glance-kiosk.apk ($KIOSK_URL, zoom $KIOSK_ZOOM, debug $DEBUG)"
