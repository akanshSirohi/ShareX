package com.akansh.fileserversuit.server;

import android.os.Handler;
import android.util.Log;

import com.akansh.fileserversuit.common.Constants;

import org.json.JSONArray;
import org.json.JSONObject;
import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.http.NanoHTTPD;
import org.nanohttpd.protocols.websockets.CloseCode;
import org.nanohttpd.protocols.websockets.NanoWSD;
import org.nanohttpd.protocols.websockets.WebSocket;
import org.nanohttpd.protocols.websockets.WebSocketFrame;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WebServerSocket extends NanoWSD {

    enum UserActions {
        ADD, REMOVE, UPDATE
    }

    HashMap<String, SocketUser> socketUsers;

    private ScheduledExecutorService agerScheduledExecutorService, killerScheduledExecutorService;

    public WebServerSocket(int port) {
        super(port);
        socketUsers = new HashMap<>();
    }

    @Override
    protected WebSocket openWebSocket(IHTTPSession ihttpSession) {
        return new WsdSocket(ihttpSession);
    }

    private class WsdSocket extends WebSocket {
        public WsdSocket(IHTTPSession handshakeRequest) {
            super(handshakeRequest);
            connectionAgerTimer();
            connectionKillerTimer();
        }

        @Override
        protected void onOpen() {}

        @Override
        protected void onClose(CloseCode closeCode, String s, boolean b) {}

        @Override
        protected void onMessage(WebSocketFrame webSocketFrame) {
            try {
                JSONObject jsonObject = new JSONObject(webSocketFrame.getTextPayload());
                String action = jsonObject.getString("action");
                String package_user = jsonObject.getString("package_name");
                switch (action) {
                    case SocketActions.PING:
                        // Get called every 10 seconds
                        String uuid = jsonObject.getString("uuid");
                        Log.d(Constants.LOG_TAG,"GOT Ping for uuid: " + uuid);
                        for (Map.Entry<String, SocketUser> entry : socketUsers.entrySet()) {
                            // Age All Users
                            if(uuid.equals(entry.getKey())) {
                                SocketUser socketUser = entry.getValue();
                                socketUser.set_alive(true);
                                socketUsers.put(entry.getKey(), socketUser);
                                Log.d(Constants.LOG_TAG,"Make A Connection Alive!");
                                break;
                            }
                        }
//                        if(socketUsers.containsKey(uuid)) {
//                            SocketUser socketUser = socketUsers.get(uuid);
//                            socketUser.set_alive(true);
//                            socketUsers.put(uuid, socketUser);
//                            Log.d(Constants.LOG_TAG,"Make A Connection Alive!");
//                        }
                        break;
                    case SocketActions.INIT_USER:
                        handleSocketUser(package_user, jsonObject.getJSONObject("data"), UserActions.ADD);
                        break;
                    case SocketActions.UPDATE_USER:
                        handleSocketUser(package_user, jsonObject.getJSONObject("data"), UserActions.UPDATE);
                        break;
                    case SocketActions.GET_ALL_USERS:
                        JSONArray users_array = getAllUsersByPackage(package_user);
                        JSONObject users_response_obj = new JSONObject();
                        users_response_obj.put("action", SocketActions.RETURN_ALL_USERS);
                        users_response_obj.put("all_users", users_array);
                        send(users_response_obj.toString());
                        break;
                }
            } catch (Exception e) {}
        }

        @Override
        protected void onPong(WebSocketFrame webSocketFrame) {}

        @Override
        protected void onException(IOException e) {}

        public void handleSocketUser(String package_name, JSONObject jsonData, UserActions action) {
            try {
                SocketUser socketUser;
                switch (action) {
                    case ADD:
                        socketUser = new SocketUser(jsonData.getString("uuid"), jsonData.getJSONObject("public_data").toString(), package_name);
                        socketUsers.put(jsonData.getString("uuid"), socketUser);
                        break;
                    case REMOVE:
                        socketUsers.remove(jsonData.getString("uuid"));
                        break;
                    case UPDATE:
                        if(socketUsers.containsKey(jsonData.getString("uuid"))) {
                            socketUser = new SocketUser(jsonData.getString("uuid"), jsonData.getJSONObject("public_data").toString(), package_name);
                            socketUsers.put(jsonData.getString("uuid"), socketUser);
                        }
                        break;
                }
            }catch (Exception e) {}
        }

        public void connectionAgerTimer() {
            agerScheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
            agerScheduledExecutorService.scheduleAtFixedRate(() -> {
                for (Map.Entry<String, SocketUser> entry : socketUsers.entrySet()) {
                    // Age All Users
                    SocketUser socketUser = entry.getValue();
                    socketUser.set_alive(false);
                    socketUsers.put(entry.getKey(), socketUser);
                }
            }, 10, 10, TimeUnit.SECONDS);
        }

        public void connectionKillerTimer() {
            killerScheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
            // Schedule the task to run every 15 seconds
            killerScheduledExecutorService.scheduleAtFixedRate(() -> {
                HashMap<String, SocketUser> aliveSocketUsers = new HashMap<>();
                for (Map.Entry<String, SocketUser> entry : socketUsers.entrySet()) {
                    SocketUser socketUser = entry.getValue();
                    if(socketUser.is_alive()) {
                        aliveSocketUsers.put(entry.getKey(), socketUser);
                    }
                }
                socketUsers.clear();
                socketUsers.putAll(aliveSocketUsers);
            }, 20, 20, TimeUnit.SECONDS);
        }

        public JSONArray getAllUsersByPackage(String package_name) {
            JSONArray jsonArray = new JSONArray();
            for (Map.Entry<String, SocketUser> entry : socketUsers.entrySet()) {
                if(entry.getValue().getPlugin_package().equals(package_name)) {
                    jsonArray.put(entry.getValue().getJSONObject());
                }
            }
            return jsonArray;
        }
    }

    @Override
    public synchronized void closeAllConnections() {
        if (agerScheduledExecutorService != null && !agerScheduledExecutorService.isShutdown()) {
            agerScheduledExecutorService.shutdown();
        }
        if (killerScheduledExecutorService != null && !killerScheduledExecutorService.isShutdown()) {
            killerScheduledExecutorService.shutdown();
        }
        super.closeAllConnections();
    }
}
