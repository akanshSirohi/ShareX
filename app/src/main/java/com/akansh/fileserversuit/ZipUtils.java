package com.akansh.fileserversuit;

import android.os.Environment;
import android.util.Log;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;

import java.io.File;
import java.util.ArrayList;

public class ZipUtils {
    public String zip(ArrayList<File> files,String output) {
        File f=new File(Environment.getExternalStorageDirectory()+"/ShareX/.temp");
        f.mkdirs();
        File zipFile=new File(f,output);
        try {
            ZipParameters zipParameters=new ZipParameters();
            zipParameters.setCompressionMethod(CompressionMethod.DEFLATE);
            zipParameters.setCompressionLevel(CompressionLevel.FASTEST);
            zipParameters.setIncludeRootFolder(true);
            zipParameters.setReadHiddenFiles(true);
            zipParameters.setReadHiddenFolders(true);
            ZipFile zip=new ZipFile(zipFile);
            for(File file:files) {
                if(file.isDirectory()) {
                    zip.addFolder(file,zipParameters);
                }else{
                    zip.addFile(file,zipParameters);
                }
            }
        }catch (Exception e) {
            Log.d(Constants.LOG_TAG,"Zip Error: "+e);
        }
        return zipFile.getAbsolutePath();
    }

    public void extractZip(String src, String dest) {
        try(ZipFile zipFile = new ZipFile(src)) {
            zipFile.extractAll(dest);
        }catch (Exception e) {
            Log.d(Constants.LOG_TAG, "Extract Error!");
        }
    }
}
