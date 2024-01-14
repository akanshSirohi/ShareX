package com.akansh.fileserversuit.server;

import android.util.Log;

import com.akansh.fileserversuit.common.Constants;
import com.akansh.fileserversuit.common.JsonDBActions;
import com.jayway.jsonpath.JsonPath;

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

    public void setPlugin_package(String plugin_package) {
        this.pluginFilesDir = new File(String.format("/data/data/%s/plugins/plugin_files/%s", appPackageName, plugin_package));

        boolean b = this.pluginFilesDir.mkdirs();
        Log.d(Constants.LOG_TAG, "Plugin Files Dir: " + b);
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
                        Log.d(Constants.LOG_TAG, "InitDB Error: " + e.getMessage());
                        this.jsonDBHandlerListener.onJsonDBHandlerResponse(prepare_action(JsonDBActions.INIT_DB_RESULT), "fail");
                    }
                } else {
                    this.jsonDBHandlerListener.onJsonDBHandlerResponse(prepare_action(JsonDBActions.INIT_DB_RESULT), "fail");
                }
                break;
            case JsonDBActions.INSERT_DATA:
                insertData(data, false);
                break;
            case JsonDBActions.INSERT_DATA_BULK:
                insertData(data, true);
                break;
            case JsonDBActions.GET_ALL_DATA:
                findAll(data);
                break;
            case JsonDBActions.GET_DATA:
                find(data);
                break;
        }
    }

    private void insertData(String data, boolean isBulk) {
        try {
            JSONObject jsonObject = new JSONObject(data);
            String db_name = jsonObject.getString("db_name");
            File dbFile = new File(pluginFilesDir, db_name + ".json");
            String collection = jsonObject.getString("collection");
            String new_data = jsonObject.getString("new_data");
            JSONObject result_response = new JSONObject();

            if (dbFile.exists()) {
                String dbFileContent = readFile(dbFile);
                JSONObject mainDBJsonObject = new JSONObject(dbFileContent);
                JSONArray collectionArray = mainDBJsonObject.optJSONArray(collection);

                if (collectionArray == null) {
                    collectionArray = new JSONArray();
                    mainDBJsonObject.put(collection, collectionArray);
                }

                JSONArray insertArray = isBulk ? new JSONArray(new_data) : new JSONArray().put(new JSONObject(new_data));
                Log.d(Constants.LOG_TAG, "Insert Data: " + insertArray.toString());


                JSONArray inserted_ids = new JSONArray();

                for (int i = 0; i < insertArray.length(); i++) {
                    JSONObject insertObj = insertArray.getJSONObject(i);
                    collectionArray.put(insertObj);
                    if (insertObj.has("_uuid")) {
                        inserted_ids.put(insertObj.getString("_uuid"));
                    }
                }

                mainDBJsonObject.put(collection, collectionArray);

                boolean res = writeFile(dbFile, mainDBJsonObject.toString());
                result_response.put("status", res ? "success" : "fail");

                if (inserted_ids.length() > 0) {
                    result_response.put("inserted_uuid", inserted_ids);
                }
            }else {
                result_response.put("status","fail");
            }
            this.jsonDBHandlerListener.onJsonDBHandlerResponse(prepare_action(JsonDBActions.INSERT_DATA_RESULT), result_response.toString());
        } catch (Exception e) {
            Log.d(Constants.LOG_TAG, "Insert Data Error: " + e.getMessage());
            this.jsonDBHandlerListener.onJsonDBHandlerResponse(prepare_action(JsonDBActions.INSERT_DATA_RESULT), "{\"status\":\"fail\"}");
        }
    }

    public void findAll(String data) {
        try {
            JSONObject jsonObject = new JSONObject(data);
            JSONArray collectionArray = readCollectionFromDB(jsonObject.getString("db_name"), jsonObject.getString("collection"));
            JSONObject result_response = new JSONObject();
            result_response.put("status", "success");
            result_response.put("data", collectionArray);
            this.jsonDBHandlerListener.onJsonDBHandlerResponse(prepare_action(JsonDBActions.GET_ALL_DATA_RESULT), result_response.toString());
            return;
        } catch (Exception e) {
            Log.d(Constants.LOG_TAG, "Get Collection Error: " + e.getMessage());
        }
        this.jsonDBHandlerListener.onJsonDBHandlerResponse(prepare_action(JsonDBActions.GET_ALL_DATA_RESULT), "{\"status\":\"fail\"}");
    }

    public void find(String data) {
        try {
            JSONObject jsonObject = new JSONObject(data);
            String db_name = jsonObject.getString("db_name");
            String collection = jsonObject.getString("collection");
            String query = jsonObject.getString("query");
            JSONArray collectionArray = readCollectionFromDB(db_name, collection);
            String result = JsonPath.read(collectionArray.toString(), query).toString();
            JSONObject result_response = new JSONObject();
            result_response.put("status", "success");
            result_response.put("data", result);
            this.jsonDBHandlerListener.onJsonDBHandlerResponse(prepare_action(JsonDBActions.GET_DATA_RESULT), result_response.toString());
        }catch (Exception e){
            Log.d(Constants.LOG_TAG, "Find Error: "+e.getMessage());
            this.jsonDBHandlerListener.onJsonDBHandlerResponse(prepare_action(JsonDBActions.GET_DATA_RESULT), "{\"status\":\"fail\"}");
        }
    }


    private JSONArray readCollectionFromDB(String db_name, String collection) {
        try {
            File dbFile = new File(pluginFilesDir, db_name + ".json");
            if (dbFile.exists()) {
                String dbFileContent = readFile(dbFile);
                JSONObject mainDBJsonObject = new JSONObject(dbFileContent);
                JSONArray collectionArray = mainDBJsonObject.optJSONArray(collection);
                if (collectionArray != null) {
                    return collectionArray;
                }
            }
        }catch (Exception e){}
        return new JSONArray();
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
