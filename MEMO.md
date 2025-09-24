# MEMO

## ビルド環境の前提
- Android Gradle Plugin 8.0.0 に合わせて Java 17 を利用する。`JAVA_HOME` は JDK 17〜21 が指すことを確認する。
- Android SDK 12 (API 31) の Platform / Build Tools 31.0.0 / Platform-Tools をインストールし、`sdkmanager` でライセンス承認を済ませておく。
- `./gradlew assembleDebug` 実行時には同梱の Gradle Wrapper (8.6) が自動でダウンロードされる。
- リリースビルド用の鍵情報は `SIGNING_STORE_PASSWORD` などの環境変数、または開発用 `local.properties` から読み込まれる設計 (`app/build.gradle` を参照)。

## Docker ビルド環境
- ルートに追加した `Dockerfile` は `ubuntu:22.04` ベースで JDK 17 と Android コマンドラインツールをセットアップする。
- イメージ作成: `docker build -t scrcpy-android-builder .`
- ビルド実行例: `docker run --rm -v $(pwd):/workspace scrcpy-android-builder ./gradlew assembleDebug`
  - 初回は Gradle/SDK の取得で時間がかかる。
  - 別タスクを走らせる場合は後続引数を変更する（例: `./gradlew testDebugUnitTest`）。
- リリース署名が必要な場合は `-e SIGNING_STORE_PASSWORD=...` などの環境変数を渡す。

## ビルド結果と成果物
- `app/build/outputs/apk/scrcpy/debug/app-scrcpy-debug.apk` がデバッグ用 APK。
- `app/src/main/assets/scrcpy-server.jar` にサーバ JAR がコピーされる (`:server:copyServer` タスク経由)。

## 成果物の用途
- `app-scrcpy-debug.apk`: クライアント端末にインストールして利用するアプリ本体。ADB で転送する場合は `adb install app/build/outputs/apk/scrcpy/debug/app-scrcpy-debug.apk`。
- `scrcpy-server.jar`: ミラー対象デバイス側で動作する headless サーバ。APK に同梱されており、アプリ起動時に ADB 経由でリモート端末へプッシュ・起動される。

## 注意事項
- `app/src/main/AndroidManifest.xml` と `server/src/main/AndroidManifest.xml` の `package` 属性は namespace と重複しており、Gradle 8 系では無視されるため警告が出る。
- アプリ内では `ProgressDialog` や `View#setSystemUiVisibility` など非推奨 API が使われている。ビルドは成功するが将来のメンテナンスで置き換えを検討。
