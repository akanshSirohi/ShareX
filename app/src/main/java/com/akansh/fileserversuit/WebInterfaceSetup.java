package com.akansh.fileserversuit;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class WebInterfaceSetup extends AsyncTask<Void, Void, Void> {

    String packageName;
    Context ctx;

    public WebInterfaceSetup(String packageName, Context ctx) {
        this.packageName = packageName;
        this.ctx = ctx;
    }

    SetupListeners setupListeners;
    boolean status=false;

    @Override
    @SuppressLint("SdCardPath")
    protected void onPreExecute() {
        File pV=new File(String.format("/data/data/%s/%s/index.html",packageName,Constants.OLD_DIR));
        if(pV.exists()) {
            setupListeners.onSetupStarted(true);
        }else{
            setupListeners.onSetupStarted(false);
        }
    }

    @Override
    @SuppressLint("SdCardPath")
    protected Void doInBackground(Void... voids) {
        try {
            InputStream myInput =  ctx.getAssets().open(Constants.ZIP_FILE_ASSETS);
            File outFile=new File("/data/data/"+packageName, Constants.ZIP_FILE_ASSETS);
            OutputStream myOutput = new FileOutputStream(outFile);
            byte[] buffer = new byte[2048];
            int length;
            while ((length = myInput.read(buffer)) > 0) {
                myOutput.write(buffer, 0, length);
            }
            // Close the streams
            myOutput.flush();
            myOutput.close();
            myInput.close();

            File zipFile = new File(String.format("/data/data/%s/%s",packageName,Constants.ZIP_FILE_ASSETS));
            File destDir = new File(String.format("/data/data/%s/%s",packageName,Constants.NEW_DIR));
            status=unzip(zipFile, destDir);
            if(!status) {
                outFile.delete();
                Log.d(Constants.LOG_TAG,"Failed To Unzip File!");
            }

            // Delete Prev-Ver Files
            Log.d(Constants.LOG_TAG,"Old Version Found!");
            File pV=new File(String.format("/data/data/%s/%s",packageName,Constants.OLD_DIR));
            if(pV.exists()) {
                deleteDirectory(pV);
            }
        }catch (Exception e) {
            Log.d(Constants.LOG_TAG,e.toString());
            status=false;
            Log.d(Constants.LOG_TAG,"Failed To Download File!");
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        setupListeners.onSetupCompeted(status);
    }


    public interface SetupListeners {
        void onSetupCompeted(boolean status);
        void onSetupStarted(boolean updating);
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
