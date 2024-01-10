package com.akansh.fileserversuit.server;

import android.util.Log;

import com.akansh.fileserversuit.common.Constants;
import com.akansh.fileserversuit.common.JsonDBActions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;

public class JsonDBHandler {

    JsonDBHandlerListener jsonDBHandlerListener;
    String appPackageName;
    File pluginFilesDir;

    public JsonDBHandler(String appPackageName) {
        this.appPackageName = appPackageName;
    }

    public void setPlugin_uuid(String plugin_uuid) {
        this.pluginFilesDir = new File(String.format("/data/data/%s/plugins/plugin_files/%s", appPackageName, plugin_uuid));
        this.pluginFilesDir.mkdirs();
    }

    public void setJsonDBHandlerListener(JsonDBHandlerListener jsonDBHandlerListener) {
        this.jsonDBHandlerListener = jsonDBHandlerListener;
    }

    public void handleActions(String action, String data) {
        switch (action) {
            case JsonDBActions.INIT_DB:
                if (pluginFilesDir.exists()) {
                    try {
                        JSONObject jsonObject = new JSONObject(data);
                        String db_name = jsonObject.getString("db_name");
                        File dbFile = new File(pluginFilesDir, db_name + ".json");
                        boolean dbFileExists = dbFile.exists();
                        if (!dbFile.exists()) {
                            dbFileExists = dbFile.createNewFile();
                        }
                        String result_msg = dbFileExists ? "success" : "fail";
                        this.jsonDBHandlerListener.onJsonDBHandlerResponse(prepare_action(JsonDBActions.INIT_DB_RESULT), result_msg);
                    } catch (Exception e) {
                        Log.d(Constants.LOG_TAG, "InitDB Error: "+e.getMessage());
                        this.jsonDBHandlerListener.onJsonDBHandlerResponse(prepare_action(JsonDBActions.INIT_DB_RESULT), "fail");
                    }
                } else {
                    this.jsonDBHandlerListener.onJsonDBHandlerResponse(prepare_action(JsonDBActions.INIT_DB_RESULT), "fail");
                }
                break;
            case JsonDBActions.INSERT_DATA:
                insertData(data);
                break;
        }
    }

    private void insertData(String data) {
        try {
            JSONObject jsonObject = new JSONObject(data);
            String db_name = jsonObject.getString("db_name");
            File dbFile = new File(pluginFilesDir, db_name + ".json");
            String collection = jsonObject.getString("collection");
            String new_data = jsonObject.getString("new_data");
            if (dbFile.exists()) {
                String dbFileContent = readFile(dbFile);
                JSONObject mainDBJsonObject = new JSONObject(dbFileContent);
                JSONArray jsonArray = new JSONArray();
                if (mainDBJsonObject.has(collection)) {
                    jsonArray = mainDBJsonObject.getJSONArray(collection);
                    jsonArray.put(new JSONObject(new_data));
                    mainDBJsonObject.put(collection, jsonArray);
                }else{
                    jsonArray.put(new JSONObject(new_data));
                }
                mainDBJsonObject.put(collection, jsonArray);
                boolean res = writeFile(dbFile, mainDBJsonObject.toString());
                String result_msg = res ? "success" : "fail";
                this.jsonDBHandlerListener.onJsonDBHandlerResponse(prepare_action(JsonDBActions.INSERT_DATA_RESULT), result_msg);
                return;
            }
            this.jsonDBHandlerListener.onJsonDBHandlerResponse(prepare_action(JsonDBActions.INSERT_DATA_RESULT), "fail");
        } catch (Exception e) {
            Log.d(Constants.LOG_TAG, "Insert Data Error: "+e.getMessage());
            this.jsonDBHandlerListener.onJsonDBHandlerResponse(prepare_action(JsonDBActions.INSERT_DATA_RESULT), "fail");
        }
    }

    private String readFile(File file) {
        try {
            BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
            }
            bufferedReader.close();
            if (stringBuilder.length() > 0) {
                return stringBuilder.toString();
            }
        }catch (Exception e){
            Log.d(Constants.LOG_TAG, "Read File Error: "+e.getMessage());
        }
        return "{}";
    }

    private boolean writeFile(File file, String data) {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
            bufferedOutputStream.write(data.getBytes());
            bufferedOutputStream.flush();
            bufferedOutputStream.close();
            return true;
        }catch (Exception e){
            Log.d(Constants.LOG_TAG, "Write File Error: "+e.getMessage());
        }
        return false;
    }

    private String prepare_action(String action) {
        return "db_action_" + action;
    }

    public interface JsonDBHandlerListener {
        void onJsonDBHandlerResponse(String action, String data);
    }
}
