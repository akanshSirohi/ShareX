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
    File f=new File(Environment.getExternalStorageDirectory()+"/ShareX/.temp");
    public ZipUtils() {
        f.mkdirs();
    }
    public String zip(ArrayList<File> files,String output) {
        File zipFile=new File(f,output);
        try {
            ZipParameters zipParameters=new ZipParameters();
            zipParameters.setCompressionMethod(CompressionMethod.DEFLATE);
            zipParameters.setCompressionLevel(CompressionLevel.FASTEST);
            zipParameters.setIncludeRootFolder(true);
            zipParameters.setReadHiddenFiles(true);
            zipParameters.setReadHiddenFolders(true);
            ZipFile zip=new ZipFile(zipFile);
            for(File f:files) {
                if(f.isDirectory()) {
                    zip.addFolder(f,zipParameters);
                }else{
                    zip.addFile(f,zipParameters);
                }
            }
        }catch (Exception e) {
            Log.d(Constants.LOG_TAG,"Zip Error: "+e);
        }
        return zipFile.getAbsolutePath();
    }
}
