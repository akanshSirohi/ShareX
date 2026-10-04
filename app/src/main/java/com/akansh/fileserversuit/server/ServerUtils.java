package com.akansh.fileserversuit.server;

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

import com.akansh.fileserversuit.common.Constants;
import com.akansh.fileserversuit.common.Utils;
import com.akansh.plugins.common.Plugin;
import com.akansh.plugins.PluginsDBHelper;
import com.akansh.fileserversuit.transfer_history.HistoryItem;
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
    private final List<ApplicationInfo> packages;
    private final PackageManager packageManager;
    private final Context ctx;
    Utils utils;
    SendProgressListener sendProgressListener;
    UpdateTransferHistoryListener updateTransferHistoryListener;

    public ServerUtils(Context ctx) {
        this.ctx=ctx;
        utils=new Utils(ctx);
        packageManager=ctx.getPackageManager();
        packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
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

    public String getFilesListCode(String path, boolean allowHiddenMedia) {
        StringBuilder code=new StringBuilder();
        try {
            if (!utils.loadSetting(Constants.PRIVATE_MODE)) {
                File f = new File(path);
                if (f.exists()) {
                    File[] files = f.listFiles();
                    Collections.sort(Arrays.asList(files), new FilesComparator());
                    String name = "";
                    int len = 0;
                    for (int i = 0; i < files.length; i++) {
                        name = files[i].getName();
                        if (!allowHiddenMedia) {
                            if (name.startsWith(".")) {
                                continue;
                            }
                        }
                        len++;
                        code.append("<tr id=\"row_").append(name).append("\"><td class=\"align-middle icon-col text-center\">");
                        code.append("<div class=\"form-check\">");
                        code.append("<input type=\"checkbox\" class=\"form-check-input\" name=\"fChkBoxes\" id=\"").append(name).append("\" onchange=\"notifyChkBoxUI();\">");
                        code.append("<label class=\"form-check-label\" for=\"").append(name).append("\"></label>");
                        code.append("</div>");
                        if (files[i].isDirectory()) {
                            code.append("</td><td class=\"align-middle row-highlight ctxMenu ps-3\" data-ctxmap=\"").append(name).append("\" onclick=\"openFolder(this.dataset.ctxmap)\">");
                        } else if (utils.getMimeType(files[i]).startsWith("image")) {
                            code.append("</td><td class=\"align-middle row-highlight ctxMenu ps-3\" data-ctxmap=\"").append(name).append("\" onclick=\"viewFile(this.dataset.ctxmap)\">");
                        } else {
                            code.append("</td><td class=\"align-middle row-highlight ctxMenu ps-3\" data-ctxmap=\"").append(name).append("\" onclick=\"openFile(this.dataset.ctxmap)\">");
                        }
                        if (files[i].isDirectory()) {
                            code.append("<i class=\"fa-solid fa-folder-closed\"></i>");
                        } else if (utils.getMimeType(files[i]).startsWith("image")) {
                            code.append("<img height=\"50\" loading=\"lazy\" src=\"ShareX?action=thumbImage&location=").append(files[i].getAbsolutePath()).append("\"></img>");
                        } else {
                            code.append(utils.getIconCode(files[i]));
                        }

                        if (name.startsWith(".")) {
                            code.append("<sub><i class=\"fas fa-mask\"></i></sub>&nbsp;&nbsp;");
                        } else {
                            code.append("&nbsp;&nbsp;");
                        }
                        code.append(name);
                        code.append("</td>");
                        if (!files[i].isDirectory()) {
                            code.append("<td class=\"align-middle\">");
                            code.append(fileSize(files[i]));
                            code.append("</td>");
                        } else {
                            code.append("<td class=\"align-middle\">-</td>");
                        }
                        code.append("</tr>");
                    }
                    if (len == 0) {
                        code.append("<tr><td colspan=\"3\" class=\"align-middle\" style=\"text-align:center;\"><i class=\"fa-regular fa-folder-open\"></i>&nbsp;&nbsp;Empty Folder!</td></tr>");
                    }
                } else {
                    code.append("<tr><td colspan=\"3\" class=\"align-middle\" style=\"text-align:center;\"><i class=\"fa-regular fa-folder-open\"></i>&nbsp;&nbsp;Empty Folder!</td></tr>");
                }


            } else {
                try {
                    File file = new File("/data/data/" + ctx.getPackageName() + "/", "pFilesList.bin");
                    if (file.exists()) {
                        FileReader fr = new FileReader(file);
                        BufferedReader br = new BufferedReader(fr);
                        String pth, name;
                        int len = 0;
                        File fTemp;
                        while ((pth = br.readLine()) != null) {
                            if (pth.length() > 2) {
                                fTemp = new File(pth);
                                name = fTemp.getName();
                                len++;
                                code.append("<tr id=\"row_").append(fTemp.getAbsolutePath()).append("\"><td class=\"align-middle icon-col\" style=\"padding-left:25px;\">");
                                code.append("<div class=\"custom-control custom-checkbox\">");
                                code.append("<input type=\"checkbox\" class=\"custom-control-input\" name=\"fChkBoxes\" id=\"").append(fTemp.getAbsolutePath()).append("\" onchange=\"notifyChkBoxUI();\">");
                                code.append("<label class=\"custom-control-label\" for=\"").append(fTemp.getAbsolutePath()).append("\"></label>");
                                code.append("</div>");
                                if (utils.getMimeType(pth).startsWith("image")) {
                                    code.append("</td><td class=\"align-middle row-highlight ctxMenu ps-3\" data-ctxmap=\"").append(fTemp.getAbsolutePath()).append("\" onclick=\"viewFile_p(this.dataset.ctxmap)\">");
                                    code.append("<img height=\"50\" src=\"ShareX?action=thumbImage&location=").append(fTemp.getAbsolutePath()).append("\"></img>");
                                } else {
                                    code.append("</td><td class=\"align-middle row-highlight ctxMenu ps-3\" data-ctxmap=\"").append(fTemp.getAbsolutePath()).append("\" onclick=\"openFile_p(this.dataset.ctxmap)\">");
                                    code.append(utils.getIconCode(fTemp));
                                }
                                code.append("&nbsp;&nbsp;");
                                code.append(name);
                                code.append("</td>");
                                code.append("<td class=\"align-middle\">");
                                code.append(fileSize(fTemp));
                                code.append("</td>");
                                code.append("</tr>");
                            }
                        }
                        if (len == 0) {
                            code.append("<tr><td colspan=\"3\" class=\"align-middle\" style=\"text-align:center;\"><i class=\"far fa-folder\"></i>&nbsp;&nbsp;No Files Shared!</td></tr>");
                        }
                        br.close();
                        fr.close();
                    } else {
                        code.append("<tr><td colspan=\"3\" class=\"align-middle\" style=\"text-align:center;\"><i class=\"far fa-folder\"></i>&nbsp;&nbsp;No Files Shared!</td></tr>");
                    }
                } catch (Exception e) {
                    Log.d(Constants.LOG_TAG, "Pivate Files List Error: " + e.toString());
                }
            }
        }catch (Exception e) {
            Log.d(Constants.LOG_TAG,"Error in getFilesListCode: "+e);
            code.append("<tr><td colspan=\"3\" class=\"align-middle\" style=\"text-align:center;\"><i class=\"fa-solid fa-triangle-exclamation\"></i>&nbsp;&nbsp;Can't read this location!</td></tr>");
        }
        return code.toString();
    }

    public String getAppsListCode() {
        StringBuilder code = new StringBuilder();
        if(utils.loadSetting(Constants.LOAD_APPS)) {
            Comparator<ApplicationInfo> comparator = (obj1, obj2) -> packageManager.getApplicationLabel(obj1).toString().compareToIgnoreCase(packageManager.getApplicationLabel(obj2).toString());
            Collections.sort(packages, comparator);
            IconUtils iconUtils = new IconUtils();
            File base = new File(Environment.getExternalStorageDirectory(), "ShareX/.thumbs");
            if (!base.exists()) {
                base.mkdirs();
            }
            for (ApplicationInfo applicationInfo : packages) {
                if (utils.isUserApp(applicationInfo)) {
                    String pkg = applicationInfo.packageName;
                    packageManager.getApplicationLogo(applicationInfo);
                    File dest = new File(base, pkg + ".png");
                    if (!dest.exists()) {
                        Bitmap bm = iconUtils.drawableToBitmap(packageManager.getApplicationIcon(applicationInfo));
                        iconUtils.writeBitmapToFile(bm, dest);
                    }
                    File apk = new File(applicationInfo.sourceDir);
                    String appName = packageManager.getApplicationLabel(applicationInfo).toString();
                    code.append("<tr>");
                    code.append("<td>");
                    code.append("<div class=\"d-flex\">");
                    code.append("<div class=\"ps-2 appInfo\">");
                    code.append("<img src=\"/ShareX/thumbnail/app/").append(pkg).append("\" class=\"app-icon\" />");
                    code.append("<div class=\"px-3\">");
                    code.append(appName);
                    code.append("<br><small>").append(pkg);
                    code.append("<br><b>Size: </b>");
                    code.append(fileSize(apk));
                    code.append("</small></div>");
                    code.append("</div>");
                    code.append("<div class=\"apk-dwl-btn pe-2\">");
                    code.append("<button class=\"btn btn-primary\" onclick=\"getApp('").append(pkg).append("');\"><i class=\"fas fa-download\"></i></button>");
                    code.append("</div></div>");
                    code.append("</tr>");
                }
            }
        }else{
            code.append("<tr>");
            code.append("<td>");
            code.append("<div style=\"display: flex;\" class=\"my-3 justify-content-center align-items-center\"><i class=\"fa-solid fa-ban\"></i>&nbsp;Apps Access Denied!</div>");
            code.append("</td>");
            code.append("</tr>");
        }
        return code.toString();
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
            String disposition = inline && (mime.startsWith("image/") || mime.startsWith("video/") || mime.equals("application/pdf")) ? "inline" : "attachment";
            WebResponse result = WebResponse.file(status, file, start, end - start + 1, true)
                    .header("Content-Type", mime)
                    .header("Content-Disposition", disposition + "; filename=\"" + safeHeaderFilename(file.getName()) + "\"")
                    .header("Accept-Ranges", "bytes");
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
