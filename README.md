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
フロー解説
録音開始フロー
ユーザーが「録音開始」ボタンを押してから、実際に録音が始まるまでの流れです。

【ユーザー】 「録音開始」ボタンをタップ

MainActivity がボタンのタップを検知します。
【MainActivity】 パーミッションの確認

RECORD_AUDIO（録音）や POST_NOTIFICATIONS（通知）などの権限が許可されているかチェックします。
もし未許可なら: ユーザーに許可を求めるポップアップを表示し、処理は一旦中断します。
もし許可済みなら: 次のステップへ進みます。
【MainActivity -> RecordingService】 サービスの起動指示

startForegroundService() メソッドを使って RecordingService を起動します。
「バックグラウンドで処理を開始してください」という指示（Intent）を飛ばします。
【RecordingService】 フォアグラウンドサービス化

onStartCommand() が呼ばれます。
最初に「録音中です」という通知を作成し、startForeground() を呼び出します。
⚠️ 重要: startForegroundService() で起動されたサービスは、数秒以内に startForeground() を呼ばないと、システムに強制終了させられてしまいます。
【RecordingService】 MediaRecorderの準備（状態遷移）

MediaRecorder の設定を決められた順序で行います。これが録音機能の心臓部です。
<!-- end list -->

[State: Initial]           -> new MediaRecorder()
[State: Initialized]        -> .setAudioSource(MIC)
[State: DataSourceConfigured] -> .setOutputFormat(MPEG_4)
-> .setAudioEncoder(AAC)
-> .setOutputFile(保存先パス)
[State: Prepared]           -> .prepare()
【RecordingService】 録音開始！

mediaRecorder.start() を呼び出します。
[State: Recording] 状態になり、マイクからの音声がファイルに書き込まれ始めます。
録音停止フロー
【ユーザー】 「録音終了」ボタンをタップ

MainActivity がボタンのタップを検知します。
【MainActivity -> RecordingService】 サービスの停止指示

stopService() メソッドを使って RecordingService の停止を指示します。
【RecordingService】 録音の停止と後片付け

onDestroy() メソッドが呼ばれます。
mediaRecorder.stop() を呼び出し、録音を停止します。ファイルへの書き込みが完了します。
mediaRecorder.release() を呼び出し、MediaRecorderが確保していたメモリなどのリソースを完全に解放します。
⚠️ 重要: release() を忘れると、アプリを閉じてもリソースを掴みっぱなしになり、メモリリークや他のアプリの動作不良の原因になります。
押さえておきたい重要コンセプト
フォアグラウンドサービス (Foreground Service)

なぜ必要？: アプリがバックグラウンドに回っても、ユーザーに「動作中であること」を通知で示し続けることで、Android OSによる強制終了を防ぐためです。バッテリー消費の激しい処理（位置情報取得、音楽再生、録音など）で利用が必須とされています。
パーミッション (Permissions)

なぜ必要？: ユーザーのプライバシーを守るためです。マイクのような機密性の高い機能へのアクセスには、AndroidManifest.xmlでの事前申告と、アプリ実行中のユーザーからの動的な許可の両方が必要になります。
アプリ固有ストレージ (App-Specific Storage)

なぜここに保存？: Androidのセキュリティモデル（スコープストレージ）に従うためです。この場所に保存することで、他のアプリからデータが隔離され安全性が高まる上、アプリのアンインストール時に録音データも自動的に削除され、ユーザーのストレージにゴミが残りません。
UIとサービスの連携 (BroadcastReceiver)

なぜこれを使う？: バックグラウンドで動作するServiceから、画面に表示されているActivityへ安全に情報を伝えるためです。Serviceが「こんなログが出たよ！」とブロードキャスト（放送）を投げ、Activityがそれを受け取って画面のログエリアに表示する、という疎結合（お互いが直接干渉しすぎない）な設計になっています。