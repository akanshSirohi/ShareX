package com.akansh.fileserversuit;

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

import com.akansh.t_history.HistoryItem;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import fi.iki.elonen.NanoHTTPD;
import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;

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

    public void setUpdateTransferHistoryListener(UpdateTransferHistoryListener updateTransferHistoryListener) {
        this.updateTransferHistoryListener = updateTransferHistoryListener;
    }

    public String getFilesListCode(String path, boolean allowHiddenMedia) {
        StringBuilder code=new StringBuilder();
        try {
            if (!utils.loadSetting(Constants.PRIVATE_MODE)) {
                File f = new File(path);
//                Log.d(Constants.LOG_TAG, "Files Path: " + f.getAbsolutePath());
                if (f.exists()) {
                    File[] files = f.listFiles();
//                    Log.d(Constants.LOG_TAG, "Files Length: " + files.length);
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
                            code.append("<img height=\"50\" src=\"ShareX?action=thumbImage&location=").append(files[i].getAbsolutePath()).append("\"></img>");
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
                                    code.append("</td><td class=\"align-middle row-highlight ctxMenu ps-3\" data-ctxmap=\"").append(stripRoot(fTemp.getAbsolutePath())).append("\" onclick=\"viewFile_p(this.dataset.ctxmap)\">");
                                    code.append("<img height=\"50\" src=\"ShareX?action=thumbImage&location=").append(fTemp.getAbsolutePath()).append("\"></img>");
                                } else {
                                    code.append("</td><td class=\"align-middle row-highlight ctxMenu ps-3\" data-ctxmap=\"").append(stripRoot(fTemp.getAbsolutePath())).append("\" onclick=\"openFile_p(this.dataset.ctxmap)\">");
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
            code.append("<tr><td colspan=\"3\" class=\"align-middle\" style=\"text-align:center;\"><i class=\"fa-solid fa-triangle-exclamation\"></i>&nbsp;&nbsp;Can't read this location!</td></tr>");
        }
        return code.toString();
    }

    public String getAppsListCode() {
        Comparator<ApplicationInfo> comparator= (obj1, obj2) -> packageManager.getApplicationLabel(obj1).toString().compareToIgnoreCase(packageManager.getApplicationLabel(obj2).toString());
        Collections.sort(packages,comparator);
        StringBuilder code=new StringBuilder();
        IconUtils iconUtils=new IconUtils();
        File base=new File(Environment.getExternalStorageDirectory(),"ShareX/.thumbs");
        if(!base.exists()) {
            base.mkdirs();
        }
        for (ApplicationInfo applicationInfo : packages) {
            if(utils.isUserApp(applicationInfo)) {
                String pkg = applicationInfo.packageName;
                packageManager.getApplicationLogo(applicationInfo);
                File dest = new File(base, pkg + ".png");
                if (!dest.exists()) {
                    Bitmap bm = iconUtils.drawableToBitmap(packageManager.getApplicationIcon(applicationInfo));
                    iconUtils.writeBitmapToFile(bm, dest);
                }
                File apk = new File(applicationInfo.sourceDir);
                String appName=packageManager.getApplicationLabel(applicationInfo).toString();
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
        return code.toString();
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

    public NanoHTTPD.Response serveFile(String path,boolean pushHistory,String rangeHeader) {
        NanoHTTPD.Response response;
        try {
            if(utils.loadSetting(Constants.PRIVATE_MODE)) {
                File f=new File(path);
                if (!isInPrivateFiles(f.getName())) {
                    return newFixedLengthResponse("");
                }
            }
            String m = utils.getMimeType(new File(path));
            String name = new File(path).getName();
            if(m==null) {
                m = "";
            }
            if(rangeHeader==null) {
                long b = utils.getTotalBytes(path);
                FileInputStream fis = new FileInputStream(path);
                ProgressInputStream pis = new ProgressInputStream(fis, (int) b);
                pis.addListener(percent -> {
                    if (sendProgressListener != null) {
                        sendProgressListener.onProgressUpdate((int) percent);
                    }
                });
                response = newFixedLengthResponse(NanoHTTPD.Response.Status.OK, m, pis, b);
                if (pushHistory) {
                    Calendar c = Calendar.getInstance();
                    SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH);
                    SimpleDateFormat tf = new SimpleDateFormat("hh:mm a", Locale.ENGLISH);
                    updateTransferHistoryListener.onUpdateTransferHistory(new HistoryItem(Constants.ITEM_TYPE_SENT, name, fileSize(new File(path)), df.format(c.getTime()), tf.format(c.getTime()), m, new File(path).getAbsolutePath()));
                }
            }else{
                String rangeValue = rangeHeader.trim().substring("bytes=".length());
                long fileLength = utils.getTotalBytes(path);
                long start, end;
                if (rangeValue.startsWith("-")) {
                    end = fileLength - 1;
                    start = fileLength - 1
                            - Long.parseLong(rangeValue.substring("-".length()));
                } else {
                    String[] range = rangeValue.split("-");
                    start = Long.parseLong(range[0]);
                    end = range.length > 1 ? Long.parseLong(range[1])
                            : fileLength - 1;
                }
                if (end > fileLength - 1) {
                    end = fileLength - 1;
                }
                if (start <= end) {
                    long contentLength = end - start + 1;
                    FileInputStream fis = new FileInputStream(path);
                    fis.skip(start);
                    ProgressInputStream pis = new ProgressInputStream(fis, (int) fileLength);
                    pis.addListener(new ProgressInputStream.ProgressListener() {
                        @Override
                        public void process(double percent) {
                            if (sendProgressListener != null) {
                                sendProgressListener.onProgressUpdate((int) percent);
                            }
                        }
                    });
                    response = newFixedLengthResponse(NanoHTTPD.Response.Status.PARTIAL_CONTENT, m, pis,fileLength);
                    response.addHeader("Content-Length", contentLength + "");
                    response.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
                } else {
                    response = newFixedLengthResponse(NanoHTTPD.Response.Status.RANGE_NOT_SATISFIABLE, m, rangeHeader);
                }
            }
            if(m.startsWith("image") || m.startsWith("video") || name.endsWith("pdf")) {
                response.addHeader("Content-Disposition", "inline; filename=\"" + name + "\"");
                response.addHeader("Content-Transfer-Encoding","binary");
                response.addHeader("Accept-Ranges","bytes");
            }else{
                response.addHeader("Accept-Ranges","bytes");
                response.addHeader("Content-type","application/octet-stream");
                response.addHeader("Content-Disposition", "inline; filename=\"" + name + "\"");
            }
            return response;
        }catch (Exception e) {
            return newFixedLengthResponse(e.getMessage());
        }
    }

    public NanoHTTPD.Response downloadFile(String path,boolean pCheck,boolean pushHistory,String rangeHeader) {
        NanoHTTPD.Response response;
        try {
            if(pCheck) {
                if (utils.loadSetting(Constants.PRIVATE_MODE)) {
                    File f = new File(path);
                    if (!isInPrivateFiles(f.getName())) {
                        return newFixedLengthResponse("");
                    }
                }
            }
            String name = new File(path).getName();
            if(rangeHeader==null) {
                long b = utils.getTotalBytes(path);
                FileInputStream fis = new FileInputStream(path);
                ProgressInputStream pis = new ProgressInputStream(fis, (int) b);
                pis.addListener(percent -> {
                    if (sendProgressListener != null) {
                        sendProgressListener.onProgressUpdate((int) percent);
                    }
                });
                response = newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/octet-stream", pis, b);
                if (pushHistory) {
                    Calendar c = Calendar.getInstance();
                    SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH);
                    SimpleDateFormat tf = new SimpleDateFormat("hh:mm a", Locale.ENGLISH);
                    updateTransferHistoryListener.onUpdateTransferHistory(new HistoryItem(Constants.ITEM_TYPE_SENT, name, fileSize(new File(path)), df.format(c.getTime()), tf.format(c.getTime()), utils.getMimeType(new File(path)), new File(path).getAbsolutePath()));
                }
            }else{
                String rangeValue = rangeHeader.trim().substring("bytes=".length());
                long fileLength = utils.getTotalBytes(path);
                long start, end;
                if (rangeValue.startsWith("-")) {
                    end = fileLength - 1;
                    start = fileLength - 1
                            - Long.parseLong(rangeValue.substring("-".length()));
                } else {
                    String[] range = rangeValue.split("-");
                    start = Long.parseLong(range[0]);
                    end = range.length > 1 ? Long.parseLong(range[1])
                            : fileLength - 1;
                }
                if (end > fileLength - 1) {
                    end = fileLength - 1;
                }
                if (start <= end) {
                    long contentLength = end - start + 1;
                    FileInputStream fis = new FileInputStream(path);
                    fis.skip(start);
                    ProgressInputStream pis = new ProgressInputStream(fis, (int) fileLength);
                    pis.addListener(percent -> {
                        if (sendProgressListener != null) {
                            sendProgressListener.onProgressUpdate((int) percent);
                        }
                    });
                    response = newFixedLengthResponse(NanoHTTPD.Response.Status.PARTIAL_CONTENT,"application/octet-stream", pis,fileLength);
                    response.addHeader("Content-Length", contentLength + "");
                    response.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
                } else {
                    response = newFixedLengthResponse(NanoHTTPD.Response.Status.RANGE_NOT_SATISFIABLE,"application/octet-stream", rangeHeader);
                }
            }
            response.addHeader("Content-type","application/octet-stream");
            response.addHeader("Content-Disposition", "inline; filename=\"" + name + "\"");
            response.addHeader("Content-Transfer-Encoding","binary");
            response.addHeader("Accept-Ranges","bytes");
            return response;
        }catch (Exception e) {
            return newFixedLengthResponse(e.getMessage());
        }
    }

    public NanoHTTPD.Response serveApp(String name,String path,String pkg) {
        NanoHTTPD.Response response;
        try {
            FileInputStream str = new FileInputStream(path);
            Utils utils = new Utils(ctx);
            long b = utils.getTotalBytes(path);
            response = newFixedLengthResponse(NanoHTTPD.Response.Status.OK,"application/vnd.android.package-archive", str, b);
            response.addHeader("Content-Disposition", "attachment; filename=\"" + name + "\"");
            Calendar c = Calendar.getInstance();
            SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH);
            SimpleDateFormat tf = new SimpleDateFormat("hh:mm a", Locale.ENGLISH);
            updateTransferHistoryListener.onUpdateTransferHistory(new HistoryItem(Constants.ITEM_TYPE_SENT, name, fileSize(new File(path)), df.format(c.getTime()), tf.format(c.getTime()), "Application",pkg));
        }catch (Exception e) {
            return newFixedLengthResponse(e.getMessage());
        }
        return response;
    }

    public void sendLog(String action,String key,String value) {
        Intent local = new Intent();
        local.setAction("service.to.activity.transfer");
        local.putExtra("action",action);
        local.putExtra(key,value);
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

    private String stripRoot(String path) {
        return path.replace(Environment.getExternalStorageDirectory().toString(),"");
    }

    @SuppressLint("SdCardPath")
    public boolean isInPrivateFiles(String path) {
        try {
            File file=new File("/data/data/"+ctx.getPackageName()+"/","pFilesList.bin");
            FileReader fr = new FileReader(file);
            BufferedReader br = new BufferedReader(fr);
            String line;
            while ((line = br.readLine()) != null) {
                if (line.length() > 2) {
                    File f=new File(line);
                    String x=f.getName();
                    if(x.equals(path)) {
                        return true;
                    }
                }
            }
            br.close();
            fr.close();
        }catch (Exception e) {
            Log.d(Constants.LOG_TAG,"Error is_in_p_func: "+e.toString());
            return false;
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
