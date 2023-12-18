package com.akansh.plugins.ui;

import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.akansh.fileserversuit.Constants;
import com.akansh.fileserversuit.R;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PluginsStore extends Fragment {

    public PluginsStore() {}

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        new Thread(new Runnable() {
            String appListJsonUrl = "https://api.github.com/repos/akanshSirohi/ShareX-Plugins/contents/apps.json";
            @Override
            public void run() {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(appListJsonUrl)
                        .build();
                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.d(Constants.LOG_TAG,"Connection Error: "+e.getMessage());
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        try {
                            String resp = response.body().string();
                            JSONObject pluginConfigObject = new JSONObject(resp);
                            String encoded_resp = pluginConfigObject.getString("content");
                            encoded_resp = encoded_resp.replace("\n","");
                            byte[] decodedBytes = Base64.decode(encoded_resp, Base64.DEFAULT);
                            String decodedString = new String(decodedBytes, StandardCharsets.UTF_8);
                            // decodedString contains the contents of the apps.json file
                            Log.d(Constants.LOG_TAG,"Response: "+decodedString);
                        }catch (Exception e) {
                            Log.d(Constants.LOG_TAG,"Connection Error: "+e.getMessage());
                        }
                    }
                });
            }
        }).start();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.plugins_store_layout, container, false);
    }
}
