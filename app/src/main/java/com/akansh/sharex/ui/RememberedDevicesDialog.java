package com.akansh.sharex.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.akansh.sharex.R;
import com.akansh.sharex.server.DeviceManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.List;

public final class RememberedDevicesDialog {
    private RememberedDevicesDialog() {}

    public static AlertDialog show(Context context, DeviceManager manager, Runnable changed) {
        View content = LayoutInflater.from(context).inflate(R.layout.dialog_remembered_devices, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.remembered_devices_title).setView(content)
                .setPositiveButton(R.string.remembered_devices_close, null)
                .setNeutralButton(R.string.remembered_devices_clear_all, null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                manager.clearRemembered();
                changed.run();
                refresh(context, manager, changed, dialog, content);
            });
            refresh(context, manager, changed, dialog, content);
        });
        dialog.show();
        return dialog;
    }

    private static void refresh(Context context, DeviceManager manager, Runnable changed, AlertDialog dialog, View content) {
        List<DeviceManager.RememberedDevice> devices = manager.getRememberedDevices();
        LinearLayout list = content.findViewById(R.id.remembered_devices_list);
        list.removeAllViews();
        content.findViewById(R.id.remembered_devices_empty).setVisibility(devices.isEmpty() ? View.VISIBLE : View.GONE);
        content.findViewById(R.id.remembered_devices_scroll).setVisibility(devices.isEmpty() ? View.GONE : View.VISIBLE);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(!devices.isEmpty());
        View scroll = content.findViewById(R.id.remembered_devices_scroll);
        scroll.getLayoutParams().height = Math.min(dp(context, Math.min(240, devices.size() * 100)), (int) (context.getResources().getDisplayMetrics().heightPixels * 0.35f));
        scroll.requestLayout();
        for (DeviceManager.RememberedDevice device : devices) {
            String name = device.name.isEmpty() ? context.getString(R.string.remembered_devices_unknown) : device.name;
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(context, 8), 0, dp(context, 8));
            LinearLayout labels = new LinearLayout(context);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setPadding(0, 0, dp(context, 8), 0);
            TextView title = new TextView(context);
            title.setText(name);
            title.setTextSize(15);
            title.setTextColor(context.getColor(R.color.txt_color));
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            labels.addView(title);
            TextView identifier = new TextView(context);
            String suffix = device.id.substring(Math.max(0, device.id.length() - 8));
            identifier.setText(context.getString(R.string.remembered_devices_identifier, suffix));
            identifier.setTextSize(12);
            identifier.setTextColor(context.getColor(R.color.txt_color_secondary));
            identifier.setPadding(0, dp(context, 4), 0, 0);
            labels.addView(identifier);
            TextView connectedAt = new TextView(context);
            connectedAt.setText(device.lastConnected > 0 ? context.getString(R.string.remembered_devices_last_connected,
                    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                            .format(new java.util.Date(device.lastConnected))) : context.getString(R.string.remembered_devices_date_unknown));
            connectedAt.setTextSize(12);
            connectedAt.setTextColor(context.getColor(R.color.txt_color_secondary));
            connectedAt.setPadding(0, dp(context, 4), 0, 0);
            labels.addView(connectedAt);
            row.addView(labels, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            MaterialButton remove = new MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            remove.setText(R.string.remembered_devices_remove);
            remove.setContentDescription(context.getString(R.string.remembered_devices_remove_named, name));
            remove.setMinHeight(dp(context, 48));
            remove.setOnClickListener(view -> {
                manager.forgetDevice(device.id);
                changed.run();
                refresh(context, manager, changed, dialog, content);
            });
            row.addView(remove);
            list.addView(row);
        }
    }

    private static int dp(Context context, int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
