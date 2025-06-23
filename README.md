Android録音アプリの仕組みとフロー解説
このドキュメントは、このシンプルな録音アプリが、Androidのどのような仕組みを使ってバックグラウンドでの録音を実現しているかを解説します。

登場人物（主要コンポーネント）
このアプリは、主に以下の4つの要素が連携して動作しています。

MainActivity.java: 司令塔（UI担当）
ユーザーが操作する画面です。
「録音開始」「録音終了」ボタンの操作を受け付けます。
録音に必要な権限（パーミッション）をユーザーに要求します。
実際の録音作業を行う RecordingService に対して「開始」「停止」の指示を出します。

RecordingService.java: 実行部隊（バックグラウンド処理担当）
アプリが画面に表示されていなくても、バックグラウンドで録音を続けるための部品です。
フォアグラウンドサービスとして動作し、Androidシステムに「重要な処理中です」と通知で知らせることで、処理が中断されにくくなります。
MediaRecorder を直接操作して、録音の開始・停止・保存を行います。

MediaRecorder: 録音のプロフェッショナル
Androidに標準で用意されている、マイクなどの音声を録音・エンコード（データ変換）するためのクラスです。
厳密な手順（状態遷移）に沿って操作しないとエラーになる、繊細な専門家です。

AndroidManifest.xml: アプリの設計図・身分証明書
アプリがどんな権限（マイクの使用許可など）を必要とするかを定義します。
RecordingService という部品が存在することをAndroidシステムに登録します。

=================
全体像：役割分担
まず、2つのファイルの役割分担は以下のようになっています。

MainActivity.java：司令塔・フロント担当

ユーザーが操作する画面（UI）の表示と操作（ボタンのクリックなど）の受付。
録音に必要な権限（パーミッション）の確認とリクエスト。
RecordingServiceに対して「録音を開始しろ」「停止しろ」という命令を出す。
RecordingServiceからの状況報告（ログ）を受け取って画面に表示する。
RecordingService.java：録音の実行部隊・バックグラウンド担当

画面を持たず、裏方に徹して実際の録音処理を行う。
MainActivityからの命令を受けて、MediaRecorderを使い録音を開始・停止する。
録音した音声データをファイルとして保存する。
自分の状況（「録音を開始しました」など）をMainActivityに逐一報告する。
フロー①：録音開始の流れ
ユーザーが「録音開始」ボタンを押してから、実際に録音が始まるまでのステップです。

あなた ➡ MainActivity ➡ RecordingService

あなた: MainActivityの画面で**[録音開始]ボタン**をタップします。
MainActivity.java: btnStartのsetOnClickListenerが実行され、checkPermissionsAndStart()メソッドを呼び出します。
MainActivity.java: checkPermissionsAndStart()の中で、録音（RECORD_AUDIO）と通知（POST_NOTIFICATIONS、Android 13以上）のパーミッションが許可されているかチェックします。
許可されていない場合: OSの許可ダイアログを表示し、ユーザーに許可を求めます。許可されると、次のステップに進みます。
既に許可されている場合: すぐに次のステップに進みます。
MainActivity.java: startRecordingService()メソッドを呼び出します。
MainActivity.java: Intentを作成し、startForegroundService(serviceIntent) を使ってRecordingServiceの起動をOSに命令します。
(OS): RecordingServiceを起動し、そのonStartCommand()メソッドを呼び出します。
RecordingService.java: onStartCommand()が実行されます。これがサービスの処理開始の合図です。
RecordingService.java: startForeground()を呼び出し、スマホの通知バーに「録音中です」という通知を表示させます。これにより、アプリがバックグラウンドに回ってもOSに強制終了されにくくなります。
RecordingService.java: startRecording()メソッドを呼び出します。
RecordingService.java: startRecording()の中で、MediaRecorderを準備し、mediaRecorder.start()を実行して実際の録音が開始されます。
フロー②：録音停止の流れ
ユーザーが「録音停止」ボタンを押してから、ファイルが保存されるまでのステップです。

