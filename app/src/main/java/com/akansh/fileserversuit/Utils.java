package com.akansh.fileserversuit;

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.webkit.MimeTypeMap;

import androidx.core.content.FileProvider;
import androidx.core.content.res.ResourcesCompat;

import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static android.content.Context.MODE_PRIVATE;


public class Utils {

    private Context ctx;

    public Utils() {}

    public Utils(Context ctx) {
        this.ctx = ctx;
    }

    public String fileSize(String src) {
        File file = new File(src);
        String output = null;
        if (file.exists()) {
            float size = file.length() / 1024;
            if (size >= 1024) {
                size = size / 1024;
                if (size >= 1024) {
                    output = new DecimalFormat("##.##").format(size) + " GB";
                } else {
                    output = new DecimalFormat("##.##").format(size) + " MB";
                }
            } else {
                output = new DecimalFormat("##.##").format(size) + " KB";
            }
        }
        return output;
    }

    public String bytesToMemory(float bytes) {
        String output = "";
        float size = bytes / 1024;
        if (size >= 1024) {
            size = size / 1024;
            if (size >= 1024) {
                output = new DecimalFormat("##.##").format(size) + " GB";
            } else {
                output = new DecimalFormat("##.##").format(size) + " MB";
            }
        } else {
            output = new DecimalFormat("##.##").format(size) + " KB";
        }
        return output;
    }

