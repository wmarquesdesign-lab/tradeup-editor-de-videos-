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

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView webView;
    private String selectedVideoPath = "";
    private String selectedVideoName = "video.mp4";
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

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        try {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }

            String fileName = getFileName(uri);
            if (requestCode == PICK_VIDEO) {
                selectedVideoName = ensureMp4Name(fileName);
                selectedVideoPath = copyToCache(uri, "input_video");
                callJs("setVideoName", fileName);
                callJs("setStatus", "Vídeo importado. Pronto para salvar/exportar.");
            } else if (requestCode == PICK_AUDIO) {
                callJs("setAudioName", fileName);
                callJs("setStatus", "Áudio escolhido. Nesta versão de build estável, o APK salva o vídeo original.");
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
        String result = "arquivo.mp4";
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        try {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) result = cursor.getString(idx);
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return result == null || result.trim().isEmpty() ? "arquivo.mp4" : result;
    }

    private String ensureMp4Name(String name) {
        String clean = name == null ? "tradeup_video.mp4" : name.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (!clean.toLowerCase().endsWith(".mp4")) {
            int dot = clean.lastIndexOf('.');
            if (dot > 0) clean = clean.substring(0, dot);
            clean += ".mp4";
        }
        return "tradeup_" + System.currentTimeMillis() + "_" + clean;
    }

    private String copyToCache(Uri uri, String prefix) throws Exception {
        String name = getFileName(uri);
        String ext = ".mp4";
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
                callJs("setStatus", "Escolha um vídeo primeiro.");
                return;
            }

            String preset = "video";
            try {
                JSONObject p = new JSONObject(json);
                preset = p.optString("preset", "video").replaceAll("[^a-zA-Z0-9_-]", "");
            } catch (Exception ignored) {
            }

            File source = new File(selectedVideoPath);
            String outputName = "tradeup_" + preset + "_" + System.currentTimeMillis() + "_" + selectedVideoName;
            callJs("setStatus", "Salvando vídeo na galeria...");
            saveVideoToGallery(source, outputName);
            callJs("setStatus", "Concluído. Salvo na galeria em Movies/TradeUpVideosPro");
            Toast.makeText(this, "Vídeo salvo na galeria", Toast.LENGTH_LONG).show();
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

        Uri item = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        if (item == null) throw new Exception("MediaStore retornou vazio");

        try (InputStream in = new FileInputStream(source);
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
