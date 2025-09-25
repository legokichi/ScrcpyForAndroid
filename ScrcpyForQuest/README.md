# QuestHelloWorld 2D Panel Template

This template is a minimal Android project that targets Meta Quest OS (Horizon OS) 2D panel surfaces. When launched on a Quest headset, it displays a centered `helloworld` label on a dark background.

## Project structure

- `app/src/main/AndroidManifest.xml` configures the activity for 2D panel mode (`com.oculus.intent.category.2D`) and enables free panel resizing with a default window size defined in `res/xml/panel_window_layout.xml`.
- `MainActivity` is an `AppCompatActivity` that inflates `activity_main.xml`, which centers the `helloworld` text.
- Gradle is configured for Android Gradle Plugin 8.5.2, Kotlin 2.0.20, `compileSdk` 35, and `minSdk` 29 to ensure compatibility with current Quest OS releases.

## Prerequisites

- Android Studio Koala | 2024.1.1 Patch 1 or newer.
- Android SDK Platform 35 and Android 14 emulator components.
- Meta Quest Link cable or Wi-Fi ADB to deploy to a headset with Developer Mode enabled.

## Getting started

1. Open the project in Android Studio and let it sync Gradle.
2. Connect a Quest headset with Developer Mode enabled and accept the ADB prompt.
3. From *Run > Select Device*, pick the headset, then click *Run*. The `helloworld` text should appear on a resizable 2D panel.

## Customization tips

- Update `res/xml/panel_window_layout.xml` to change the default surface size or gravity.
- Adjust `AndroidManifest.xml` metadata (e.g., `com.oculus.vrdesktop.control_bar_config`) to tweak the control bar and resizing behavior.
- Replace `activity_main.xml` with Jetpack Compose or another UI framework as needed.

## Validation checklist

- Verify the app launches in a 2D panel and can be resized freely.
- Confirm the text remains centered when resizing, and colors render as expected on the Quest display.
- Run `./gradlew lint` and `./gradlew assembleDebug` before distributing the APK.
