package com.akansh.fileserversuit;

import android.app.Activity;
import android.content.Context;
import android.view.View;

import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;

public class FabActionsHandler {
    Activity activity;
    Context ctx;
    FabActionsHandlerListener fabActionsHandlerListener;
    FloatingActionMenu materialDesignFAM;
    FloatingActionButton floatingActionButton1, floatingActionButton2;

    public FabActionsHandler(Activity activity, Context ctx) {
        this.activity = activity;
        this.ctx = ctx;
    }

    public void init() {
        materialDesignFAM = activity.findViewById(R.id.material_design_android_floating_action_menu);
        floatingActionButton1 = activity.findViewById(R.id.material_design_floating_action_menu_item1);
        floatingActionButton2 = activity.findViewById(R.id.material_design_floating_action_menu_item2);
        floatingActionButton1.setOnClickListener(v -> {
            if(fabActionsHandlerListener!=null) {
                fabActionsHandlerListener.onClickImageSelect();
            }
        });
        floatingActionButton2.setOnClickListener(v -> {
            if(fabActionsHandlerListener!=null) {
                fabActionsHandlerListener.onClickFilesSelect();
            }
        });
    }

    public void hideFab() {
        materialDesignFAM.close(true);
    }

    public void setLabels(String imgLbl,String filesLbl) {
        try {
            floatingActionButton1.setLabelText(imgLbl);
            floatingActionButton2.setLabelText(filesLbl);
        }catch (Exception e) {
            //Do nothing...
        }
    }

    public void setFabActionsHandlerListener(FabActionsHandlerListener fabActionsHandlerListener) {
        this.fabActionsHandlerListener = fabActionsHandlerListener;
    }

    public interface FabActionsHandlerListener {
        void onClickImageSelect();
        void onClickFilesSelect();
    }
}
