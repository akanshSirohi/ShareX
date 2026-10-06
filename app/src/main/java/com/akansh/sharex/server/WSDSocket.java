package com.akansh.sharex.server;

import com.akansh.sharex.common.SocketActions;

import org.json.JSONObject;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

final class WSDSocket {
    public String package_name;
    public String uuid;
    private final ChannelHandlerContext context;
    private final JsonDBHandler jsonDBHandler;
    private final String developmentPackage;
    private WsdSocketListener wsdSocketListener;

    WSDSocket(ChannelHandlerContext context, String appPackageName, String developmentPackage) {
        this.context = context;
        this.developmentPackage = developmentPackage;
        jsonDBHandler = new JsonDBHandler(appPackageName);
        jsonDBHandler.setJsonDBHandlerListener((action, data) -> {
            JSONObject result = new JSONObject();
            try {
                result.put("action", action);
                result.put("data", data);
                send(result.toString());
            } catch (Exception ignored) { }
        });
    }

    void setWsdSocketListener(WsdSocketListener listener) { wsdSocketListener = listener; }
    void send(String message) { if (context.channel().isActive()) context.writeAndFlush(new TextWebSocketFrame(message)); }
    void close() { context.close(); }

    void onClose() {
        if (wsdSocketListener != null) wsdSocketListener.onRemoveUser(uuid, this);
    }

    void onMessage(String text) {
        try {
            JSONObject jsonObject = new JSONObject(text);
            String action = jsonObject.getString("action");
            if (package_name == null && !SocketActions.INIT_USER.equals(action)) { context.close(); return; }
            switch (action) {
                case SocketActions.INIT_USER:
                    handleSocketUser(jsonObject, true);
                    break;
                case SocketActions.UPDATE_USER_DATA:
                    handleSocketUser(jsonObject, false);
                    break;
                case SocketActions.GET_ALL_USERS:
                    if (wsdSocketListener != null) wsdSocketListener.onAllUsersRequest(this);
                    break;
                case SocketActions.SEND_MSG: {
                    JSONObject data = jsonObject.getJSONObject("data");
                    if (wsdSocketListener != null) wsdSocketListener.onSendMessageToOther(data.getString("uuid"), data.getString("msg"), package_name);
                    break;
                }
                case SocketActions.GET_PUBLIC_DATA_OF_USER: {
                    JSONObject data = jsonObject.getJSONObject("data");
                    if (wsdSocketListener != null) wsdSocketListener.onGetPublicDataOfUser(data.getString("uuid"), this);
                    break;
                }
                case SocketActions.CREATE_JSON_FILE: {
                    JSONObject data = jsonObject.getJSONObject("data");
                    boolean result = jsonDBHandler.createJsonFile(data.getString("filename"), data.getJSONObject("data").toString());
                    JSONObject response = new JSONObject();
                    response.put("action", SocketActions.RETURN_CREATE_JSON_FILE);
                    response.put("result", result);
                    send(response.toString());
                    break;
                }
                case SocketActions.READ_JSON_FILE: {
                    String fileName = jsonObject.getJSONObject("data").getString("filename");
                    JSONObject response = new JSONObject();
                    response.put("action", SocketActions.RETURN_READ_JSON_FILE);
                    response.put("data", jsonDBHandler.readJsonFile(fileName));
                    send(response.toString());
                    break;
                }
                default:
                    if (action.startsWith("db_action_")) jsonDBHandler.handleActions(action.substring("db_action_".length()), jsonObject.getJSONObject("data").toString());
                    break;
            }
        } catch (Exception ignored) { }
    }

    private void handleSocketUser(JSONObject object, boolean add) throws Exception {
        JSONObject data = object.getJSONObject("data");
        if (add) {
            String requestedPackage = object.getString("package_name");
            if (package_name != null || !requestedPackage.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*")
                    || (developmentPackage != null && !developmentPackage.equals(requestedPackage))
                    || (developmentPackage == null && requestedPackage.startsWith("dev."))) {
                context.close();
                return;
            }
            package_name = requestedPackage;
            uuid = data.getString("uuid");
            if (!uuid.matches("[a-fA-F0-9]{8}(?:-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}")) { context.close(); return; }
            jsonDBHandler.setPlugin_package(package_name);
            if (wsdSocketListener != null) wsdSocketListener.onNewUser(new SocketUser(uuid, data.getJSONObject("public_data").toString(), package_name), this);
        } else if (wsdSocketListener != null) {
            wsdSocketListener.onUpdateUserData(data.getJSONObject("public_data").toString(), this);
        }
    }

    interface WsdSocketListener {
        void onNewUser(SocketUser socketUser, WSDSocket socket);
        void onUpdateUserData(String public_data, WSDSocket socket);
        void onAllUsersRequest(WSDSocket socket);
        void onRemoveUser(String uuid, WSDSocket socket);
        void onSendMessageToOther(String receiver_uuid, String message, String sender_package_name);
        void onGetPublicDataOfUser(String uuid, WSDSocket socket);
    }
}