    public String getIPAddress(boolean useIPv4) {
        try {
            for (NetworkInterface intf : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress addr : Collections.list(intf.getInetAddresses())) {
                    if (!addr.isLoopbackAddress()) {
                        boolean isIPv4;
                        String sAddr = addr.getHostAddress();
                        if (sAddr.indexOf(58) < 0) {
                            isIPv4 = true;
                        } else {
                            isIPv4 = false;
                        }
                        if (useIPv4) {
                            if (isIPv4) {
                                return sAddr;
                            }
                        } else if (!isIPv4) {
                            int delim = sAddr.indexOf(37);
                            return delim < 0 ? sAddr.toUpperCase() : sAddr.substring(0, delim).toUpperCase();
                        }
                    }
                }
            }
        } catch (Exception e) {
            return "localhost";
        }
        return "localhost";
    }

    public String getMimeType(String path) {
        String type = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(path);
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        if (type == null || type.equals("null") || type.equals("")) {
            type = "text";
        }
        return type;
    }

    public long getTotalBytes(String path) {
        File file = new File(path);
        long tBytes = file.length();
        return tBytes;
    }

    public String getFileProperPath(String file) {
        if (file.startsWith("/")) {
            file = file.substring(1);
        }
        return "/data/data/" + ctx.getPackageName() + "/" + Constants.NEW_DIR + "/" + file;
    }

    public void saveString(String constant, String str) {
        SharedPreferences.Editor editor = ctx.getSharedPreferences(ctx.getPackageName(), MODE_PRIVATE).edit();
        editor.putString(constant, str);
        editor.apply();
    }

    public String loadString(String constant) {
        SharedPreferences sharedPrefs = ctx.getSharedPreferences(ctx.getPackageName(), MODE_PRIVATE);
        return sharedPrefs.getString(constant, null);
    }

    public void saveInt(String constant, int val) {
        SharedPreferences.Editor editor = ctx.getSharedPreferences(ctx.getPackageName(), MODE_PRIVATE).edit();
        editor.putInt(constant, val);
        editor.apply();
    }

    public int loadInt(String constant, int defValue) {
        SharedPreferences sharedPrefs = ctx.getSharedPreferences(ctx.getPackageName(), MODE_PRIVATE);
        return sharedPrefs.getInt(constant, defValue);
    }

    public void saveSetting(String constant, boolean b) {
        SharedPreferences.Editor editor = ctx.getSharedPreferences(ctx.getPackageName(), MODE_PRIVATE).edit();
        editor.putBoolean(constant, b);
        editor.apply();
    }

    public boolean loadSetting(String constant) {
        boolean def = false;
        if (constant.equals(Constants.FORCE_DOWNLOAD) || constant.equals(Constants.IS_LOGGER_VISIBLE) || constant.equals(Constants.RESTRICT_MODIFY)) {
            def = true;
        }
        SharedPreferences sharedPrefs = ctx.getSharedPreferences(ctx.getPackageName(), MODE_PRIVATE);
        return sharedPrefs.getBoolean(constant, def);
    }

    public void saveRoot(String path) {
        SharedPreferences.Editor editor = ctx.getSharedPreferences(ctx.getPackageName(), MODE_PRIVATE).edit();
        editor.putString("SERVER_ROOT", path);
        editor.apply();
    }

    public String loadRoot() {
        SharedPreferences sharedPrefs = ctx.getSharedPreferences(ctx.getPackageName(), MODE_PRIVATE);
        return sharedPrefs.getString("SERVER_ROOT", Environment.getExternalStorageDirectory().getAbsolutePath());
    }

    public boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public void deleteFileOrDir(File fileOrDirectory) {
        try {
            if (fileOrDirectory.isDirectory()) {
                for (File child : fileOrDirectory.listFiles()) {
                    deleteFileOrDir(child);
                }
            }
            fileOrDirectory.delete();
        } catch (Exception e) {
            //Do nothing
        }
    }

    public void clearTemp() {
        File f = new File(Environment.getExternalStorageDirectory() + "/ShareX/.temp");
        if (f.exists()) {
            deleteFileOrDir(f);
        }
    }

    public void clearCache() {
        File f = new File("/data/user/0/" + ctx.getPackageName() + "/cache");
        if (f.exists()) {
            deleteFileOrDir(f);
        }
    }

    public void clearThumbs() {
        try {
            File f = new File(Environment.getExternalStorageDirectory() + "/ShareX/.thumbs");
            if (f.exists()) {
                deleteFileOrDir(f);
            }
        } catch (Exception e) {
            //Do nothing
        }
    }

    public String getMimeType(File f) {
        try {
            if (f.isDirectory()) {
                return "Folder";
            }
            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                uri = FileProvider.getUriForFile(ctx, BuildConfig.APPLICATION_ID + ".provider", f);
            } else {
                uri = Uri.fromFile(f);
            }
            String mimeType = null;
            if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
                ContentResolver cr = ctx.getContentResolver();
                mimeType = cr.getType(uri);
            } else {
                String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase());
            }
            return mimeType;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isUserApp(ApplicationInfo applicationInfo) {
        int mask = ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
        boolean ans = (applicationInfo.flags & mask) == 0;
        String src = applicationInfo.sourceDir;
        boolean b = !src.startsWith("/system");
        return ans && b;
    }

    public SpannableString getSpannableFont(String str) {
        SpannableString ss = new SpannableString(str);
        Typeface typeFace_h = ResourcesCompat.getFont(ctx, R.font.product_sans);
        ss.setSpan(new RelativeSizeSpan(1.0f), 0, ss.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new CustomTypefaceSpan("", typeFace_h), 0, ss.length(), 0);
        return ss;
    }

    public String getIconCode(File f) {
        String mime = getMimeType(f);
        String filename = f.getName();
        if (mime.startsWith("image")) {
            return "<i class=\"fas fa-file-image\"></i>";
        } else if (mime.startsWith("video")) {
            return "<i class=\"fas fa-file-video\"></i>";
        } else if (mime.startsWith("audio")) {
            return "<i class=\"fas fa-file-audio\"></i>";
        } else if (mime.equals("text/plain")) {
            return "<i class=\"fas fa-file-alt\"></i>";
        } else if (mime.equals("application/pdf")) {
            return "<i class=\"fas fa-file-pdf\"></i>";
        } else if (mime.equals("application/zip")) {
            return "<i class=\"fas fa-file-archive\"></i>";
        } else if (filename.endsWith("js")) {
            return "<i class=\"fab fa-js-square\"></i>";
        } else if (filename.endsWith("css")) {
            return "<i class=\"fab fa-css3-alt\"></i>";
        } else if (filename.endsWith("html")) {
            return "<i class=\"fab fa-html5\"></i>";
        } else if (filename.endsWith("php")) {
            return "<i class=\"fab fa-php\"></i>";
        } else {
            return "<i class=\"fas fa-file\"></i>";
        }
    }

    public String getParent(String path) {
        if (path.endsWith("/")) {
            path = path.substring(0, path.lastIndexOf("/"));
        }
        path = path.substring(0, path.lastIndexOf("/"));
        return path;
    }

    public List<String> uriListResolve(List<Uri> uriList) {
        List<String> paths = new ArrayList<>();
        for (Uri uri : uriList) {
            paths.add(UriResolver.getUriRealPath(uri, ctx));
        }
        return paths;
    }

    public void verifyImage(File file) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        int imageHeight = options.outHeight;
        int imageWidth = options.outWidth;
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, file.getName());
        values.put(MediaStore.Images.Media.HEIGHT, imageHeight);
        values.put(MediaStore.Images.Media.WIDTH, imageWidth);
        values.put(MediaStore.Images.Media.MIME_TYPE, getMimeType(file));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
            values.put(MediaStore.Images.ImageColumns.BUCKET_ID, file.getName().toLowerCase(Locale.US).hashCode());
            values.put(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME, file.getName().toLowerCase(Locale.US));
        }
        values.put("_data", file.getAbsolutePath());
        ContentResolver cr = ctx.getContentResolver();
        cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }

    public void verifyVideo(File file) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.TITLE, file.getName());
        values.put(MediaStore.Video.Media.MIME_TYPE, getMimeType(file));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            values.put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis());
        }
        values.put(MediaStore.Video.Media.DISPLAY_NAME, file.getName().toLowerCase(Locale.US));
        values.put(MediaStore.Video.Media.DATA, file.getAbsolutePath());
        ContentResolver cr = ctx.getContentResolver();
        cr.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
    }

}