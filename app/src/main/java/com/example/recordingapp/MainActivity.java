package com.example.recordingapp;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.io.File;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private Button btnStart, btnStop;
    private TextView tvStatus, tvLog;
    private ScrollView scrollView;

    // 複数のパーミッションをリクエストするためのランチャー
    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                // 結果をチェックし、すべてのパーミッションが許可されたか確認
                boolean allPermissionsGranted = true;
                for (Boolean granted : result.values()) {
                    if (!granted) {
                        allPermissionsGranted = false;
                        break;
                    }
                }

                if (allPermissionsGranted) {
                    addLog("必要な全てのパーミッションが許可されました。");
                    // 許可されたので、録音を開始する
                    startRecordingService();
                } else {
                    addLog("エラー: 録音に必要なパーミッションが拒否されました。");
                    Toast.makeText(this, "録音を開始するには、すべての権限を許可してください。", Toast.LENGTH_LONG).show();
                }
            });

    // RecordingServiceからのログを受け取るためのBroadcastReceiver
    private final BroadcastReceiver logReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (RecordingService.ACTION_UPDATE_LOG.equals(intent.getAction())) {
                String logMessage = intent.getStringExtra(RecordingService.EXTRA_LOG_MESSAGE);
                if (logMessage != null) {
                    addLog(logMessage);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // UIコンポーネントをIDで取得
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        tvStatus = findViewById(R.id.tvStatus);
        tvLog = findViewById(R.id.tvLog);
        scrollView = findViewById(R.id.scrollView);

        // ログエリアをスクロール可能にする
        tvLog.setMovementMethod(new ScrollingMovementMethod());
        addLog("アプリを起動しました。");

        // 録音開始ボタンのクリックイベント
        btnStart.setOnClickListener(v -> {
            addLog("録音開始ボタンが押されました。");
            checkPermissionsAndStart();
        });

        // 録音終了ボタンのクリックイベント
        btnStop.setOnClickListener(v -> {
            addLog("録音終了ボタンが押されました。");
            stopRecordingService();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // サービスからのログを受け取るBroadcastReceiverを登録します。
        // ContextCompat を使うことで、OSのバージョンによる分岐を書かずに、
        // 安全にレシーバーを登録できます。

        IntentFilter filter = new IntentFilter(RecordingService.ACTION_UPDATE_LOG);

        // RECEIVER_NOT_EXPORTED を指定することで、このレシーバーが
        // このアプリ内からのブロードキャストのみを受け取ることを明示します。
        // これにより、セキュリティが向上します。
        ContextCompat.registerReceiver(this, logReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Activityが非表示になるときにBroadcastReceiverの登録を解除
        unregisterReceiver(logReceiver);
    }

    /**
     * 録音を開始するために、必要なパーミッションを確認し、なければリクエストする
     */
    private void checkPermissionsAndStart() {
        addLog("パーミッションのチェックを開始します...");

        // 必要なパーミッションのリスト
        List<String> requiredPermissions = new ArrayList<>();
        requiredPermissions.add(Manifest.permission.RECORD_AUDIO);

        // Android 13 (API 33) 以上では通知のパーミッションも必要
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        // 許可されていないパーミッションを特定
        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
                addLog(permission + " の許可がありません。");
            } else {
                addLog(permission + " は既に許可されています。");
            }
        }

        // 許可されていないパーミッションがあれば、ユーザーにリクエスト
        if (!permissionsToRequest.isEmpty()) {
            addLog("ユーザーにパーミッションの許可を求めます。");
            requestPermissionLauncher.launch(permissionsToRequest.toArray(new String[0]));
        } else {
            // 全てのパーミッションが既に許可されている場合
            addLog("全てのパーミッションは既に許可されています。");
            startRecordingService();
        }
    }

    /**
     * 録音サービスを開始する
     */
    private void startRecordingService() {
        addLog("RecordingServiceの開始を試みます。");
        Intent serviceIntent = new Intent(this, RecordingService.class);
        serviceIntent.setAction(RecordingService.ACTION_START_RECORDING);

        // Android 8.0 (API 26) 以降では、フォアグラウンドサービスとして開始する必要がある
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        updateUI(true); // UIを「録音中」状態に更新
    }

    /**
     * 録音サービスを停止する
     */
    private void stopRecordingService() {
        addLog("RecordingServiceの停止を試みます。");
        Intent serviceIntent = new Intent(this, RecordingService.class);
        serviceIntent.setAction(RecordingService.ACTION_STOP_RECORDING);
        startService(serviceIntent);
        updateUI(false); // UIを「待機中」状態に更新

        // ↓↓↓ ここからが最後の追加部分です ↓↓↓
        // 完了報告を待たずに、UIに直接メッセージを表示する
        addLog("--------------------");
        addLog("FILE_IO: 録音を停止し、ファイルの保存処理を開始しました。");
        addLog("FILE_IO: 保存先は以下のフォルダを確認してください。");
        // getExternalFilesDir(null) を使って、保存先フォルダのパスを取得して表示
        File storageDir = getExternalFilesDir(null);
        if (storageDir != null) {
            addLog("PATH: " + storageDir.getAbsolutePath());
        }
        addLog("--------------------");
    }

    /**
     * 録音状態に応じてUIを更新する
     * @param isRecording 録音中かどうか
     */
    private void updateUI(boolean isRecording) {
        if (isRecording) {
            // 録音中のUI設定
            tvStatus.setText("🔴録音中");
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
            addLog("UIを「録音中」状態に更新しました。");
        } else {
            // 待機中のUI設定
            tvStatus.setText("待機中");
            // デフォルトのテキストカラーに戻す (テーマによって色が変わるように)
            tvStatus.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Large);
            btnStart.setEnabled(true);
            btnStop.setEnabled(false);
            addLog("UIを「待機中」状態に更新しました。");
        }
    }

    /**
     * ログ表示用のTextViewに新しいログメッセージを追加する
     * @param message ログメッセージ
     */
    private void addLog(String message) {
        // 現在時刻を取得してフォーマット
        String currentTime = new SimpleDateFormat("HH:mm:ss.SSS", Locale.JAPAN).format(new Date());
        String logText = currentTime + " - " + message + "\n";

        // UIスレッドでTextViewを更新
        tvLog.append(logText);

        // 自動的に一番下までスクロール
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }
}