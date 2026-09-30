#!/bin/sh
set -eu
cd "$(dirname "$0")"
export PATH="/data/data/com.termux/files/usr/bin:$PATH"
platform_jar="${ANDROID_JAR:-../hello-android/platform/android-35/android.jar}"
mkdir -p build/classes
javac -source 8 -target 8 -bootclasspath "$platform_jar" -d build/classes src/com/example/colorbattle/*.java
dx --dex --output=build/classes.dex build/classes
aapt package -f -M AndroidManifest.xml -S res -A assets -I "$platform_jar" -F build/unsigned.apk
(cd build && aapt add unsigned.apk classes.dex)
zipalign -f 4 build/unsigned.apk build/aligned.apk
if [ ! -f build/debug.keystore ]; then
 keytool -genkeypair -keystore build/debug.keystore -alias color-battle -storepass android -keypass android -dname 'CN=Color Battle Development' -keyalg RSA -validity 10000
fi
apksigner sign --ks build/debug.keystore --ks-key-alias color-battle --ks-pass pass:android --key-pass pass:android --out build/color-battle.apk build/aligned.apk
apksigner verify --verbose build/color-battle.apk
