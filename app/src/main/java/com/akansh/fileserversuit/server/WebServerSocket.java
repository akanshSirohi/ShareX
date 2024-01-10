package com.akansh.fileserversuit.server;

import android.util.Log;

import com.akansh.fileserversuit.common.Constants;
import com.akansh.fileserversuit.common.SocketActions;

import org.json.JSONArray;
import org.json.JSONObject;
import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.websockets.NanoWSD;
import org.nanohttpd.protocols.websockets.WebSocket;

import java.util.HashMap;
import java.util.Map;

public class WebServerSocket extends NanoWSD {

    HashMap<String, SocketUser> socketUsers;
    String appPackageName;

    public WebServerSocket(int port, String appPackageName) {
        super(port);
        socketUsers = new HashMap<>();
        this.appPackageName = appPackageName;
    }

    @Override
    protected WebSocket openWebSocket(IHTTPSession ihttpSession) {
        WSDSocket wsdSocket = new WSDSocket(ihttpSession, appPackageName);
        wsdSocket.setWsdSocketListener(new WSDSocket.WsdSocketListener() {
            @Override
            public void onNewUser(SocketUser socketUser, WSDSocket socket) {
                socketUser.setWsdSocket(socket);
                socketUsers.put(socketUser.getUuid(), socketUser);
                try {
                    JSONObject newUserObject = new JSONObject();
                    newUserObject.put("action", SocketActions.USER_ARRIVE);
                    newUserObject.put("user", socketUser.getJSONObject());
                    for (Map.Entry<String, SocketUser> entry : socketUsers.entrySet()) {
                        SocketUser connectedUser = entry.getValue();
                        if(connectedUser.getPlugin_package().equals(socket.package_name) && !connectedUser.getUuid().equals(socket.uuid)) {
                            connectedUser.getWsdSocket().send(newUserObject.toString());
                        }
                    }
                }catch (Exception e) {
                    Log.d(Constants.LOG_TAG, "User Arrive Error: "+e.getMessage());
                }
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

            @Override
            public void onSendMessageToOther(String receiver_uuid, String message, String sender_package_name) {
                try {
                    if(socketUsers.containsKey(receiver_uuid)) {
                        SocketUser socketUser = socketUsers.get(receiver_uuid);
                        if(socketUser != null) {
                            // Verify if they are both on same app
                            if(socketUser.getPlugin_package().equals(sender_package_name)) {
                                JSONObject jsonObject = new JSONObject();
                                jsonObject.put("action", SocketActions.MSG_ARRIVE);
                                jsonObject.put("message", message);
                                socketUser.getWsdSocket().send(jsonObject.toString());
                            }
                        }
                    }
                }catch (Exception e){
                    Log.d(Constants.LOG_TAG, "Error: "+e.getMessage());
                }
            }

            @Override
            public void onGetPublicDataOfUser(String uuid, WSDSocket socket) {
                try {
                    if(socketUsers.containsKey(uuid)) {
                        SocketUser socketUser = socketUsers.get(uuid);
                        if(socketUser != null) {
                            if(socketUser.getPlugin_package().equals(socket.package_name)) {
                                JSONObject jsonObject = new JSONObject();
                                JSONObject public_data = new JSONObject(socketUser.getPublic_data());
                                jsonObject.put("action", SocketActions.RETURN_PUBLIC_DATA_OF_USER);
                                jsonObject.put("public_data", public_data);
                                socket.send(jsonObject.toString());
                            }
                        }
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