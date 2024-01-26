package com.akansh.fileserversuit.server;

import android.util.Log;

import com.akansh.fileserversuit.common.Constants;
import com.akansh.fileserversuit.common.SocketActions;

import org.json.JSONObject;
import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.websockets.CloseCode;
import org.nanohttpd.protocols.websockets.WebSocket;
import org.nanohttpd.protocols.websockets.WebSocketFrame;

import java.io.IOException;

class WSDSocket extends WebSocket {
    public String package_name, uuid;

    enum UserActions {
        ADD, REMOVE, UPDATE
    }

    public WsdSocketListener wsdSocketListener;
    private JsonDBHandler jsonDBHandler;

    public WSDSocket(IHTTPSession handshakeRequest, String appPackageName) {
        super(handshakeRequest);
        jsonDBHandler = new JsonDBHandler(appPackageName);
        jsonDBHandler.setJsonDBHandlerListener((action, data) -> {
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put("action", action);
                jsonObject.put("data", data);
                send(jsonObject.toString());
            } catch (Exception e) {}
        });
    }

    public void setWsdSocketListener(WsdSocketListener wsdSocketListener) {
        this.wsdSocketListener = wsdSocketListener;
    }

    @Override
    protected void onOpen() {}

    @Override
    protected void onClose(CloseCode closeCode, String s, boolean b) {
        if(this.wsdSocketListener != null) {
            this.wsdSocketListener.onRemoveUser(this.uuid);
        }
    }

    @Override
    protected void onMessage(WebSocketFrame webSocketFrame) {
        try {
            JSONObject jsonObject = new JSONObject(webSocketFrame.getTextPayload());
            String action = jsonObject.getString("action");
            switch (action) {
                case SocketActions.INIT_USER:
                    handleSocketUser(jsonObject, UserActions.ADD);
                    break;
                case SocketActions.UPDATE_USER_DATA:
                    handleSocketUser(jsonObject, UserActions.UPDATE);
                    break;
                case SocketActions.GET_ALL_USERS:
                    if(this.wsdSocketListener != null) {
                        this.wsdSocketListener.onAllUsersRequest(this);
                    }
                    break;
                case SocketActions.SEND_MSG:
                    if(this.wsdSocketListener != null) {
                        JSONObject contents = jsonObject.getJSONObject("data");
                        this.wsdSocketListener.onSendMessageToOther(contents.getString("uuid"), contents.getString("msg"), package_name);
                    }
                    break;
                case SocketActions.GET_PUBLIC_DATA_OF_USER:
                    if(this.wsdSocketListener != null) {
                        JSONObject contents = jsonObject.getJSONObject("data");
                        String uuid = contents.getString("uuid");
                        this.wsdSocketListener.onGetPublicDataOfUser(uuid, this);
                    }
                    break;
                case SocketActions.CREATE_JSON_FILE:
                    if(this.wsdSocketListener != null) {
                        JSONObject contents = jsonObject.getJSONObject("data");
                        String file_name = contents.getString("filename");
                        String file_data = contents.getJSONObject("data").toString();
                        boolean res = jsonDBHandler.createJsonFile(file_name, file_data);
                        JSONObject response_obj = new JSONObject();
                        response_obj.put("action", SocketActions.RETURN_CREATE_JSON_FILE);
                        response_obj.put("result", res);
                        send(response_obj.toString());
                    }
                    break;
                case SocketActions.READ_JSON_FILE:
                    if(this.wsdSocketListener != null) {
                        JSONObject contents = jsonObject.getJSONObject("data");
                        String file_name = contents.getString("filename");
                        String file_data = jsonDBHandler.readJsonFile(file_name);
                        JSONObject response_obj = new JSONObject();
                        response_obj.put("action", SocketActions.RETURN_READ_JSON_FILE);
                        response_obj.put("data", file_data);
                        send(response_obj.toString());
                    }
                    break;
                default:
                    if(action.startsWith("db_action_")) {
                        String db_action = action.replace("db_action_", "");
                        jsonDBHandler.handleActions(db_action, jsonObject.getJSONObject("data").toString());
                    }
                    break;
            }
        } catch (Exception e) {}
    }

    @Override
    protected void onPong(WebSocketFrame webSocketFrame) {}

    @Override
    protected void onException(IOException e) {}

    public void handleSocketUser(JSONObject jsonObject, UserActions action) {
        try {
            JSONObject jsonData = jsonObject.getJSONObject("data");
            switch (action) {
                case ADD:
                    if(this.wsdSocketListener != null) {
                        SocketUser socketUser = new SocketUser(jsonData.getString("uuid"), jsonData.getJSONObject("public_data").toString(), jsonObject.getString("package_name"));
                        this.package_name = jsonObject.getString("package_name");
                        this.uuid = jsonData.getString("uuid");
                        this.wsdSocketListener.onNewUser(socketUser, this);
                        jsonDBHandler.setPlugin_package(this.package_name);
                    }
                    break;
                case UPDATE:
                    if(this.wsdSocketListener != null) {
                        this.wsdSocketListener.onUpdateUserData(jsonData.getJSONObject("public_data").toString(), this);
                    }
                    break;
            }
        } catch (Exception e) {

        }
    }

    public interface WsdSocketListener {
        void onNewUser(SocketUser socketUser, WSDSocket socket);
        void onUpdateUserData(String public_data, WSDSocket socket);
        void onAllUsersRequest(WSDSocket socket);
        void onRemoveUser(String uuid);
        void onSendMessageToOther(String receiver_uuid, String message, String sender_package_name);
        void onGetPublicDataOfUser(String uuid, WSDSocket socket);
    }
}
