# Contributing to divviup-android

## Prerequisites

Building this project requires a Java JDK (17 or newer), the Android SDK, Rust,
and Docker to be installed. It is highly recommended to use [Android
Studio](https://developer.android.com/studio).

### Android Studio

Installing Android Studio will provide both a Java JDK and the Android SDK.
Android Studio is the recommended way to install and manage Android SDK
components. Refer to the documentation on [compatible versions of the Android
Gradle plugin and Android Studio][compat-docs] to determine what version to
install. The Android Gradle plugin's version is set in `/build.gradle.kts`.

[compat-docs]:
    https://developer.android.com/studio/releases#android_gradle_plugin_and_android_studio_compatibility

### Android SDK

An Android SDK must be installed, along with several additional components. SDK
components can be managed from Android Studio via Tools -> SDK Manager, or on
the command line via `$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager`.

Install the version of "Android SDK Build-Tools" that matches the `compileSdk`
version in `/divviup/build.gradle.kts`. Install the version of the NDK that
matches `ndkVersion` in `/divviup/build.gradle.kts`. Install the "Android
Emulator".

Additionally, you will need to set up a virtual emulator device and download a
system image for it. This can be done via Tools -> Device Manager in Android
Studio or `$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager` on the command
line. Use a system image for a recent API level and the processor architecture
matching your host computer for day-to-day development.

When making major FFI changes or adding support for a new architecture, you may
want to return to the device manager and create new virtual devices with
different architectures for additional testing. Note that there is a significant
performance difference between using a processor architecture that matches the
host (which can be virtualized) versus using a dissimilar processor architecture
(which must be emulated).

### Rust

Additional files must be installed to cross-compile for Android platforms. Run
the following command to download target-specific copies of the standard
library.

```sh
rustup target add armv7-linux-androideabi aarch64-linux-android i686-linux-android x86_64-linux-android
```

### Docker

[Docker][docker-install] is required for host-based integration tests against
Janus, to confirm that reports are correctly formed, and DAP API requests are
correct. Alternative container runtimes may be used in lieu of Docker, see the
[Testcontainers documentation][testcontainers-docker] for details.

[docker-install]: https://docs.docker.com/desktop/
[testcontainers-docker]: https://java.testcontainers.org/supported_docker_environment/

## Building and Testing

All compilation and testing is managed by Gradle targets, which can be used from
both Android Studio and the command line. Right click on a project or a test
folder in Android Studio to run or debug it. Running the sample app launches the
emulator within the Android Studio UI. Any `test` directory runs on the host's
JDK, while any `androidTest` must run in an emulator or a connected device in
developer mode. Gradle can be invoked on the command line as follows.

```sh
# Runs host-based tests.
./gradlew check

# Runs device-based tests. An emulator must already be running.
./gradlew connectedCheck

# Build the AAR.
./gradlew :divviup:assemble

# Build the sample app's APK.
./gradlew :sampleapp:assemble
```

Note that the CI only runs `./gradlew check`, and not `./gradlew
connectedCheck`, because of the difficulty of accessing an Android emulator from
CI runners.

## Releases

See [internal
documentation](https://github.com/divviup/docs/blob/main/janus/releasing-janus.md#generating-a-release-1).
