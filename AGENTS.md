# Repository Guidelines

## Project Structure & Module Organization
ScrcpyForAndroid is split into two Android Gradle modules. The client app lives in `app/`, with Java sources under `app/src/main/java`, resources in `app/src/main/res`, and the embedded desktop bridge binaries in `app/src/main/jniLibs`. The headless server that streams the mirrored device ships from `server/`, which exposes AIDL stubs in `server/src/main/aidl`; its built artifact is copied into `app/src/main/assets/scrcpy-server.jar` through Gradle when you assemble. Auxiliary release scripts and Play Store metadata live in `fastlane/`.

## Build, Test, and Development Commands
Run `./gradlew assembleDebug` to build the client and package the matching server jar automatically via the `copyServer` task. Use `./gradlew :server:assembleRelease` when you only need an updated server artifact. `./gradlew clean` removes stale build outputs; add `:server:deleteServer` if you must drop the embedded jar before rebuilding.

## Coding Style & Naming Conventions
All production sources are Java with four-space indentation and brace-on-new-line formatting; mirror the existing layout in files like `app/src/main/java/org/client/scrcpy/MainActivity.java`. Prefer descriptive PascalCase for classes, camelCase for methods and fields, and SCREAMING_SNAKE_CASE for constants defined in `Constant.java`. Android resources should follow standard prefixes (`activity_`, `fragment_`, `ic_`) and live under localized `values-*` folders when text varies. Keep command invocations and host addresses user-facing by extracting them into `strings.xml`.

## Testing Guidelines
Use JUnit4 for local unit tests (`app/src/test/java`) and Espresso for device-level checks (`app/src/androidTest/java`). Name tests after the behavior under test, e.g., `MainActivityConnectionTest`, and group helper fakes under a `support` package. Run `./gradlew testDebugUnitTest` before pushing for fast feedback, and `./gradlew connectedAndroidTest` on an emulator to validate the ADB workflow end-to-end. The server module exposes its own unit suite through `./gradlew :server:testDebugUnitTest`; ensure new protocol changes include matching coverage.

## Commit & Pull Request Guidelines
Recent history favors short, imperative subjects in lowercase (e.g., `fix build script`); follow that style and scope each commit to one logical change, with body details when behavior shifts. Every pull request should link the tracked issue, summarize user-visible impact, and include screenshots or screen recordings when UI flows change. Mention how you validated the change (commands run, devices tested) and call out any follow-up tasks so reviewers know what remains.

## Security & Configuration Tips
Never hardcode signing secrets. The release keystore in `app/build.gradle` reads passwords from environment variables (`SIGNING_*`) or a developer-only `local.properties`; confirm these remain excluded from Git. When testing on public networks, redact IPs and hostnames in logs before sharing artifacts.

## Build Environment Notes (from MEMO)
- Target Java 17–21 with `JAVA_HOME` pointing at that JDK; the current Android Gradle Plugin (8.0.0) expects Java 17.
- Install Android SDK Platform 31, Build Tools 31.0.0, and platform-tools, and accept licenses via `sdkmanager` before running Gradle.
- The bundled Gradle Wrapper (8.6) auto-downloads during `./gradlew assembleDebug`.
- Release signing reads `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, and `SIGNING_KEY_PASSWORD` from the environment, falling back to `local.properties` for local-only testing.

### Docker Build Workflow
- Base image: `ubuntu:22.04` with JDK 17 and Android command-line tools (see repository `Dockerfile`).
- Build the image with `docker build -t scrcpy-android-builder .`.
- Run builds with `docker run --rm -v $(pwd):/workspace scrcpy-android-builder ./gradlew assembleDebug` (adjust the trailing command for other Gradle tasks).
- Pass signing environment variables (e.g., `-e SIGNING_STORE_PASSWORD=...`) when you need release artifacts.

### Build Outputs
- Debug APK path: `app/build/outputs/apk/scrcpy/debug/app-scrcpy-debug.apk`.
- The server jar is copied into `app/src/main/assets/scrcpy-server.jar` via the Gradle `copyServer` task.

### Artifact Usage
- Install the debug APK on the client device with `adb install app/build/outputs/apk/scrcpy/debug/app-scrcpy-debug.apk`.
- The bundled `scrcpy-server.jar` is pushed to the mirrored device over ADB when the app starts.

### Caveats
- `package` declarations in `app/src/main/AndroidManifest.xml` and `server/src/main/AndroidManifest.xml` are ignored because the namespace is already defined; Gradle 8 warns about this.
- The app still uses deprecated APIs such as `ProgressDialog` and `View#setSystemUiVisibility`. Builds succeed, but plan to replace them in future maintenance.
