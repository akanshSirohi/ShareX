package com.akansh.fileserversuit.server;

import com.akansh.fileserversuit.common.JsonDBActions;

public class JsonDBHandler {

    JsonDBHandlerListener jsonDBHandlerListener;

    public JsonDBHandler() {
    }

    public void setJsonDBHandlerListener(JsonDBHandlerListener jsonDBHandlerListener) {
        this.jsonDBHandlerListener = jsonDBHandlerListener;
    }

    public void handleActions(String action, String data) {
        switch (action) {
            case JsonDBActions.INIT_DB:
                if(this.jsonDBHandlerListener != null) {
                    this.jsonDBHandlerListener.onJsonDBHandlerResponse(prepare_action(JsonDBActions.INIT_DB_RESULT), "success");
                }
                break;
        }
    }

    public String prepare_action(String action) {
        return "db_action_" + action;
    }

    public interface JsonDBHandlerListener {
        void onJsonDBHandlerResponse(String action, String data);
    }
}
