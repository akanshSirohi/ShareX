package com.akansh.fileserversuit.common;

import android.os.Environment;
import android.util.Log;

import com.akansh.fileserversuit.common.Constants;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
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
            zip.close();
        }catch (Exception e) {
            Log.d(Constants.LOG_TAG,"Zip Error: "+e);
        }
        return zipFile.getAbsolutePath();
    }

    public boolean extractZip(String src, String dest, boolean deleteZipAfterExtract) {
        try(ZipFile zipFile = new ZipFile(src)) {
            zipFile.extractAll(dest);
            zipFile.close();
            if(deleteZipAfterExtract) {
                File f = new File(src);
                f.delete();
            }
            return true;
        }catch (Exception e) {
            return false;
        }
    }

    public String readFileFromZip(String filename, String zipPath) {
        try(ZipFile zipFile = new ZipFile(zipPath)) {
            FileHeader fileHeader = zipFile.getFileHeader(filename);
            if (fileHeader != null) {
                InputStream inputStream = zipFile.getInputStream(fileHeader);
                BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                StringBuilder result = new StringBuilder();
                while ((line = br.readLine()) != null) {
                    result.append(line);
                }
                inputStream.close();
                return result.toString();
            }else{

            }
        }catch (Exception e) {
            Log.d(Constants.LOG_TAG, "Read Error!");
        }
        return null;
    }
}
