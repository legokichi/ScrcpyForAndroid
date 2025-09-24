# Scrcpy for Quest

- This application is android port to desktop applicaton [**Scrcpy**](https://github.com/Genymobile/scrcpy).

- This application mirrors display and touch controls from a remote android device to android device.

- Scrcpy for Android uses ADB-Connect interface to connect to android device to be mirrored.



## Download

[scrcpy-release.apk](https://github.com/zwc456baby/ScrcpyForAndroid/releases)


![home](home.jpg)



## Instructions to use

- Make sure both devices are on same local network.
- Enable **ADB-connect/ADB-wireless/ADB over network** on the device to be mirrored. 
- Open scrcpy-android app and enter ip address of device to be mirrored.
- Select display parameters and bitrate from drop-down menu(1280x720 and 2Mbps works best).
- Set **Navbar** switch if the device to be mirrored has only hardware navigation buttons.
- Hit **start** button.
- Accept and trust(check always allow from this computer) the ADB connection prompt on target device(Some custom roms don't have this prompt).
- Thats all! You should be seeing the screen of remote android device.
- To wake up the remote device, **double tap anywhere on screen**.
- To put the remote device to sleep, **close proxmity sensor and double tap anywhere on the screen**. 
- To bring back the local android system navbar while mirroring the remote device, **swipe up from the bottom edge of screen**.



## Connecting to public network devices



>  The public network port of the device needs to be open for access



### Connection Example

- 192.168.1.222

- host.example.com:5555

- [2000:2000:2000:2000::2000]:5555

## Code Reference

- [scrcpy-android](https://gitlab.com/las2mile/scrcpy-android)
- [scrcpy](https://github.com/Genymobile/scrcpy)



- app/src/main/java/org/client/scrcpy/App.java:28-92 でアプリ起動直後に同梱のlibadb.so を使って ADB サーバーを開始し、アプリ内から任意の ADB コマンドを実行できるようにしています。これによりユーザー端末上のクライアントがリモート端末と直接 ADB でやり取りできます。
- 接続操作を行うと app/src/main/java/org/client/scrcpy/MainActivity.java:760-808 がscrcpy-server.jar をアセットから端末上の一時領域に展開し、SendCommands に ADB 経由の準備を依頼します。
- app/src/main/java/org/client/scrcpy/SendCommands.java:34-114 は (1) adb connect で対象端末に接続し、(2) adb push で scrcpy-server.jar を /data/local/tmp/ に配置し、(3)adb forward tcp:7008 tcp:7007 でローカルの 7008 番ポートとリモートの 7007 番ポートをトンネリングし、(4) app_process から org.server.scrcpy.Server を起動してサーバー側プロセスを立ち上げます。
- リモート端末側では server/src/main/java/org/server/scrcpy/Server.java:15-101 がDroidConnection を開き、画面・音声のエンコーダ (ScreenEncoder と AudioEncoder) を起動して同一ソケットに映像・音声ストリームを流します。映像は server/src/main/java/org/server/scrcpy/ScreenEncoder.java:131-188 で H.264 に、音声は server/src/main/java/org/server/scrcpy/AudioEncoder.java:62-121 で AAC にエンコードされています。ストリーム開始時には解像度情報を 16 バイトで先に送っています。
- クライアント側サービス app/src/main/java/org/client/scrcpy/Scrcpy.java:81-339 はポートフォワードされた 127.0.0.1:7008 にソケット接続し、先頭 16 バイトで受け取ったリモート解像度を保存した後、連続する VideoPacket / AudioPacket を読み込みます。各種フラグに応じて VideoDecoder (app/src/main/java/org/client/scrcpy/decoder/VideoDecoder.java:1-103) と AudioDecoder (app/src/main/java/org/client/scrcpy/decoder/AudioDecoder.java:1-121) を設定・デコードし、SurfaceView と AudioTrack に描画・再生します。
- ユーザーがタップ・スワイプすると app/src/main/java/org/client/scrcpy/Scrcpy.java:136-223 がローカル表示サイズとリモート実解像度の比率を用いて座標を補正し、ポインタ ID を含む 20 バイトのイベントを送信キューに積みます。イベントは映像と同じ TCPソケットからサーバーへ送信されます。
- サーバー側の server/src/main/java/org/server/scrcpy/EventController.java:66-183 はこの 20 バイト配列を読み取り、InputManager ラッパーを介して injectInputEvent を呼び出し、タッチやキー入力をリモート端末に注入します。マルチタッチや近接センサーによる電源ボタン動作などもここでハンドリングしています。

このように、アプリは ADB を利用してリモート端末上に scrcpy のサーバーを一時的に展開し、TCP ソケット一本で映像・音声と入力イベントを双方向にやり取りすることでスマホのリモートコントロールを実現しています。

