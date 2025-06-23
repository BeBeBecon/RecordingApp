package com.example.recordingapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

// extends Service という部分が「このRecordingServiceクラスは、AndroidのService部品としてのルールブックに従います」という宣言にあたる。
// サービス開始(ServiceIntent)するときは、OSは onStartCommand() という名前のメソッドを呼びますね、のようなもの。
public class RecordingService extends Service {

    private MediaRecorder mediaRecorder;
    private String outputFilePath;
    private boolean isRecording = false;

    // 通知チャンネルのID
    private static final String CHANNEL_ID = "RecordingServiceChannel";
    // 通知のID
    private static final int NOTIFICATION_ID = 1;

    // MainActivityにログを送信するためのアクション定義
    public static final String ACTION_UPDATE_LOG = "com.example.recordingapp.UPDATE_LOG";
    public static final String EXTRA_LOG_MESSAGE = "extra_log_message";
    public static final String ACTION_START_RECORDING = "com.example.recordingapp.ACTION_START";
    public static final String ACTION_STOP_RECORDING = "com.example.recordingapp.ACTION_STOP";

    @Override
    public void onCreate() {
        super.onCreate();
        // サービスが作成されたときに一度だけ呼ばれる
        sendToLog("--------------------");
        sendToLog("LIFECYCLE: RecordingService#onCreate: サービスがメモリ上に初めて作成されました。");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // サービスが開始されるたびに呼ばれる
        // Intentがnullでない、かつアクションが指定されている場合のみ処理
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_START_RECORDING:
                    sendToLog("LIFECYCLE: RecordingService#onStartCommand: サービスが開始コマンドを受け取りました。");

                    // 通知とフォアグラウンド化の処理
                    createNotificationChannel();
                    Intent notificationIntent = new Intent(this, MainActivity.class);
                    PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
                    Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                            .setContentTitle("録音サービス")
                            .setContentText("バックグラウンドで録音を実行中です。")
                            .setSmallIcon(android.R.drawable.ic_media_play)
                            .setContentIntent(pendingIntent)
                            .build();
                    startForeground(NOTIFICATION_ID, notification);
                    sendToLog("SYSTEM: startForeground() を呼び出し、フォアグラウンドサービスを開始しました。");

                    // 録音処理を開始
                    startRecording();
                    break;

                case ACTION_STOP_RECORDING:
                    sendToLog("LIFECYCLE: RecordingService#onStartCommand: サービスが停止コマンドを受け取りました。");
                    // 録音を停止し、サービス自身も停止する
                    stopRecording();
                    stopSelf(); // サービス自身に終了を命令する
                    break;
            }
        }

        // START_STICKY: システムによってサービスが強制終了された場合、システムがサービスを再作成する
        return START_STICKY;
    }

    /**
     * 録音を開始する処理
     */
    private void startRecording() {
        // デバッグ用の特別なタグ
        final String TAG = "RECORDER_DEBUG";
        Log.d(TAG, "startRecording: メソッドが開始されました。");

        if (mediaRecorder != null) {
            Log.d(TAG, "startRecording: 既存のmediaRecorderインスタンスを解放します。");
            mediaRecorder.release();
        }
        mediaRecorder = new MediaRecorder();
        Log.d(TAG, "startRecording: new MediaRecorder() が完了しました。");

        try {
            Log.d(TAG, "startRecording: 音声ソースをマイクに設定します...");
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            Log.d(TAG, "startRecording: -> 成功");

            Log.d(TAG, "startRecording: 出力フォーマットをMPEG_4に設定します...");
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            Log.d(TAG, "startRecording: -> 成功");

            Log.d(TAG, "startRecording: 音声エンコーダーをAACに設定します...");
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            Log.d(TAG, "startRecording: -> 成功");

            outputFilePath = getOutputFilePath();
            Log.d(TAG, "startRecording: 出力ファイルパスを取得: " + outputFilePath);
            if (outputFilePath == null) {
                Log.e(TAG, "startRecording: ファイルパスがnullのため終了します。");
                stopSelf();
                return;
            }
            mediaRecorder.setOutputFile(outputFilePath);
            Log.d(TAG, "startRecording: 出力ファイルパスの設定完了");

            Log.d(TAG, "startRecording: mediaRecorder.prepare() を呼び出します...");
            mediaRecorder.prepare();
            Log.d(TAG, "startRecording: -> 成功");

            Log.d(TAG, "startRecording: mediaRecorder.start() を呼び出します...");
            mediaRecorder.start();
            Log.d(TAG, "startRecording: -> 成功！録音を完全に開始しました。");
            isRecording = true;

        } catch (Exception e) {
            // catchする例外をExceptionに広げて、あらゆるエラーを捕捉する
            Log.e(TAG, "★★★★★★★★★★★★★★★★★★★★★★★★");
            Log.e(TAG, "startRecording: 処理中に致命的なエラーが発生しました！", e);
            Log.e(TAG, "★★★★★★★★★★★★★★★★★★★★★★★★");
            isRecording = false;
            stopSelf();
        }
    }

    /**
     * 録音を停止し、リソースを解放する処理
     */
    private void stopRecording() {
        if (!isRecording) { // もし録音中でなければ何もしない
            return;
        }
        isRecording = false; // ←←← 停止処理に入ったので、まず旗を降ろす
        sendToLog("--------------------");
        sendToLog("ACTION: 録音処理を停止します...");

        if (mediaRecorder != null) {
            try {
                // 8. 録音を停止 (State: Recording -> Initial)
                sendToLog("[State: Recording] MediaRecorder.stop() を呼び出します...");
                mediaRecorder.stop();
                sendToLog("[State: Initial] 録音を停止しました。");

                // ファイルサイズの情報をログに出力
                File file = new File(outputFilePath);
                if (file.exists()) {
                    long fileSize = file.length();
                    sendToLog("FILE_IO: ファイルが正常に保存されました。");
                    sendToLog(String.format(Locale.JAPAN, "FILE_INFO: ファイルサイズ: %,d bytes (%.2f KB)", fileSize, fileSize / 1024.0));
                } else {
                    sendToLog("WARN: 保存されたはずのファイルが見つかりません。");
                }

            } catch (IllegalStateException e) {
                // 既に停止している場合などに発生する可能性がある
                sendToLog("WARN: MediaRecorder.stop() で例外発生 (おそらく既に停止済み): " + e.getMessage());
            } finally {
                // 9. リソースを解放 (State: Initial -> Released)
                sendToLog("[State: Initial] MediaRecorder.release() を呼び出します...");
                mediaRecorder.release();
                mediaRecorder = null;
                sendToLog("[State: Released] リソースを解放しました。");
                sendToLog("録音が完了しました。ファイルが保存されました: " + outputFilePath);
            }
        } else {
            sendToLog("INFO: MediaRecorderは既にnullのため、停止処理をスキップします。");
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        sendToLog("--------------------");
        sendToLog("LIFECYCLE: RecordingService#onDestroy: サービスが破棄されます。");
        if (isRecording) { // ←←← 条件を追加
            stopRecording(); // 録音中だった場合のみ停止処理を呼ぶ
        }
        sendToLog("LIFECYCLE: サービスが完全に停止しました。");
        sendToLog("--------------------");
    }


    /**
     * アプリ固有の外部ストレージ領域に、録音ファイルの保存パスを生成する
     * この領域は、ユーザーからは直接見えにくく、アプリがアンインストールされると自動的に削除される
     * @return ファイルのフルパス
     */
    private String getOutputFilePath() {
        // getExternalFilesDir(null)で /Android/data/パッケージ名/files を指す
        File mediaStorageDir = getExternalFilesDir(null);
        if (mediaStorageDir == null) {
            sendToLog("ERROR: アプリ固有ストレージにアクセスできません。");
            return null;
        }
        if (!mediaStorageDir.exists()) {
            sendToLog("FILE_IO: 保存先ディレクトリが存在しないため、作成を試みます...");
            if (!mediaStorageDir.mkdirs()) {
                sendToLog("ERROR: 保存先ディレクトリの作成に失敗しました。");
                return null;
            }
            sendToLog("FILE_IO: 保存先ディレクトリを作成しました。");
        }
        // ファイル名は現在の日時を使って一意にする (例: REC_20250621_183000.mp4)
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.JAPAN).format(new Date());
        return mediaStorageDir.getPath() + File.separator + "REC_" + timeStamp + ".mp4";
    }

    /**
     * 通知チャンネルを作成する (Android 8.0以降で必須)
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "録音サービスチャンネル",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    /**
     * ログメッセージをMainActivityに送信する
     * @param message 送信するログメッセージ
     */
    private void sendToLog(String message) {
        Intent intent = new Intent(ACTION_UPDATE_LOG);
        intent.putExtra(EXTRA_LOG_MESSAGE, message);
        sendBroadcast(intent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // 今回はバインドしないのでnullを返す
        return null;
    }
}