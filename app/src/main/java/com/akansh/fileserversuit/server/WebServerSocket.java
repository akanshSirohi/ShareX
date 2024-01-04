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

    HashMap<String, SocketUser> socketUsers;

    public WebServerSocket(int port) {
        super(port);
        socketUsers = new HashMap<>();
    }

    @Override
    protected WebSocket openWebSocket(IHTTPSession ihttpSession) {
        WSDSocket wsdSocket = new WSDSocket(ihttpSession);
        wsdSocket.setWsdSocketListener(new WSDSocket.WsdSocketListener() {
            @Override
            public void onNewUser(SocketUser socketUser, WSDSocket socket) {
                socketUser.setWsdSocket(socket);
                socketUsers.put(socketUser.getUuid(), socketUser);
            }

            @Override
            public void onUpdateUserData(String public_data, WSDSocket socket) {
                if(socketUsers.containsKey(socket.uuid)) {
                    SocketUser socketUser = socketUsers.get(socket.uuid);
                    if(socketUser != null) {
                        socketUser.updatePublic_data(public_data);
                        socketUsers.put(socket.uuid, socketUser);
                    }
                }
            }

            @Override
            public void onAllUsersRequest(WSDSocket socket) {
                try {
                    JSONArray users_array = getAllUsersByPackage(socket.package_name);
                    JSONObject users_response_obj = new JSONObject();
                    users_response_obj.put("action", SocketActions.RETURN_ALL_USERS);
                    users_response_obj.put("all_users", users_array);
                    socket.send(users_response_obj.toString());
                }catch (Exception e) {}
            }

            @Override
            public void onRemoveUser(String uuid) {
                socketUsers.remove(uuid);
                try {
                    JSONObject response_obj = new JSONObject();
                    response_obj.put("action", SocketActions.USER_LEFT);
                    response_obj.put("uuid", uuid);
                    for (Map.Entry<String, SocketUser> entry : socketUsers.entrySet()) {
                        SocketUser socketUser = entry.getValue();
                        socketUser.getWsdSocket().send(response_obj.toString());
                    }
                }catch (Exception e){}
            }
        });
        return wsdSocket;
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

    @Override
    public synchronized void closeAllConnections() {
        super.closeAllConnections();
    }
}
