package com.akansh.fileserversuit.server;

import android.util.Log;

import com.akansh.fileserversuit.common.Constants;

import org.json.JSONArray;
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

    public WSDSocket(IHTTPSession handshakeRequest) {
        super(handshakeRequest);
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
                case SocketActions.GET_PUBLIC_DATA_OF_USER:
                    if(this.wsdSocketListener != null) {
                        JSONObject contents = jsonObject.getJSONObject("data");
                        String uuid = contents.getString("uuid");
                        this.wsdSocketListener.onGetPublicDataOfUser(uuid, this);
                    }
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
