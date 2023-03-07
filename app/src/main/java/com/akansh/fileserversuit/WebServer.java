package com.akansh.fileserversuit;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.util.Log;

import com.akansh.t_history.HistoryDBManager;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.json.JSONArray;

import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import fi.iki.elonen.NanoFileUpload;
import fi.iki.elonen.NanoHTTPD;

public class WebServer extends NanoHTTPD {

    private Utils utils;
    private String root=Environment.getExternalStorageDirectory().getAbsolutePath();
    private boolean allowHiddenMedia=true;
    private HistoryDBManager historyDBManager;
    private Context ctx;
    private ServerUtils serverUtils;
    private String c_parent = "";
    private ThemesData themesData;

    WebServer(String hostname, int port) {
        super(hostname, port);
    }

    void setContext(Context context) {
        utils=new Utils(context);
        historyDBManager=new HistoryDBManager(context);
        ctx=context;
        themesData = new ThemesData();
        serverUtils = new ServerUtils(ctx);

        serverUtils.setSendProgressListener(progress -> {
            Intent local = new Intent();
            local.setAction("service.to.activity.transfer");
            local.putExtra("action",Constants.ACTION_PROGRESS);
            local.putExtra("value",progress);
            ctx.sendBroadcast(local);
        });
        serverUtils.setUpdateTransferHistoryListener(historyItem -> historyDBManager.addTransferHistory(historyItem.getItem_type(),historyItem.getFile_name(),historyItem.getSize(),historyItem.getDate(),historyItem.getTime(),historyItem.getType(),historyItem.getPath()));
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public void setAllowHiddenMedia(boolean allowHiddenMedia) {
        this.allowHiddenMedia = allowHiddenMedia;
    }

    @Override
    public Response serve(IHTTPSession session) {
        try {
            String range=null;
            for(String key : session.getHeaders().keySet()) {
                if(key.equals("range")) {
                    range=session.getHeaders().get(key);
                }
            }
            String uri=session.getUri();
            String path;
            if(uri.equals("/ShareX")) {
                Map<String, List<String>> params=session.getParameters();
                String action = Objects.requireNonNull(params.get("action")).get(0);
                if(action.equals("listFiles")) {
                    String loc = Objects.requireNonNull(params.get("location")).get(0);
                    c_parent = loc;
                    return newFixedLengthResponse(serverUtils.getFilesListCode(root + loc, allowHiddenMedia));
                }else if(action.equals("openFile")){
                    String loc = root + Objects.requireNonNull(params.get("location")).get(0);
                    File f=new File(loc);
                    sendLog("msg","msg","Sending file: "+f.getName());
                    if(utils.loadSetting(Constants.FORCE_DOWNLOAD)) {
                        return serverUtils.downloadFile(loc,true,true,range);
                    }else{
                        return serverUtils.serveFile(loc,true,range);
                    }
                }else if(action.equals("viewImage")) {
                    String loc=root+ Objects.requireNonNull(params.get("location")).get(0);
                    return serverUtils.serveFile(loc,false,range);
                }else if(action.equals("thumbImage")) {
                    String loc= Objects.requireNonNull(params.get("location")).get(0);
                    return serverUtils.serveFile(loc,false,range);
                }else if(action.equals("delFiles")) {
                    if(!utils.loadSetting(Constants.RESTRICT_MODIFY) && !utils.loadSetting(Constants.PRIVATE_MODE)) {
                        JSONArray jsonArray = new JSONArray(Objects.requireNonNull(params.get("data")).get(0));
                        StringBuilder names = new StringBuilder();
                        for (int i = 0; i < jsonArray.length(); i++) {
                            String file = jsonArray.getString(i);
                            File f=new File(root + file);
                            utils.deleteFileOrDir(f);
                            names.append("'").append(f.getName()).append("' ");
                        }
                        sendLog("msg","msg","Deleted File/Folder: "+names);
                        return newFixedLengthResponse(jsonArray.length()+" files deleted successfully!;");
                    }else{
                        return newFixedLengthResponse("File deletion is restricted!;");
                    }

                }else if(action.equals("downloadFiles")) {
                    Log.d(Constants.LOG_TAG,"Download Files Hitted!");
                    JSONArray jsonArray=new JSONArray(Objects.requireNonNull(params.get("data")).get(0));
                    boolean serveOne=false;
                    File f;
                    if(!utils.loadSetting(Constants.PRIVATE_MODE)) {
                        f = new File(root + jsonArray.getString(0));
                    }else{
                        f = new File(jsonArray.getString(0));
                        if(!f.exists()) {
                            f = new File(root + jsonArray.getString(0));
                        }
                    }
                    if(jsonArray.length()==1 && !f.isDirectory()) {
                        serveOne=true;
                    }
                    if(!serveOne) {
                        sendLog("msg","msg","Zipping files to download...");
                        String fileName = "ShareX_zip_" + Objects.requireNonNull(params.get("filename")).get(0) + ".zip";
                        File base = new File(Environment.getExternalStorageDirectory() + "/ShareX/.temp", fileName);
                        String loc;
                        boolean b=utils.loadSetting(Constants.PRIVATE_MODE);
                        if (!base.exists()) {
                            ArrayList<File> filesList = new ArrayList<>();
                            File file;
                            for (int i = 0; i < jsonArray.length(); i++) {
                                if(!b) {
                                    file = new File(root + jsonArray.getString(i));
                                }else{
                                    file = new File(jsonArray.getString(i));
                                }
                                if (file.exists()) {
                                    pushHistory(file,Constants.ITEM_TYPE_SENT);
                                    filesList.add(file);
                                }
                            }
                            sendLog("msg","msg","Adding "+filesList+" to zip file...");
                            ZipUtils zipUtils = new ZipUtils();
                            loc = zipUtils.zip(filesList, fileName);
                        } else {
                            loc = base.getAbsolutePath();
                        }
                        sendLog("msg", "msg", "Zipping completed...");
                        sendLog("msg", "msg", "Sending zip file...");
                        return serverUtils.downloadFile(loc,false,false, range);
                    }else{
                        sendLog("msg", "msg", "Sending "+f.getName()+" to download...");
                        return serverUtils.downloadFile(f.getAbsolutePath(),true,true, range);
                    }
                }else if(action.equals("renF")) {
                    if(!utils.loadSetting(Constants.RESTRICT_MODIFY) && !utils.loadSetting(Constants.PRIVATE_MODE)) {
                        File old = new File(root + Objects.requireNonNull(params.get("old_n")).get(0));
                        File new_n = new File(root + Objects.requireNonNull(params.get("parent")).get(0), Objects.requireNonNull(params.get("new_n")).get(0));
                        old.renameTo(new_n);
                        sendLog("msg","msg","File/folder renamed \""+old.getName()+"\" to \""+new_n.getName()+"\"");
                        return newFixedLengthResponse("File/Folder Renamed Successfully!;");
                    }else{
                        return newFixedLengthResponse("File/Folder modification restricted!;");
                    }
                }else if(action.equals("newF")) {
                    if(!utils.loadSetting(Constants.RESTRICT_MODIFY) && !utils.loadSetting(Constants.PRIVATE_MODE)) {
                        File f = new File(root + Objects.requireNonNull(params.get("parent")).get(0), Objects.requireNonNull(params.get("name")).get(0));
                        f.mkdirs();
                        sendLog("msg","msg","New folder created at "+f.getAbsolutePath());
                        return newFixedLengthResponse("Folder Created Successfully!;");
                    }else{
                        return newFixedLengthResponse("File/Folder modification restricted!;");
                    }
                }else if(action.equals("listApps")) {
                    return newFixedLengthResponse(serverUtils.getAppsListCode());
                }else if(action.equals("getApp")) {
                    String pkg = Objects.requireNonNull(params.get("pkg")).get(0);
                    PackageManager packageManager=ctx.getPackageManager();
                    String appName = packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg,PackageManager.GET_META_DATA)).toString();
                    String appPath = packageManager.getApplicationInfo(pkg,PackageManager.GET_META_DATA).sourceDir;
                    sendLog("msg","msg","Sending App: "+appName);
                    return serverUtils.serveApp(appName+".apk",appPath,pkg);
                }else if(action.equals("getInfo")) {
                    return newFixedLengthResponse(serverUtils.getInfo());
                }else if(action.equals("getUploadLocation")) {
                    if(!utils.loadSetting(Constants.PRIVATE_MODE)) {
                        if(c_parent.isEmpty()) {
                            c_parent = "/";
                        }else if(c_parent.startsWith("//")) {
                            c_parent = c_parent.replace("//","/");
                        }
                        return newFixedLengthResponse("storage"+c_parent);
                    }else{
                        return newFixedLengthResponse("storage/ShareX");
                    }
                }else if(action.equals("getPrivateMode")) {
                    return newFixedLengthResponse(String.valueOf(utils.loadSetting(Constants.PRIVATE_MODE)));
                }else if(action.equals("auth")) {
                    final DeviceManager deviceManager=new DeviceManager(ctx);
                    final String device_id = Objects.requireNonNull(params.get("device_id")).get(0);
                    if(deviceManager.isDeviceExist(device_id)) {
                        return newFixedLengthResponse("true");
                    }else if(deviceManager.isDeviceDenied(device_id)){
                        return newFixedLengthResponse("denied");
                    }else {
                        sendLog(Constants.ACTION_AUTH, "device_id", device_id);
                        return newFixedLengthResponse("false");
                    }
                }
                return newFixedLengthResponse("");
            }else if(uri.equals("/ShareX/uploadFile")) {
                try {
                    if(utils.loadSetting(Constants.PRIVATE_MODE)) {
                        File folder = new File(Environment.getExternalStorageDirectory() + "/ShareX");
                        if (!folder.exists()) {
                            folder.mkdirs();
                        }
                    }
                    DiskFileItemFactory factory = new DiskFileItemFactory();
                    if(!utils.loadSetting(Constants.PRIVATE_MODE)) {
                        factory.setRepository(new File(utils.loadRoot() + c_parent));
                    }else{
                        factory.setRepository(new File(Environment.getExternalStorageDirectory() + "/ShareX"));
                    }
                    List<FileItem> files = new NanoFileUpload(factory).parseRequest(session);
                    int len = 0;
                    for (FileItem file : files) {
                        try {
                            String fileName = file.getName();
                            if(fileName!=null) {
                                File received;
                                if(!utils.loadSetting(Constants.PRIVATE_MODE)) {
                                    received = new File(utils.loadRoot() + c_parent + "/" +fileName);
                                }else{
                                    received = new File(Environment.getExternalStorageDirectory() + "/ShareX/" + fileName);
                                }
                                file.write(received);
                                String mime = utils.getMimeType(received);
                                if (mime.startsWith("image")) {
                                    utils.verifyImage(received);
                                } else if (mime.startsWith("video")) {
                                    utils.verifyVideo(received);
                                }
                                pushHistory(received,Constants.ITEM_TYPE_RECEIVED);
                                sendLog("msg", "msg", "File received: " + fileName);
                                len++;
                            }
                        } catch (Exception exception) {
                            // handle
                        }
                    }
                    if(!utils.loadSetting(Constants.PRIVATE_MODE)) {
                        String loc = utils.loadRoot().replace("/emulated/0","") + c_parent;
                        if(loc.startsWith("/")) {
                            loc = loc.substring(1);
                        }
                        if(loc.endsWith("/")) {
                            loc = loc.substring(0, loc.length() - 1);
                        }
                        sendLog("msg", "msg", "Total files received " + len + " and stored in "+ loc + " directory");
                    }else{
                        sendLog("msg", "msg", "Total files received " + len + " and stored in storage/ShareX directory");
                    }
                    return newFixedLengthResponse(len+" Files Uploaded Successsfully!");
                } catch (Exception ex) {
                    Log.d("server", ex.toString());
                }
                return newFixedLengthResponse("Error!");
            }else if(uri.startsWith("/ShareX/thumbnail/app")) {
                String pkg=uri.split("/")[uri.split("/").length-1];
                File iconP=new File(Environment.getExternalStorageDirectory()+"/ShareX/.thumbs",pkg+".png");
                if(iconP.exists()) {
                    FileInputStream fis = new FileInputStream(iconP);
                    String mime = utils.getMimeType(iconP.getAbsolutePath());
                    long bytes = utils.getTotalBytes(iconP.getAbsolutePath());
                    return newFixedLengthResponse(Response.Status.OK, mime, fis, bytes);
                }else{
                    return newFixedLengthResponse("");
                }
            }else{
                if(uri.equals("/")) {
                    path= utils.getFileProperPath("index.html");
                }else if(uri.equals("/libs/bootstrap/css/theme_bootstrap.min.css")) {
                    uri = uri.replace("theme", themesData.getPrefix(utils.loadInt(Constants.WEB_INTERFACE_THEME,0)));
                    path=utils.getFileProperPath(uri);
                }else{
                    path=utils.getFileProperPath(uri);
                }
                FileInputStream fis=new FileInputStream(path);
                String mime = utils.getMimeType(path);
                long bytes = utils.getTotalBytes(path);
                return newFixedLengthResponse(Response.Status.OK,mime,fis,bytes);
            }
        } catch (Exception e) {
            return newFixedLengthResponse("Error Occured: "+e.getMessage());
        }
    }

    public void sendLog(String action,String key,String value) {
        Intent local = new Intent();
        local.setAction("service.to.activity.transfer");
        local.putExtra("action",action);
        local.putExtra(key,value);
        ctx.sendBroadcast(local);
    }

    public void pushHistory(File f,int itemType) {
        Calendar c = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH);
        SimpleDateFormat tf = new SimpleDateFormat("hh:mm a", Locale.ENGLISH);
        String mime=utils.getMimeType(f);
        if(mime==null) {
            mime="folder";
        }
        historyDBManager.addTransferHistory(itemType,f.getName(),serverUtils.fileSize(f),df.format(c.getTime()),tf.format(c.getTime()),mime,f.getAbsolutePath());
    }
}