あなた ➡ MainActivity ➡ RecordingService

あなた: MainActivityの画面で**[録音終了]ボタン**をタップします。
MainActivity.java: btnStopのsetOnClickListenerが実行され、stopRecordingService()メソッドを呼び出します。
MainActivity.java: stopService(serviceIntent) を使ってRecordingServiceの停止をOSに命令します。
(OS): RecordingServiceを破棄する準備に入り、そのonDestroy()メソッドを呼び出します。
RecordingService.java: onDestroy()が実行されます。これがサービスの終了処理の合図です。
RecordingService.java: onDestroy()の中で、後片付けのためにstopRecording()メソッドを呼び出します。
RecordingService.java: stopRecording()の中で、mediaRecorder.stop()を実行して録音を停止します。
RecordingService.java: mediaRecorder.release()を実行して、使用していたマイクなどのリソースを解放します。これでファイルが完全に保存されます。
フロー③：ログ通知の仕組み (Service ➡ Activity)
RecordingServiceでの出来事が、MainActivityのログエリアにリアルタイムで表示される仕組みです。

RecordingService.java: startRecording()やstopRecording()などの処理の節目で、sendToLog("ログメッセージ")メソッドを呼び出します。
RecordingService.java: sendToLog()は、ログメッセージをIntentに詰め込み、sendBroadcast(intent)で**「店内放送」のようにアプリ内に向けて一斉送信**します。
MainActivity.java: onStart()で登録しておいたlogReceiver（BroadcastReceiver）が、この放送をキャッチします。
MainActivity.java: logReceiverのonReceive()メソッドが実行されます。
MainActivity.java: onReceive()の中で、放送（Intent）からログメッセージを取り出し、addLog()メソッドに渡して画面のTextViewに表示します。
この仕組みによって、裏方であるRecordingServiceの状況が、表舞台のMainActivityに伝わっているわけです。

ファイル名と保存場所について
何をしているか: RecordingServiceのgetOutputFilePath()メソッドがファイル名を決めています。
ファイル名: REC_ + yyyyMMdd_HHmmss (年月日時分秒) + .mp4 という形式で、録音開始時の日時から一意なファイル名を生成しています。 例: REC_20250623_153000.mp4
保存場所: getExternalFilesDir(null) という、このアプリ専用の外部ストレージ領域（通常は Android/data/com.example.recordingapp/files/ の下）に保存されます。この場所は、アプリをアンインストールすると自動的に削除されます。

=================



押さえておきたい重要コンセプト

フォアグラウンドサービス (Foreground Service)
なぜ必要？: アプリがバックグラウンドに回っても、ユーザーに「動作中であること」を通知で示し続けることで、Android OSによる強制終了を防ぐためです。バッテリー消費の激しい処理（位置情報取得、音楽再生、録音など）で利用が必須とされています。

パーミッション (Permissions)
なぜ必要？: ユーザーのプライバシーを守るためです。マイクのような機密性の高い機能へのアクセスには、AndroidManifest.xmlでの事前申告と、アプリ実行中のユーザーからの動的な許可の両方が必要になります。

アプリ固有ストレージ (App-Specific Storage)
なぜここに保存？: Androidのセキュリティモデル（スコープストレージ）に従うためです。この場所に保存することで、他のアプリからデータが隔離され安全性が高まる上、アプリのアンインストール時に録音データも自動的に削除され、ユーザーのストレージにゴミが残りません。

UIとサービスの連携 (BroadcastReceiver)
なぜこれを使う？: バックグラウンドで動作するServiceから、画面に表示されているActivityへ安全に情報を伝えるためです。Serviceが「こんなログが出たよ！」とブロードキャスト（放送）を投げ、Activityがそれを受け取って画面のログエリアに表示する、という疎結合（お互いが直接干渉しすぎない）な設計になっています。