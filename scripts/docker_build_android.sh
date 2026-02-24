#!/bin/bash
set -eux

# install necessary packages
apt-get update
apt-get install -y wget unzip git curl python3

# setup Android SDK
mkdir -p /opt/android-sdk/cmdline-tools
cd /opt/android-sdk
wget -q https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip -O cmdline.zip
unzip -q cmdline.zip -d cmdline-tools
# move into expected path
mkdir -p cmdline-tools/latest
mv cmdline-tools/cmdline-tools/* cmdline-tools/latest/ || true

export ANDROID_HOME=/opt/android-sdk
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# accept licenses and install packages
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses
sdkmanager --sdk_root="$ANDROID_HOME" "platform-tools" "platforms;android-34" "build-tools;34.0.0" "platforms;android-33" "build-tools;33.0.2"

# install gradle
wget -q https://services.gradle.org/distributions/gradle-8.4-bin.zip -O /tmp/gradle.zip
unzip -q /tmp/gradle.zip -d /opt/gradle
export PATH="/opt/gradle/gradle-8.4/bin:$PATH"

# build the project
cd /work/apps/mobile-rider
/opt/gradle/gradle-8.4/bin/gradle assembleDebug --no-daemon --stacktrace --warning-mode all

# print artifact path
ls -l app/build/outputs/apk/debug || true

exit 0
