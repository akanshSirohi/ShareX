package com.akansh.sharex.server;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.BatteryManager;
import android.os.Environment;
import android.util.Log;

import com.akansh.sharex.common.Constants;
import com.akansh.sharex.common.Utils;
import com.akansh.plugins.common.Plugin;
import com.akansh.plugins.PluginsDBHelper;
import com.akansh.sharex.transfer_history.HistoryItem;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.Target;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;

public class ServerUtils {
    private final PackageManager packageManager;
    private final Context ctx;
    Utils utils;
    SendProgressListener sendProgressListener;
    UpdateTransferHistoryListener updateTransferHistoryListener;

    public ServerUtils(Context ctx) {
        this.ctx=ctx;
        utils=new Utils(ctx);
        packageManager=ctx.getPackageManager();
    }

    public void setSendProgressListener(SendProgressListener sendProgressListener) {
        this.sendProgressListener = sendProgressListener;
    }

    public void sendProgressListenerUpdate(int progress) {
        if (sendProgressListener != null) sendProgressListener.onProgressUpdate(progress);
    }

    public void setUpdateTransferHistoryListener(UpdateTransferHistoryListener updateTransferHistoryListener) {
        this.updateTransferHistoryListener = updateTransferHistoryListener;
    }

    public JSONObject getFilesList(String path, boolean allowHiddenMedia, String root) throws Exception {
        JSONArray items = new JSONArray();
        boolean privateMode = utils.loadSetting(Constants.PRIVATE_MODE);
        if (privateMode) {
            File sharedList = new File(ctx.getApplicationInfo().dataDir, "pFilesList.bin");
            if (sharedList.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(sharedList))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        File file = new File(line);
                        if (line.length() > 2 && file.isFile()) items.put(fileInfo(file, file.getCanonicalPath()));
                    }
                }
            }
        } else {
            File directory = new File(path);
            File[] files = directory.listFiles();
            if (files == null) throw new java.io.IOException("Can't read this location");
            Arrays.sort(files, new FilesComparator());
            File allowedRoot = new File(root).getCanonicalFile();
            for (File file : files) {
                if (!allowHiddenMedia && file.getName().startsWith(".")) continue;
                File canonical = file.getCanonicalFile();
                if (!canonical.getPath().startsWith(allowedRoot.getPath() + File.separator)) continue;
                String location = allowedRoot.toPath().relativize(file.toPath()).toString();
                items.put(fileInfo(file, location));
            }
        }
        return new JSONObject().put("items", items).put("privateMode", privateMode);
    }

    private JSONObject fileInfo(File file, String location) throws Exception {
        String mime = file.isDirectory() ? "inode/directory" : utils.getMimeType(file);
        return new JSONObject().put("name", file.getName()).put("location", location)
                .put("directory", file.isDirectory()).put("size", file.isDirectory() ? 0 : file.length())
                .put("modified", file.lastModified()).put("hidden", file.getName().startsWith("."))
                .put("mime", mime == null ? "application/octet-stream" : mime);
    }

    public JSONObject getAppsList() throws Exception {
        JSONArray items = new JSONArray();
        if (utils.loadSetting(Constants.LOAD_APPS)) {
            List<ApplicationInfo> applications = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
            applications.sort(Comparator.comparing(info -> packageManager.getApplicationLabel(info).toString(), String.CASE_INSENSITIVE_ORDER));
            IconUtils iconUtils = new IconUtils();
            File base = new File(Environment.getExternalStorageDirectory(), "ShareX/.thumbs");
            if (!base.isDirectory() && !base.mkdirs()) throw new java.io.IOException("Cannot create app thumbnails");
            for (ApplicationInfo info : applications) {
                if (!utils.isUserApp(info)) continue;
                File icon = new File(base, info.packageName + ".png");
                if (!icon.exists()) iconUtils.writeBitmapToFile(iconUtils.drawableToBitmap(packageManager.getApplicationIcon(info)), icon);
                items.put(new JSONObject().put("name", packageManager.getApplicationLabel(info).toString())
                        .put("package", info.packageName).put("size", new File(info.sourceDir).length())
                        .put("icon", "/ShareX/thumbnail/app/" + info.packageName));
            }
        }
        return new JSONObject().put("items", items).put("allowed", utils.loadSetting(Constants.LOAD_APPS));
    }

    public JSONObject getPortalState() throws Exception {
        Intent battery = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery == null ? 100 : battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int plugged = battery == null ? 0 : battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        long total = 0, available = 0;
        try {
            android.os.StatFs storage = new android.os.StatFs(utils.loadRoot());
            total = storage.getTotalBytes();
            available = storage.getAvailableBytes();
        } catch (IllegalArgumentException | SecurityException ignored) { }

        return new JSONObject().put("deviceName", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL)
                .put("battery", level < 0 ? -1 : level * 100 / Math.max(1, scale)).put("charging", plugged > 0)
                .put("privateMode", utils.loadSetting(Constants.PRIVATE_MODE))
                .put("restrictModify", utils.loadSetting(Constants.RESTRICT_MODIFY))
                .put("appsAllowed", utils.loadSetting(Constants.LOAD_APPS))
                .put("theme", new ThemesData().getPrefix(utils.loadInt(Constants.WEB_INTERFACE_THEME, 0)))
                .put("webVersion", Constants.WEB_INTERFACE_DIR)
                .put("storageTotal", total).put("storageFree", available).put("storageUsed", Math.max(0, total - available))
                .put("connected", SharingSession.isRunning())
                .put("sessionElapsedMs", SharingSession.elapsed(android.os.SystemClock.elapsedRealtime()));
    }

    public String getPluginsList() {
        PluginsDBHelper pluginsDBHelper = new PluginsDBHelper(ctx);
        ArrayList<Plugin> plugins = pluginsDBHelper.getInstalledPlugins();
        JSONArray jsonArray = new JSONArray();
        try {
            // Iterate through the ArrayList and convert each object to JSON
            for (Plugin plugin : plugins) {
                if(plugin.isPlugin_enabled()) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("uid", plugin.getPlugin_uid());
                    jsonObject.put("name", plugin.getPlugin_name());
                    jsonObject.put("version", plugin.getPlugin_version());
                    jsonObject.put("description", plugin.getPlugin_description());
                    jsonObject.put("author", plugin.getPlugin_author());
                    jsonArray.put(jsonObject);
                }
            }
            /// Script file solution, custom code mst be added, replaced by specific base code
        } catch (Exception e) {}
        return jsonArray.toString();
    }

    public boolean getPluginStatus(String uid) {
        PluginsDBHelper pluginsDBHelper = new PluginsDBHelper(ctx);
        return pluginsDBHelper.getStatus(uid);
    }

    public Plugin getEnabledPlugin(String uid) {
        try (PluginsDBHelper database = new PluginsDBHelper(ctx)) {
            for (Plugin plugin : database.getInstalledPlugins()) {
                if (uid.equals(plugin.getPlugin_uid()) && plugin.isPlugin_enabled()) return plugin;
            }
        }
        return null;
    }

    public Plugin getEnabledPluginByPackage(String packageName) {
        try (PluginsDBHelper database = new PluginsDBHelper(ctx)) {
            for (Plugin plugin : database.getInstalledPlugins()) {
                if (packageName.equals(plugin.getPlugin_package_name()) && plugin.isPlugin_enabled()) return plugin;
            }
        }
        return null;
    }

    public String fileSize(File file) {
        String output=null;
        if(file.exists() && file.isFile()) {
            if(file.length()<1024) {
                output = new DecimalFormat("##.##").format(file.length()) + " B";
                return output;
            }
            float size = file.length() / 1024f;
            if (size >= 1024) {
                size = size / 1024;
                if(size >= 1024) {
                    size = size / 1024;
                    output = new DecimalFormat("##.##").format(size) + " GB";
                }else{
                    output = new DecimalFormat("##.##").format(size) + " MB";
                }
            } else {
                output = new DecimalFormat("##.##").format(size) + " KB";
            }
        }else{
            output="---";
        }
        return output;
    }

    public WebResponse serveThumbnail(String path) {
        try {
            Bitmap thumbnail = Glide.with(ctx).asBitmap().load(path).centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL).override(100, 100)
                    .submit(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL).get();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            thumbnail.compress(Bitmap.CompressFormat.JPEG, 100, output);
            return WebResponse.bytes(200, output.toByteArray()).header("Content-Type", "image/jpeg");
        } catch (Exception e) {
            return WebResponse.text(404, "Thumbnail not found");
        }
    }

    public WebResponse serveFile(String path, boolean pushHistory, String rangeHeader) {
        return transferFile(path, true, true, pushHistory, rangeHeader);
    }

    public WebResponse downloadFile(String path, boolean privateCheck, boolean pushHistory, String rangeHeader) {
        return transferFile(path, false, privateCheck, pushHistory, rangeHeader);
    }

    private WebResponse transferFile(String path, boolean inline, boolean privateCheck, boolean pushHistory, String rangeHeader) {
        File file = new File(path);
        try {
            if (!file.isFile() || !file.canRead()) return WebResponse.text(404, "File not found");
            if (privateCheck && utils.loadSetting(Constants.PRIVATE_MODE) && !isInPrivateFiles(file.getAbsolutePath())) {
                return WebResponse.text(403, "Access denied");
            }
            long size = file.length();
            long start = 0;
            long end = size - 1;
            int status = 200;
            if (rangeHeader != null) {
                long[] range = parseRange(size, rangeHeader);
                if (range == null) {
                    return WebResponse.text(416, "Invalid or unsatisfiable byte range")
                            .header("Content-Range", "bytes */" + size).header("Accept-Ranges", "bytes");
                }
                start = range[0];
                end = range[1];
                status = 206;
            }
            String mime = utils.getMimeType(file);
            if (mime == null || mime.isEmpty()) mime = "application/octet-stream";
            String disposition = inline && (mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/") || mime.equals("application/pdf")) ? "inline" : "attachment";
            WebResponse result = WebResponse.file(status, file, start, end - start + 1, true)
                    .header("Content-Type", mime)
                    .header("Content-Disposition", disposition + "; filename=\"" + safeHeaderFilename(file.getName()) + "\"")
                    .header("Accept-Ranges", "bytes")
                    .header("Cache-Control", "no-store")
                    .header("X-Content-Type-Options", "nosniff");
            if (mime.equals("text/html") || mime.equals("image/svg+xml")) result.header("Content-Security-Policy", "sandbox; default-src 'none'; style-src 'unsafe-inline'; img-src data:");
            if (status == 206) result.header("Content-Range", "bytes " + start + "-" + end + "/" + size);
            if (pushHistory && rangeHeader == null) recordSentHistory(file, mime);
            return result;
        } catch (Exception e) {
            return WebResponse.text(500, "Unable to read file");
        }
    }

    private long[] parseRange(long fileLength, String value) {
        try {
            if (fileLength <= 0 || value == null || !value.startsWith("bytes=") || value.indexOf(',') >= 0) return null;
            String spec = value.substring(6).trim();
            int dash = spec.indexOf('-');
            if (dash < 0 || dash != spec.lastIndexOf('-')) return null;
            String left = spec.substring(0, dash).trim();
            String right = spec.substring(dash + 1).trim();
            long start;
            long end;
            if (left.isEmpty()) {
                long suffix = Long.parseLong(right);
                if (suffix <= 0) return null;
                start = Math.max(0, fileLength - suffix);
                end = fileLength - 1;
            } else {
                start = Long.parseLong(left);
                end = right.isEmpty() ? fileLength - 1 : Long.parseLong(right);
                if (start >= fileLength || start > end) return null;
                end = Math.min(end, fileLength - 1);
            }
            return new long[]{start, end};
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String safeHeaderFilename(String name) {
        return name.replace("\\", "_").replace("\"", "_").replace("\r", "_").replace("\n", "_");
    }

    private void recordSentHistory(File file, String mime) {
        if (updateTransferHistoryListener == null) return;
        Calendar c = Calendar.getInstance();
        SimpleDateFormat date = new SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH);
        SimpleDateFormat time = new SimpleDateFormat("hh:mm a", Locale.ENGLISH);
        updateTransferHistoryListener.onUpdateTransferHistory(new HistoryItem(Constants.ITEM_TYPE_SENT, file.getName(), fileSize(file), date.format(c.getTime()), time.format(c.getTime()), mime, file.getAbsolutePath()));
    }

    public WebResponse serveApp(String name, String path, String pkg, String rangeHeader) {
        if (!utils.loadSetting(Constants.LOAD_APPS)) return WebResponse.text(403, "Access Denied!");
        File file = new File(path);
        if (!file.isFile() || !file.canRead()) return WebResponse.text(404, "App not found");
        long start = 0;
        long end = file.length() - 1;
        int status = 200;
        if (rangeHeader != null) {
            long[] range = parseRange(file.length(), rangeHeader);
            if (range == null) return WebResponse.text(416, "Invalid or unsatisfiable byte range")
                    .header("Content-Range", "bytes */" + file.length()).header("Accept-Ranges", "bytes");
            start = range[0];
            end = range[1];
            status = 206;
        }
        Calendar c = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH);
        SimpleDateFormat tf = new SimpleDateFormat("hh:mm a", Locale.ENGLISH);
        if (rangeHeader == null && updateTransferHistoryListener != null) updateTransferHistoryListener.onUpdateTransferHistory(new HistoryItem(Constants.ITEM_TYPE_SENT, name, fileSize(file), df.format(c.getTime()), tf.format(c.getTime()), "Application", pkg));
        WebResponse response = WebResponse.file(status, file, start, end - start + 1, true)
                .header("Content-Type", "application/vnd.android.package-archive")
                .header("Content-Disposition", "attachment; filename=\"" + safeHeaderFilename(name) + "\"")
                .header("Accept-Ranges", "bytes");
        if (status == 206) response.header("Content-Range", "bytes " + start + "-" + end + "/" + file.length());
        return response;
    }

    public void sendLog(String action,String key,String value) {
        Intent local = new Intent();
        local.setAction(Constants.BROADCAST_SERVICE_TO_ACTIVITY);
        local.putExtra("action",action);
        local.putExtra(key,value);
        local.setPackage(ctx.getPackageName());
        ctx.sendBroadcast(local);
    }

    public String getInfo() {
        try {
            Intent intent = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            int percentage = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            String pTyp;
            if(plugged == BatteryManager.BATTERY_PLUGGED_AC) {
                pTyp="Plugged: AC Charger";
            }else if(plugged == BatteryManager.BATTERY_PLUGGED_USB) {
                pTyp="Plugged: USB";
            }else if(plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS) {
                pTyp="Plugged: Wireless";
            }else{
                pTyp="Discharging...";
            }
            String col;
            if(percentage<20) {
                col="red";
            }else if(percentage<60) {
                col="yellowgreen";
            }else{
                col="green";
            }
            return percentage+";"+pTyp+";"+col;
        }catch (Exception e) {
            //Do nothing
        }
        return "-;-;transparent";
    }

    @SuppressLint("SdCardPath")
    public boolean isInPrivateFiles(String path) {
        File file = new File("/data/data/" + ctx.getPackageName() + "/", "pFilesList.bin");
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            File target = new File(path).getCanonicalFile();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() > 2 && target.equals(new File(line).getCanonicalFile())) return true;
            }
        } catch (Exception e) {
            Log.d(Constants.LOG_TAG, "Error is_in_p_func: " + e);
        }
        return false;
    }

    public interface SendProgressListener {
        void onProgressUpdate(int progress);
    }

    public interface UpdateTransferHistoryListener {
        void onUpdateTransferHistory(HistoryItem historyItem);
    }

    private static class FilesComparator implements Comparator<File> {
        public int compare(File lhs,File rhs) {
            if (lhs.isDirectory() && !rhs.isDirectory()){
                return -1;
            } else if (!lhs.isDirectory() && rhs.isDirectory()){
                return 1;
            } else {
                return lhs.getName().toLowerCase().compareTo(rhs.getName().toLowerCase());
            }
        }
    }
}
