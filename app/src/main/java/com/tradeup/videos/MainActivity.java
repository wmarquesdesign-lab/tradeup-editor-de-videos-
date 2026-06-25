package com.tradeup.videos;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;
import com.arthenica.ffmpegkit.Statistics;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {
    private WebView webView;
    private final ArrayList<ProjectVideo> videos = new ArrayList<>();
    private final ArrayList<ProjectImage> images = new ArrayList<>();
    private Uri selectedAudioUri = null;
    private String selectedAudioName = "";
    private String selectedAudioPreview = "";
    private String pendingExportJson = "{}";
    private String pendingExportName = "tradeup_video.mp4";
    private String pendingProjectJson = "{}";

    private static final int PICK_VIDEOS = 10;
    private static final int PICK_AUDIO = 11;
    private static final int CREATE_EXPORT_FILE = 12;
    private static final int PICK_IMAGES = 13;
    private static final int CREATE_PROJECT_FILE = 14;
    private static final int OPEN_PROJECT_FILE = 15;
    private static final int CREATE_PROJECT_PACKAGE = 16;

    private static class ProjectVideo {
        Uri uri;
        String name;
        long durationMs;
        String previewUrl;

        ProjectVideo(Uri uri, String name, long durationMs, String previewUrl) {
            this.uri = uri;
            this.name = name;
            this.durationMs = durationMs;
            this.previewUrl = previewUrl;
        }
    }

    private static class ProjectImage {
        Uri uri;
        String name;
        String previewUrl;

        ProjectImage(Uri uri, String name, String previewUrl) {
            this.uri = uri;
            this.name = name;
            this.previewUrl = previewUrl;
        }
    }

    public class AndroidBridge {
        @JavascriptInterface
        public void pickVideos() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.setType("video/*");
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(i, PICK_VIDEOS);
            });
        }

        @JavascriptInterface
        public void pickVideo() { pickVideos(); }

        @JavascriptInterface
        public void pickImages() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.setType("image/*");
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(i, PICK_IMAGES);
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
        public void exportVideo(String json) { runOnUiThread(() -> openExportChooser(json)); }

        @JavascriptInterface
        public void saveProjectFile(String json) { runOnUiThread(() -> openProjectSaveChooser(json)); }

        @JavascriptInterface
        public void openProjectFile() { runOnUiThread(() -> openProjectImportChooser()); }

        @JavascriptInterface
        public void exportProjectPackage(String json) { runOnUiThread(() -> openProjectPackageChooser(json)); }

        @JavascriptInterface
        public void toast(String text) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestReadPermissionsIfNeeded();
        clearPreviewCache();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= 16) {
            settings.setAllowFileAccessFromFileURLs(true);
            settings.setAllowUniversalAccessFromFileURLs(true);
        }

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void requestReadPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            ArrayList<String> perms = new ArrayList<>();
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.READ_MEDIA_VIDEO);
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.READ_MEDIA_AUDIO);
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.READ_MEDIA_IMAGES);
            if (!perms.isEmpty()) requestPermissions(perms.toArray(new String[0]), 5);
        } else if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 5);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        try {
            if (requestCode == PICK_VIDEOS) handlePickedVideos(data);
            else if (requestCode == PICK_AUDIO) handlePickedAudio(data);
            else if (requestCode == PICK_IMAGES) handlePickedImages(data);
            else if (requestCode == CREATE_EXPORT_FILE) {
                Uri out = data.getData();
                if (out != null) exportToUri(out);
            } else if (requestCode == CREATE_PROJECT_FILE) {
                Uri out = data.getData();
                if (out != null) saveProjectJsonToUri(out);
            } else if (requestCode == OPEN_PROJECT_FILE) {
                Uri in = data.getData();
                if (in != null) importProjectJsonFromUri(in);
            } else if (requestCode == CREATE_PROJECT_PACKAGE) {
                Uri out = data.getData();
                if (out != null) saveProjectPackageToUri(out);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show();
            callJs("setStatus", "Erro: " + e.getMessage());
        }
    }

    private void handlePickedVideos(Intent data) throws Exception {
        // Sprint 7: novas seleções são adicionadas à timeline sem apagar o projeto; o WebView preserva edições anteriores.
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int idx = 0; idx < clipData.getItemCount(); idx++) addVideoUri(clipData.getItemAt(idx).getUri());
        } else if (data.getData() != null) addVideoUri(data.getData());
        callJsRaw("setProjectVideos(" + videosToJson().toString() + ")");
        callJs("setStatus", videos.size() + " vídeo(s) na timeline. Novos arquivos foram adicionados sem apagar o projeto.");
    }

    private void addVideoUri(Uri uri) {
        tryTakeReadPermission(uri);
        String name = getFileName(uri, "video.mp4");
        long duration = getMediaDuration(uri);
        String preview = copyToPreviewCache(uri, "video_" + videos.size() + "_" + sanitizeFileName(name));
        videos.add(new ProjectVideo(uri, name, duration, preview));
    }

    private void handlePickedImages(Intent data) throws Exception {
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int idx = 0; idx < clipData.getItemCount(); idx++) addImageUri(clipData.getItemAt(idx).getUri());
        } else if (data.getData() != null) addImageUri(data.getData());
        callJsRaw("setProjectImages(" + imagesToJson().toString() + ")");
        callJs("setStatus", images.size() + " imagem(ns) adicionada(s) ao projeto.");
    }

    private void addImageUri(Uri uri) {
        tryTakeReadPermission(uri);
        String name = getFileName(uri, "imagem.jpg");
        String preview = copyToPreviewCache(uri, "img_" + images.size() + "_" + sanitizeFileName(name));
        images.add(new ProjectImage(uri, name, preview));
    }

    private void handlePickedAudio(Intent data) throws Exception {
        Uri uri = data.getData();
        if (uri == null) return;
        tryTakeReadPermission(uri);
        selectedAudioUri = uri;
        selectedAudioName = getFileName(uri, "musica.mp3");
        selectedAudioPreview = copyToPreviewCache(uri, "audio_" + sanitizeFileName(selectedAudioName));
        long duration = getMediaDuration(uri);

        JSONObject audio = new JSONObject();
        audio.put("name", selectedAudioName);
        audio.put("uri", selectedAudioUri.toString());
        audio.put("previewUrl", selectedAudioPreview);
        audio.put("durationMs", duration);
        callJsRaw("setAudioTrack(" + audio.toString() + ")");
        callJs("setStatus", "Música importada: " + selectedAudioName);
    }

    private void tryTakeReadPermission(Uri uri) {
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
    }

    private JSONArray videosToJson() throws Exception {
        JSONArray arr = new JSONArray();
        for (int i = 0; i < videos.size(); i++) {
            ProjectVideo v = videos.get(i);
            JSONObject o = new JSONObject();
            o.put("id", i);
            o.put("name", v.name);
            o.put("uri", v.uri.toString());
            o.put("previewUrl", v.previewUrl);
            o.put("durationMs", v.durationMs);
            arr.put(o);
        }
        return arr;
    }

    private JSONArray imagesToJson() throws Exception {
        JSONArray arr = new JSONArray();
        for (int i = 0; i < images.size(); i++) {
            ProjectImage img = images.get(i);
            JSONObject o = new JSONObject();
            o.put("id", i);
            o.put("name", img.name);
            o.put("uri", img.uri.toString());
            o.put("previewUrl", img.previewUrl);
            arr.put(o);
        }
        return arr;
    }


    private void openProjectSaveChooser(String json) {
        try {
            pendingProjectJson = json == null ? "{}" : json;
            String baseName = "tradeup_projeto_s5.tradeup.json";
            try {
                JSONObject p = new JSONObject(pendingProjectJson);
                String wanted = p.optJSONObject("settings") != null ? p.optJSONObject("settings").optString("fileName", "tradeup_projeto_s5") : "tradeup_projeto_s5";
                wanted = sanitizeFileName(wanted.replace(".mp4", ""));
                if (!wanted.isEmpty()) baseName = wanted + ".tradeup.json";
            } catch (Exception ignored) {}
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE, baseName);
            startActivityForResult(i, CREATE_PROJECT_FILE);
            callJs("setStatus", "Escolha onde salvar o arquivo do projeto.");
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao preparar projeto: " + e.getMessage(), Toast.LENGTH_LONG).show();
            callJs("setStatus", "Erro ao preparar projeto: " + e.getMessage());
        }
    }

    private void openProjectImportChooser() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("*/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(i, OPEN_PROJECT_FILE);
            callJs("setStatus", "Escolha um arquivo .tradeup.json para importar.");
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao abrir projeto: " + e.getMessage(), Toast.LENGTH_LONG).show();
            callJs("setStatus", "Erro ao abrir projeto: " + e.getMessage());
        }
    }

    private void saveProjectJsonToUri(Uri outUri) {
        try (OutputStream out = getContentResolver().openOutputStream(outUri)) {
            if (out == null) throw new Exception("não foi possível abrir o arquivo de saída");
            out.write(pendingProjectJson.getBytes("UTF-8"));
            out.flush();
            Toast.makeText(this, "Projeto salvo", Toast.LENGTH_LONG).show();
            callJs("setStatus", "Projeto salvo em arquivo JSON.");
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao salvar projeto: " + e.getMessage(), Toast.LENGTH_LONG).show();
            callJs("setStatus", "Erro ao salvar projeto: " + e.getMessage());
        }
    }

    private void importProjectJsonFromUri(Uri inUri) {
        tryTakeReadPermission(inUri);
        try (InputStream in = getContentResolver().openInputStream(inUri)) {
            if (in == null) throw new Exception("não foi possível abrir o arquivo");
            byte[] buffer = new byte[1024 * 64];
            int len;
            StringBuilder sb = new StringBuilder();
            while ((len = in.read(buffer)) > 0) sb.append(new String(buffer, 0, len, "UTF-8"));
            String json = sb.toString().trim();
            if (!json.startsWith("{")) throw new Exception("arquivo não parece ser um projeto TradeUp");
            callJsRaw("loadProjectFromNative(" + JSONObject.quote(json) + ")");
            Toast.makeText(this, "Projeto importado", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao importar projeto: " + e.getMessage(), Toast.LENGTH_LONG).show();
            callJs("setStatus", "Erro ao importar projeto: " + e.getMessage());
        }
    }

    private void openProjectPackageChooser(String json) {
        try {
            pendingProjectJson = json == null ? "{}" : json;
            String baseName = "tradeup_projeto_s7.zip";
            try {
                JSONObject p = new JSONObject(pendingProjectJson);
                String wanted = p.optJSONObject("settings") != null ? p.optJSONObject("settings").optString("fileName", "tradeup_projeto_s7") : "tradeup_projeto_s7";
                wanted = sanitizeFileName(wanted.replace(".mp4", ""));
                if (!wanted.isEmpty()) baseName = wanted + "_pacote_tradeup.zip";
            } catch (Exception ignored) {}
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/zip");
            i.putExtra(Intent.EXTRA_TITLE, baseName);
            startActivityForResult(i, CREATE_PROJECT_PACKAGE);
            callJs("setStatus", "Escolha onde salvar o pacote ZIP completo do projeto.");
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao preparar pacote: " + e.getMessage(), Toast.LENGTH_LONG).show();
            callJs("setStatus", "Erro ao preparar pacote: " + e.getMessage());
        }
    }

    private void saveProjectPackageToUri(Uri outUri) {
        try {
            OutputStream raw = getContentResolver().openOutputStream(outUri);
            if (raw == null) throw new Exception("não foi possível abrir o pacote de saída");
            try (ZipOutputStream zip = new ZipOutputStream(raw)) {
            callJsRaw("setExportProgress(3, 'Criando pacote ZIP do projeto...')");
            addTextEntry(zip, "projeto.tradeup.json", pendingProjectJson == null ? "{}" : pendingProjectJson);
            addTextEntry(zip, "LEIA-ME.txt", "Pacote TradeUp Videos Studio S7\nContém o projeto JSON e cópias dos vídeos/imagens/áudio selecionados.\nUse este pacote para backup e continuação do projeto.\n");
            int totalItems = Math.max(1, videos.size() + images.size() + (selectedAudioUri != null ? 1 : 0));
            int done = 0;
            for (int i = 0; i < videos.size(); i++) {
                ProjectVideo v = videos.get(i);
                addUriEntry(zip, v.uri, "media/videos/" + String.format("%03d_", i + 1) + sanitizeFileName(v.name));
                done++;
                callJsRaw("setExportProgress(" + Math.min(95, (done * 90) / totalItems) + ", 'Empacotando vídeos... " + done + "/" + totalItems + "')");
            }
            for (int i = 0; i < images.size(); i++) {
                ProjectImage img = images.get(i);
                addUriEntry(zip, img.uri, "media/images/" + String.format("%03d_", i + 1) + sanitizeFileName(img.name));
                done++;
                callJsRaw("setExportProgress(" + Math.min(95, (done * 90) / totalItems) + ", 'Empacotando imagens... " + done + "/" + totalItems + "')");
            }
            if (selectedAudioUri != null) {
                addUriEntry(zip, selectedAudioUri, "media/audio/" + sanitizeFileName(selectedAudioName));
            }
            zip.finish();
            }
            callJsRaw("setExportProgress(100, 'Pacote ZIP concluído.')");
            Toast.makeText(this, "Pacote do projeto salvo", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao salvar pacote: " + e.getMessage(), Toast.LENGTH_LONG).show();
            callJs("setStatus", "Erro ao salvar pacote: " + e.getMessage());
        }
    }

    private void addTextEntry(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write((content == null ? "" : content).getBytes("UTF-8"));
        zip.closeEntry();
    }

    private void addUriEntry(ZipOutputStream zip, Uri uri, String entryName) throws Exception {
        zip.putNextEntry(new ZipEntry(entryName));
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("não foi possível abrir mídia: " + entryName);
            byte[] buffer = new byte[1024 * 128];
            int len;
            while ((len = in.read(buffer)) > 0) zip.write(buffer, 0, len);
        }
        zip.closeEntry();
    }

    private void openExportChooser(String json) {
        try {
            if (videos.isEmpty()) {
                Toast.makeText(this, "Escolha pelo menos um vídeo primeiro.", Toast.LENGTH_LONG).show();
                callJs("setStatus", "Escolha pelo menos um vídeo primeiro.");
                return;
            }
            pendingExportJson = json == null ? "{}" : json;
            JSONObject p = new JSONObject(pendingExportJson);
            String requestedName = p.optString("fileName", "tradeup_video").trim();
            pendingExportName = sanitizeFileName(requestedName);
            if (!pendingExportName.toLowerCase().endsWith(".mp4")) pendingExportName += ".mp4";

            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("video/mp4");
            i.putExtra(Intent.EXTRA_TITLE, pendingExportName);
            startActivityForResult(i, CREATE_EXPORT_FILE);
            callJs("setStatus", "Escolha a pasta e confirme o nome do arquivo para salvar.");
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao exportar: " + e.getMessage(), Toast.LENGTH_LONG).show();
            callJs("setStatus", "Erro ao exportar: " + e.getMessage());
        }
    }


    private void exportToUri(Uri outUri) {
        try {
            if (videos.isEmpty()) throw new Exception("nenhum vídeo no projeto");
            callJsRaw("setExportProgress(3, 'Preparando renderização real com FFmpeg...')");

            JSONObject project = new JSONObject(pendingExportJson == null || pendingExportJson.trim().isEmpty() ? "{}" : pendingExportJson);
            JSONObject settings = project.optJSONObject("settings");
            if (settings == null) settings = new JSONObject();
            JSONArray projectVideos = project.optJSONArray("videos");
            if (projectVideos == null || projectVideos.length() == 0) throw new Exception("timeline vazia");

            String resolution = settings.optString("resolution", "1080p");
            String format = settings.optString("format", "vertical");
            int fps = Math.max(24, settings.optInt("fps", 30));
            int[] size = resolveExportSize(resolution, format);
            int width = size[0], height = size[1];

            File renderDir = new File(getCacheDir(), "ffmpeg_render");
            deleteRecursive(renderDir);
            renderDir.mkdirs();

            File outputFile = new File(renderDir, "tradeup_render_" + System.currentTimeMillis() + ".mp4");
            ArrayList<File> inputFiles = new ArrayList<>();
            ArrayList<JSONObject> timeline = new ArrayList<>();
            HashMap<String, File> sourceMap = new HashMap<>();

            for (int i = 0; i < videos.size(); i++) {
                ProjectVideo base = videos.get(i);
                File cached = new File(renderDir, String.format(Locale.US, "video_%03d_%s", i, sanitizeFileName(base.name)));
                copyUriToFile(base.uri, cached);
                sourceMap.put(base.uri.toString(), cached);
                sourceMap.put(base.name, cached);
            }

            for (int i = 0; i < projectVideos.length(); i++) {
                JSONObject v = projectVideos.optJSONObject(i);
                if (v == null) continue;
                File source = findSourceFileForVideo(v, sourceMap, i);
                if (source == null || !source.exists()) continue;
                v.put("_renderPath", source.getAbsolutePath());
                timeline.add(v);
                inputFiles.add(source);
            }
            if (timeline.isEmpty()) throw new Exception("não foi possível preparar os vídeos da timeline");

            File audioFile = null;
            JSONObject audioJson = project.optJSONObject("audio");
            if (selectedAudioUri != null) {
                audioFile = new File(renderDir, "audio_" + sanitizeFileName(selectedAudioName == null ? "musica" : selectedAudioName));
                copyUriToFile(selectedAudioUri, audioFile);
            }

            String command = buildFfmpegRenderCommand(timeline, project, audioFile, outputFile, width, height, fps, settings);
            callJsRaw("setExportProgress(8, 'Renderizando vídeo final: cortes, união, filtros, texto, música e transições...')");

            final Uri finalOutUri = outUri;
            final File finalOutputFile = outputFile;
            final long estimatedMs = Math.max(1000, estimateTimelineDuration(timeline));
            FFmpegKit.executeAsync(command, (FFmpegSession session) -> {
                ReturnCode rc = session.getReturnCode();
                runOnUiThread(() -> {
                    try {
                        if (ReturnCode.isSuccess(rc) && finalOutputFile.exists() && finalOutputFile.length() > 0) {
                            callJsRaw("setExportProgress(96, 'Copiando MP4 para o local escolhido...')");
                            copyFileToUri(finalOutputFile, finalOutUri);
                            callJsRaw("setExportProgress(100, 'Concluído: " + escapeForJs(pendingExportName) + " renderizado com sucesso.')");
                            Toast.makeText(this, "Exportação real concluída", Toast.LENGTH_LONG).show();
                        } else {
                            String logs = session.getAllLogsAsString();
                            String msg = logs == null ? "FFmpeg falhou" : logs.substring(Math.max(0, logs.length() - Math.min(900, logs.length())));
                            callJs("setStatus", "Erro FFmpeg: " + msg);
                            Toast.makeText(this, "Erro na renderização FFmpeg", Toast.LENGTH_LONG).show();
                        }
                    } catch (Exception e) {
                        callJs("setStatus", "Erro ao finalizar exportação: " + e.getMessage());
                        Toast.makeText(this, "Erro ao finalizar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }, log -> {}, (Statistics statistics) -> {
                if (statistics == null) return;
                int pct = 8 + (int)Math.min(86, Math.max(0, (statistics.getTime() * 86.0) / estimatedMs));
                callJsRaw("setExportProgress(" + pct + ", 'Renderizando MP4... " + pct + "%')");
            });
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao iniciar renderização: " + e.getMessage(), Toast.LENGTH_LONG).show();
            callJs("setStatus", "Erro ao iniciar renderização: " + e.getMessage());
        }
    }

    private String buildFfmpegRenderCommand(ArrayList<JSONObject> timeline, JSONObject project, File audioFile, File outputFile,
                                            int width, int height, int fps, JSONObject settings) throws Exception {
        StringBuilder cmd = new StringBuilder();
        cmd.append("-y ");
        for (JSONObject v : timeline) cmd.append("-i ").append(q(v.getString("_renderPath"))).append(" ");
        if (audioFile != null && audioFile.exists()) cmd.append("-i ").append(q(audioFile.getAbsolutePath())).append(" ");

        JSONObject fx = settings.optJSONObject("effects");
        if (fx == null) fx = new JSONObject();
        String eq = buildEqFilter(fx);
        StringBuilder fc = new StringBuilder();
        ArrayList<String> prepared = new ArrayList<>();

        for (int i = 0; i < timeline.size(); i++) {
            JSONObject v = timeline.get(i);
            double trimIn = Math.max(0, v.optDouble("trimInMs", v.optDouble("trimIn", 0)) / 1000.0);
            double rawOut = v.optDouble("trimOutMs", v.optDouble("trimOut", 0)) / 1000.0;
            double durationSec = Math.max(0.2, ((rawOut > 0 ? rawOut : (v.optDouble("durationMs", 5000) / 1000.0)) - trimIn) / Math.max(0.1, v.optDouble("speed", 1)));
            double speed = Math.max(0.1, v.optDouble("speed", 1));
            String label = "v" + i;
            fc.append("[").append(i).append(":v]")
              .append("trim=start=").append(fmt(trimIn)).append(":duration=").append(fmt(durationSec * speed)).append(",")
              .append("setpts=PTS-STARTPTS");
            if (Math.abs(speed - 1.0) > 0.001) fc.append("/").append(fmt(speed));
            fc.append(",fps=").append(fps).append(",")
              .append("scale=").append(width).append(":").append(height).append(":force_original_aspect_ratio=decrease,")
              .append("pad=").append(width).append(":").append(height).append(":(ow-iw)/2:(oh-ih)/2,")
              .append("setsar=1,format=rgba");
            if (!eq.isEmpty()) fc.append(",").append(eq);
            fc.append("[").append(label).append("];");
            prepared.add(label);
        }

        String videoOut;
        if (prepared.size() == 1) {
            videoOut = prepared.get(0);
        } else {
            double offset = clipDurationSeconds(timeline.get(0));
            String last = prepared.get(0);
            for (int i = 1; i < prepared.size(); i++) {
                JSONObject clip = timeline.get(i);
                String trans = mapTransition(clip.optString("transition", "fade"));
                double transDur = Math.min(Math.max(0.2, clip.optDouble("transitionMs", 700) / 1000.0), 2.0);
                String out = "xf" + i;
                fc.append("[").append(last).append("][").append(prepared.get(i)).append("]")
                  .append("xfade=transition=").append(trans)
                  .append(":duration=").append(fmt(transDur))
                  .append(":offset=").append(fmt(Math.max(0.1, offset - transDur)))
                  .append("[").append(out).append("];");
                offset += clipDurationSeconds(clip) - transDur;
                last = out;
            }
            videoOut = last;
        }

        String finalVideo = "vfinal";
        fc.append("[").append(videoOut).append("]");
        fc.append(buildDrawTextFilters(project.optJSONArray("texts"), width, height));
        fc.append("format=yuv420p[").append(finalVideo).append("];");

        boolean hasAudio = audioFile != null && audioFile.exists();
        String audioOut = "afinal";
        if (hasAudio) {
            int audioInputIndex = timeline.size();
            double totalSec = Math.max(0.5, estimateTimelineDuration(timeline) / 1000.0);
            double vol = settings.optDouble("musicVolume", 0.7);
            double fadeIn = Math.max(0, settings.optDouble("fadeIn", 0));
            double fadeOut = Math.max(0, settings.optDouble("fadeOut", 0));
            fc.append("[").append(audioInputIndex).append(":a]")
              .append("atrim=0:").append(fmt(totalSec)).append(",asetpts=PTS-STARTPTS,volume=").append(fmt(vol));
            if (fadeIn > 0) fc.append(",afade=t=in:st=0:d=").append(fmt(fadeIn));
            if (fadeOut > 0 && totalSec > fadeOut) fc.append(",afade=t=out:st=").append(fmt(totalSec - fadeOut)).append(":d=").append(fmt(fadeOut));
            fc.append("[").append(audioOut).append("];");
        }

        cmd.append("-filter_complex ").append(q(fc.toString())).append(" ");
        cmd.append("-map [").append(finalVideo).append("] ");
        if (hasAudio) cmd.append("-map [").append(audioOut).append("] ");
        else cmd.append("-an ");
        cmd.append("-c:v libx264 -preset veryfast -crf 23 -pix_fmt yuv420p -r ").append(fps).append(" ");
        if (hasAudio) cmd.append("-c:a aac -b:a 192k -shortest ");
        cmd.append("-movflags +faststart ").append(q(outputFile.getAbsolutePath()));
        return cmd.toString();
    }

    private String buildEqFilter(JSONObject fx) {
        double brightness = (fx.optDouble("brightness", 100) - 100.0) / 100.0;
        double contrast = fx.optDouble("contrast", 100) / 100.0;
        double saturation = fx.optDouble("saturation", 100) / 100.0;
        if (Math.abs(brightness) < 0.001 && Math.abs(contrast - 1.0) < 0.001 && Math.abs(saturation - 1.0) < 0.001) return "";
        return "eq=brightness=" + fmt(brightness) + ":contrast=" + fmt(contrast) + ":saturation=" + fmt(saturation);
    }

    private String buildDrawTextFilters(JSONArray texts, int width, int height) {
        if (texts == null || texts.length() == 0) return "";
        StringBuilder sb = new StringBuilder();
        String font = "/system/fonts/Roboto-Regular.ttf";
        for (int i = 0; i < texts.length(); i++) {
            JSONObject t = texts.optJSONObject(i);
            if (t == null) continue;
            String text = escapeDrawText(t.optString("text", ""));
            if (text.trim().isEmpty()) continue;
            double start = t.optDouble("startMs", 0) / 1000.0;
            double end = start + Math.max(0.5, t.optDouble("durationMs", 4000) / 1000.0);
            int size = Math.max(18, t.optInt("size", 42));
            String color = t.optString("color", "#ffffff").replace("#", "0x");
            sb.append(",drawtext=fontfile=").append(escapeDrawText(font))
              .append(":text='").append(text).append("'")
              .append(":fontcolor=").append(color)
              .append(":fontsize=").append(size)
              .append(":borderw=4:bordercolor=black@0.75")
              .append(":x=(w-text_w)/2:y=h-(text_h*4)")
              .append(":enable='between(t,").append(fmt(start)).append(",").append(fmt(end)).append(")'");
        }
        return sb.toString();
    }

    private File findSourceFileForVideo(JSONObject v, HashMap<String, File> sourceMap, int index) {
        String uri = v.optString("uri", "");
        String name = v.optString("name", "");
        if (sourceMap.containsKey(uri)) return sourceMap.get(uri);
        if (sourceMap.containsKey(name)) return sourceMap.get(name);
        if (index >= 0 && index < videos.size()) return sourceMap.get(videos.get(index).uri.toString());
        return null;
    }

    private double clipDurationSeconds(JSONObject v) {
        double inMs = v.optDouble("trimInMs", v.optDouble("trimIn", 0));
        double outMs = v.optDouble("trimOutMs", v.optDouble("trimOut", 0));
        if (outMs <= 0) outMs = v.optDouble("durationMs", 5000);
        return Math.max(0.5, (outMs - inMs) / 1000.0 / Math.max(0.1, v.optDouble("speed", 1)));
    }

    private long estimateTimelineDuration(ArrayList<JSONObject> timeline) {
        double total = 0;
        for (int i = 0; i < timeline.size(); i++) {
            JSONObject v = timeline.get(i);
            total += clipDurationSeconds(v);
            if (i > 0) total -= Math.min(2.0, Math.max(0, v.optDouble("transitionMs", 0) / 1000.0));
        }
        return Math.max(1000, (long)(total * 1000));
    }

    private int[] resolveExportSize(String resolution, String format) {
        boolean vertical = !"horizontal".equalsIgnoreCase(format) && !"landscape".equalsIgnoreCase(format);
        int longSide = "720p".equalsIgnoreCase(resolution) ? 1280 : ("4k".equalsIgnoreCase(resolution) ? 3840 : 1920);
        int shortSide = "720p".equalsIgnoreCase(resolution) ? 720 : ("4k".equalsIgnoreCase(resolution) ? 2160 : 1080);
        return vertical ? new int[]{shortSide, longSide} : new int[]{longSide, shortSide};
    }

    private String mapTransition(String transition) {
        if (transition == null) return "fade";
        transition = transition.toLowerCase(Locale.US);
        if (transition.contains("slide")) return "slideleft";
        if (transition.contains("zoom")) return "zoomin";
        if (transition.contains("wipe")) return "wipeleft";
        if (transition.contains("circle")) return "circleopen";
        if (transition.contains("blur")) return "fadeblack";
        if (transition.contains("none")) return "fade";
        return "fade";
    }

    private void copyUriToFile(Uri uri, File outFile) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(outFile)) {
            if (in == null) throw new Exception("não foi possível abrir mídia");
            byte[] buffer = new byte[1024 * 256];
            int len;
            while ((len = in.read(buffer)) > 0) out.write(buffer, 0, len);
        }
    }

    private void copyFileToUri(File source, Uri outUri) throws Exception {
        try (InputStream in = new java.io.FileInputStream(source);
             OutputStream out = getContentResolver().openOutputStream(outUri)) {
            if (out == null) throw new Exception("não foi possível abrir arquivo de saída");
            byte[] buffer = new byte[1024 * 256];
            int len;
            while ((len = in.read(buffer)) > 0) out.write(buffer, 0, len);
            out.flush();
        }
    }

    private void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) for (File f : files) deleteRecursive(f);
        }
        file.delete();
    }

    private String fmt(double v) { return String.format(Locale.US, "%.3f", v); }
    private String q(String s) { return "\"" + (s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")) + "\""; }
    private String escapeDrawText(String s) {
        return (s == null ? "" : s)
                .replace("\\", "\\\\")
                .replace(":", "\\:")
                .replace("'", "\\'")
                .replace("%", "\\%")
                .replace("\n", " ");
    }

    private String copyToPreviewCache(Uri uri, String fileName) {
        try {
            File dir = new File(getCacheDir(), "preview_media");
            if (!dir.exists()) dir.mkdirs();
            String clean = sanitizeFileName(fileName);
            File outFile = new File(dir, clean);
            try (InputStream in = getContentResolver().openInputStream(uri); FileOutputStream out = new FileOutputStream(outFile)) {
                if (in == null) return uri.toString();
                byte[] buffer = new byte[1024 * 128];
                int len;
                while ((len = in.read(buffer)) > 0) out.write(buffer, 0, len);
            }
            return Uri.fromFile(outFile).toString();
        } catch (Exception e) {
            return uri.toString();
        }
    }

    private void clearPreviewCache() {
        try {
            File dir = new File(getCacheDir(), "preview_media");
            if (!dir.exists()) return;
            File[] files = dir.listFiles();
            if (files == null) return;
            for (File f : files) f.delete();
        } catch (Exception ignored) {}
    }

    private long getContentLength(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0) return cursor.getLong(idx);
            }
        } catch (Exception ignored) { } finally { if (cursor != null) cursor.close(); }
        return -1;
    }

    private String sanitizeFileName(String name) {
        String clean = name == null ? "tradeup_video" : name.replaceAll("[^a-zA-Z0-9._ -]", "_").trim();
        if (clean.isEmpty()) clean = "tradeup_video";
        return clean;
    }

    private String escapeForJs(String text) { return text == null ? "" : text.replace("\\", "\\\\").replace("'", "\\'"); }

    private long getMediaDuration(Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            String d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return d == null ? 0 : Long.parseLong(d);
        } catch (Exception e) { return 0; }
        finally { try { retriever.release(); } catch (Exception ignored) {} }
    }

    private String getFileName(Uri uri, String fallback) {
        String result = fallback;
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        try {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) result = cursor.getString(idx);
            }
        } catch (Exception ignored) { } finally { if (cursor != null) cursor.close(); }
        return result == null || result.trim().isEmpty() ? fallback : result;
    }

    private void callJs(String fn, String value) { callJsRaw(fn + "(" + JSONObject.quote(value == null ? "" : value) + ")"); }
    private void callJsRaw(String js) { runOnUiThread(() -> webView.evaluateJavascript(js, null)); }
}
