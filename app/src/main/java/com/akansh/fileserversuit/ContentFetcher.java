package com.akansh.fileserversuit;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.net.ssl.HttpsURLConnection;

public class ContentFetcher extends AsyncTask<Void, Void, Void> {

    String packageName;

    public ContentFetcher(String packageName) {
        this.packageName = packageName;
    }

    DownloadListeners downloadListeners;
    boolean status=false;

    @Override
    @SuppressLint("SdCardPath")
    protected void onPreExecute() {
        File pV=new File(String.format("/data/data/%s/%s/index.html",packageName,Constants.OLD_DIR));
        if(pV.exists()) {
            downloadListeners.onDownloadStarted(true);
        }else{
            downloadListeners.onDownloadStarted(false);
        }
    }

    @Override
    @SuppressLint("SdCardPath")
    protected Void doInBackground(Void... voids) {
        try {
            URL file = new URL(Constants.ZIP_DOWNLOAD_URL);
            HttpsURLConnection urlConnection = (HttpsURLConnection) file.openConnection();
            urlConnection.connect();
            InputStream inputStream = urlConnection.getInputStream();
            File f=new File("/data/data/"+packageName,Constants.ZIP_FILE);
            FileOutputStream stream = new FileOutputStream(f);
            int bytesRead;
            byte[] buffer = new byte[2048];
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                stream.write(buffer, 0, bytesRead);

            }
            stream.close();
            inputStream.close();
            File zipFile = new File(String.format("/data/data/%s/%s",packageName,Constants.ZIP_FILE));
            File destDir = new File(String.format("/data/data/%s/%s",packageName,Constants.NEW_DIR));
            status=unzip(zipFile, destDir);
            if(!status) {
                f.delete();
                Log.d(Constants.LOG_TAG,"Failed To Unzip File!");
            }

            // Delete Prev-Ver Files
            Log.d(Constants.LOG_TAG,"Old Version Found!");
            File pV=new File(String.format("/data/data/%s/%s",packageName,Constants.OLD_DIR));
            if(pV.exists()) {
                deleteDirectory(pV);
            }
        }catch (Exception e) {
            Log.d(Constants.LOG_TAG,e.getMessage());
            status=false;
            Log.d(Constants.LOG_TAG,"Failed To Download File!");
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        downloadListeners.onDownloadCompeted(status);
    }


    public interface DownloadListeners {
        void onDownloadCompeted(boolean status);
        void onDownloadStarted(boolean updating);
    }

    public boolean unzip(File zipFile, File targetDirectory) {
        try {
            ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)));
            ZipEntry ze;
            int count;
            byte[] buffer = new byte[8192];
            while ((ze = zis.getNextEntry()) != null) {
                File file = new File(targetDirectory, ze.getName());
                boolean b = ensureZipPathSafety(file, targetDirectory);
                if (!b) {
                    return false;
                }
                File dir = ze.isDirectory() ? file : file.getParentFile();
                if (!dir.isDirectory() && !dir.mkdirs())
                    return false;
                if (ze.isDirectory())
                    continue;
                FileOutputStream fout = new FileOutputStream(file);
                try {
                    while ((count = zis.read(buffer)) != -1)
                        fout.write(buffer, 0, count);
                }catch (Exception e){
                    Log.d(Constants.LOG_TAG,e.getMessage());
                }finally {
                    fout.close();
                }
            }
            zis.close();
            zipFile.delete();
            return true;
        }catch(Exception e) {
            Log.d(Constants.LOG_TAG,e.getMessage());
        }
        return false;
    }

    private boolean ensureZipPathSafety(final File outputFile, final File destDirectory) {
        try {
            String destDirCanonicalPath = destDirectory.getCanonicalPath();
            String outputFileCanonicalPath = outputFile.getCanonicalPath();
            if (!outputFileCanonicalPath.startsWith(destDirCanonicalPath)) {
                return false;
            }
            return true;
        }catch (Exception e) {
            return false;
        }
    }

    public void deleteDirectory(File fileOrDirectory) {
        try {
            if (fileOrDirectory.isDirectory()) {
                File[] files = fileOrDirectory.listFiles();
                if (files != null) {
                    for (File child : files) {
                        deleteDirectory(child);
                    }
                    if (files.length == 0) {
                        fileOrDirectory.delete();
                    }
                }
            } else {
                fileOrDirectory.delete();
            }
            fileOrDirectory.delete();
        }catch (Exception e) {
            // Do Nothing!
        }
    }
}
