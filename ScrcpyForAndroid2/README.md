# Scrcpy for Android

Scrcpy for Android bundles an Android client and a headless server that mirrors and controls remote devices over ADB. The client wraps scrcpy features in a native interface while embedding the server jar so both artifacts ship together.

## Project Structure
- `app/` – Android client module (Java sources under `src/main/java`, resources in `src/main/res`, native ADB binaries in `src/main/jniLibs`).
- `server/` – Headless server module. AIDL definitions live in `src/main/aidl`, implementation sources in `src/main/java`.
- `fastlane/` – Play Store metadata and release automation scripts.
- `Dockerfile` – Reproducible build container with JDK 17 and Android command-line tools.

## Prerequisites
- JDK 17 installed (`JAVA_HOME` pointing to it).
- Android SDK Platform 31, Build Tools 31.0.0, and latest platform-tools with accepted licenses (`sdkmanager --licenses`).
- A device or emulator with USB debugging enabled if you plan to run instrumentation tests.

## Build (Host Environment)
1. Sync dependencies once: `./gradlew tasks`.
2. Build the client APK (bundles the server jar automatically):
   ```bash
   ./gradlew assembleDebug
   ```
   Output: `app/build/outputs/apk/scrcpy/debug/app-scrcpy-debug.apk`.
3. Rebuild just the server artifact when iterating on backend changes:
   ```bash
   ./gradlew :server:assembleRelease
   ```
4. Clean artifacts when switching branches:
   ```bash
   ./gradlew clean
   ./gradlew :server:deleteServer   # optional, removes embedded jar
   ```

## Docker Build Workflow
1. Build the image from the repo root:
   ```bash
   docker build -t scrcpy-android-builder .
   ```
2. Run Gradle inside the container (mount the repo to `/workspace`):
   ```bash
   docker run --rm -v $(pwd):/workspace scrcpy-android-builder ./gradlew assembleDebug
   ```
3. Swap the trailing Gradle command for other tasks, e.g. `./gradlew :server:assembleRelease` or `./gradlew testDebugUnitTest`.
4. Pass signing secrets when you need release APKs:
   ```bash
   docker run --rm \
     -e SIGNING_STORE_PASSWORD=*** \
     -e SIGNING_KEY_ALIAS=*** \
     -e SIGNING_KEY_PASSWORD=*** \
     -v $(pwd):/workspace scrcpy-android-builder \
     ./gradlew assembleRelease
   ```

## Testing
- Local unit tests: `./gradlew testDebugUnitTest`.
- Instrumentation tests (requires emulator/device): `./gradlew connectedAndroidTest`.
- Server unit tests: `./gradlew :server:testDebugUnitTest`.

## Installing & Running
Install the debug build on a connected device:
```bash
adb install -r app/build/outputs/apk/scrcpy/debug/app-scrcpy-debug.apk
```
When the app starts, it pushes `scrcpy-server.jar` to the mirrored device and begins the streaming session.

## Contributing
Use four-space indentation and brace-on-new-line formatting for Java. Keep strings localized under `app/src/main/res/values*/strings.xml`. Write short lowercase imperative commit messages (`fix build script`), link issues in pull requests, and document the commands you ran plus any connected devices used for validation.
