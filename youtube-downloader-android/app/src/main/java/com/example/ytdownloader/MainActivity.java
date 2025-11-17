package com.example.ytdownloader;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLException;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.YoutubeDLResponse;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TextInputEditText urlInput;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        urlInput = findViewById(R.id.url_input);
        statusText = findViewById(R.id.status_text);
        Button downloadButton = findViewById(R.id.download_button);

        downloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = urlInput.getText() != null ? urlInput.getText().toString().trim() : "";
                if (TextUtils.isEmpty(url)) {
                    statusText.setText(R.string.download_hint);
                    return;
                }
                startDownload(url);
            }
        });
    }

    private void startDownload(String url) {
        statusText.setText(R.string.status_working);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                YoutubeDLRequest request = new YoutubeDLRequest(url);
                request.addOption("-o", new File(downloads, "yt-%(title)s.%(ext)s").getAbsolutePath());

                try {
                    YoutubeDL.getInstance().init(getApplication());
                    YoutubeDLResponse response = YoutubeDL.getInstance().execute(request);
                    Log.d("MainActivity", response.getOut());
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText(R.string.status_saved);
                        }
                    });
                } catch (YoutubeDLException e) {
                    Log.e("MainActivity", "youtube-dl init failed", e);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText(R.string.status_error);
                        }
                    });
                } catch (Exception e) {
                    Log.e("MainActivity", "Download failed", e);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText(R.string.status_error);
                        }
                    });
                }
            }
        });
    }
}
