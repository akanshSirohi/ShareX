package com.akansh.sharex.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.akansh.sharex.R;
import com.akansh.sharex.common.Constants;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.function.IntConsumer;

public final class WebAccessDialog {
    private WebAccessDialog() {}

    public static AlertDialog show(Context context, boolean privateMode, boolean restricted, IntConsumer decision) {
        View content = LayoutInflater.from(context).inflate(R.layout.dialog_web_access, null);
        ((TextView) content.findViewById(R.id.web_access_scope)).setText(privateMode
                ? R.string.web_access_scope_private : restricted
                ? R.string.web_access_scope_restricted : R.string.web_access_scope_full);
        MaterialCheckBox remember = content.findViewById(R.id.web_access_remember);
        AlertDialog dialog = new MaterialAlertDialogBuilder(context).setView(content).setCancelable(false).create();
        content.findViewById(R.id.web_access_allow).setOnClickListener(view -> {
            decision.accept(remember.isChecked() ? Constants.DEVICE_TYPE_PERMANENT : Constants.DEVICE_TYPE_TEMP);
            dialog.dismiss();
        });
        content.findViewById(R.id.web_access_deny).setOnClickListener(view -> {
            decision.accept(Constants.DEVICE_TYPE_DENIED);
            dialog.dismiss();
        });
        dialog.show();
        return dialog;
    }
}
