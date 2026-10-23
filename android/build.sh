#!/usr/bin/env bash
# Build xmb-launcher.apk. Needs: JDK 8+, Android SDK (build-tools + platforms/android-30), R8 jar.
# Env: ANDROID_HOME (default /opt/androidsdk), BT_VER (34.0.0), R8_JAR (default /tmp/r8.jar)
set -e
cd "$(dirname "$0")"
export ANDROID_HOME=${ANDROID_HOME:-/opt/androidsdk}
BT=$ANDROID_HOME/build-tools/${BT_VER:-34.0.0}; AJ=$ANDROID_HOME/platforms/android-30/android.jar
R8=${R8_JAR:-/tmp/r8.jar}
[ -f assets/index.html ] || python3 ../tools/mk.py
rm -rf out && mkdir -p out/res out/cls out/dex
$BT/aapt2 compile --dir res -o out/res
$BT/aapt2 link -o out/base.apk -I $AJ --manifest AndroidManifest.xml --min-sdk-version 23 --target-sdk-version 30 --java out/gen out/res/*.flat
javac -nowarn -g:none --release 8 -cp $AJ -d out/cls $(find src out/gen -name '*.java')
java -cp $R8 com.android.tools.r8.D8 --lib $AJ --min-api 23 --output out/dex $(find out/cls -name '*.class')
cp out/base.apk out/app.apk
(cd out/dex && zip -q ../app.apk classes.dex)
# video + audio must be stored uncompressed (MediaPlayer reads them via AssetFileDescriptor)
zip -q -r out/app.apk assets -x '*.mp4' '*.ogg'
zip -q -0 out/app.apk assets/*.mp4 assets/*.ogg
$BT/zipalign -f 4 out/app.apk out/aligned.apk
[ -f dbg.jks ] || keytool -genkeypair -keystore dbg.jks -storepass android -keypass android -alias k -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=xmb" >/dev/null 2>&1
$BT/apksigner sign --ks dbg.jks --ks-pass pass:android --key-pass pass:android --out out/xmb-launcher.apk out/aligned.apk
$BT/apksigner verify out/xmb-launcher.apk && ls -la out/xmb-launcher.apk
