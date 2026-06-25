package com.tradeup.videos;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.ReturnCode;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView webView;
    private String selectedVideoPath = "";
    private String selectedAudioPath = "";
    private static final int PICK_VIDEO = 10;
    private static final int PICK_AUDIO = 11;

    public class AndroidBridge {
        @JavascriptInterface
        public void pickVideo() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.setType("video/*");
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(i, PICK_VIDEO);
            });
        }

        @JavascriptInterface
        public void pickAudio() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.setType("audio/*");
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(i, PICK_AUDIO);
            });
        }

        @JavascriptInterface
        public void exportVideo(String json) {
            runOnUiThread(() -> startExport(json));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestReadPermissionsIfNeeded();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void requestReadPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO}, 5);
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 5);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        try {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}

            if (requestCode == PICK_VIDEO) {
                selectedVideoPath = copyToCache(uri, "input_video");
                callJs("setVideoName", getFileName(uri));
            } else if (requestCode == PICK_AUDIO) {
                selectedAudioPath = copyToCache(uri, "input_audio");
                callJs("setAudioName", getFileName(uri));
            }
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao importar: " + e.getMessage(), Toast.LENGTH_LONG).show();
            callJs("setStatus", "Erro ao importar: " + e.getMessage());
        }
    }

    private void callJs(String fn, String value) {
        String safe = value == null ? "" : value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ");
        webView.evaluateJavascript(fn + "('" + safe + "')", null);
    }

    private String getFileName(Uri uri) {
        String result = "arquivo";
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        try {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) result = cursor.getString(idx);
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return result == null || result.trim().isEmpty() ? "arquivo" : result;
    }

    private String copyToCache(Uri uri, String prefix) throws Exception {
        String name = getFileName(uri);
        String ext = "";
        int dot = name.lastIndexOf(".");
        if (dot >= 0) ext = name.substring(dot).replaceAll("[^a-zA-Z0-9.]", "");
        File out = new File(getCacheDir(), prefix + "_" + System.currentTimeMillis() + ext);

        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(out)) {
            if (in == null) throw new Exception("não consegui abrir o arquivo");
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) fos.write(buffer, 0, len);
        }
        return out.getAbsolutePath();
    }

    private void startExport(String json) {
        try {
            if (selectedVideoPath.isEmpty()) {
                Toast.makeText(this, "Escolha um vídeo primeiro.", Toast.LENGTH_LONG).show();
                return;
            }

            JSONObject p = new JSONObject(json);
            int w = p.getInt("width");
            int h = p.getInt("height");
            String mode = p.getString("mode");
            String preset = p.getString("preset").replaceAll("[^a-zA-Z0-9_-]", "");

            File tempDir = new File(getCacheDir(), "exports");
            if (!tempDir.exists() && !tempDir.mkdirs()) throw new Exception("não consegui criar pasta temporária");
            File tempOut = new File(tempDir, "tradeup_" + preset + "_" + System.currentTimeMillis() + ".mp4");

            String vf;
            if ("cover".equals(mode)) {
                vf = "scale=" + w + ":" + h + ":force_original_aspect_ratio=increase,crop=" + w + ":" + h;
            } else if ("blur".equals(mode)) {
                vf = "[0:v]scale=" + w + ":" + h + ":force_original_aspect_ratio=increase,crop=" + w + ":" + h + ",boxblur=30:1[bg];" +
                        "[0:v]scale=" + w + ":" + h + ":force_original_aspect_ratio=decrease[fg];" +
                        "[bg][fg]overlay=(W-w)/2:(H-h)/2";
            } else {
                vf = "scale=" + w + ":" + h + ":force_original_aspect_ratio=decrease,pad=" + w + ":" + h + ":(ow-iw)/2:(oh-ih)/2:black";
            }

            String cmd;
            if (!selectedAudioPath.isEmpty()) {
                cmd = "-y -i \"" + selectedVideoPath + "\" -i \"" + selectedAudioPath + "\" " +
                        "-filter_complex \"" + vf + "\" " +
                        "-map 0:v:0 -map 1:a:0 -c:v libx264 -preset veryfast -crf 20 -r 30 -pix_fmt yuv420p " +
                        "-c:a aac -b:a 192k -shortest \"" + tempOut.getAbsolutePath() + "\"";
            } else {
                cmd = "-y -i \"" + selectedVideoPath + "\" " +
                        "-vf \"" + vf + "\" " +
                        "-c:v libx264 -preset veryfast -crf 20 -r 30 -pix_fmt yuv420p " +
                        "-an \"" + tempOut.getAbsolutePath() + "\"";
            }

            callJs("setStatus", "Exportando... aguarde");
            FFmpegKit.executeAsync(cmd, session -> {
                ReturnCode rc = session.getReturnCode();
                runOnUiThread(() -> {
                    if (ReturnCode.isSuccess(rc)) {
                        try {
                            Uri savedUri = saveVideoToGallery(tempOut, tempOut.getName());
                            callJs("setStatus", "Concluído. Salvo na galeria em Movies/TradeUpVideosPro");
                            Toast.makeText(this, "Vídeo salvo na galeria", Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            callJs("setStatus", "Exportou, mas falhou ao salvar na galeria: " + e.getMessage());
                            Toast.makeText(this, "Exportou, mas não salvou na galeria.", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        String log = session.getAllLogsAsString();
                        if (log != null && log.length() > 250) log = log.substring(log.length() - 250);
                        callJs("setStatus", "Erro na exportação. " + (log == null ? "" : log));
                        Toast.makeText(this, "Erro ao exportar vídeo.", Toast.LENGTH_LONG).show();
                    }
                });
            });
        } catch (Exception e) {
            Toast.makeText(this, "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show();
            callJs("setStatus", "Erro: " + e.getMessage());
        }
    }

    private Uri saveVideoToGallery(File source, String fileName) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        if (Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/TradeUpVideosPro");
            values.put(MediaStore.Video.Media.IS_PENDING, 1);
        }

        Uri collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        Uri item = getContentResolver().insert(collection, values);
        if (item == null) throw new Exception("MediaStore retornou vazio");

        try (InputStream in = new java.io.FileInputStream(source);
             OutputStream out = getContentResolver().openOutputStream(item)) {
            if (out == null) throw new Exception("não abriu saída");
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) out.write(buffer, 0, len);
        }

        if (Build.VERSION.SDK_INT >= 29) {
            values.clear();
            values.put(MediaStore.Video.Media.IS_PENDING, 0);
            getContentResolver().update(item, values, null, null);
        }
        return item;
    }
}
