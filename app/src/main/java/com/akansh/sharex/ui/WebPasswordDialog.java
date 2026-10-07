package com.akansh.sharex.ui;

import android.app.Dialog;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ScrollView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.akansh.sharex.R;
import com.akansh.sharex.common.Constants;
import com.akansh.sharex.common.Utils;
import com.akansh.sharex.server.WebPasswordSettings;
import com.akansh.sharex.server.WebSecurity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public final class WebPasswordDialog extends DialogFragment {
    public static final String TAG = "web-password-settings";
    private WebPasswordSettings settings;
    private View content;
    private int hours;
    private boolean saving;

    @NonNull @Override public Dialog onCreateDialog(Bundle state) {
        settings = new WebPasswordSettings(requireContext().getApplicationContext());
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        android.content.Context dialogContext = builder.getContext();
        content = LayoutInflater.from(dialogContext).inflate(R.layout.dialog_web_password, null);
        MaterialSwitch enabled = content.findViewById(R.id.web_password_enabled);
        MaterialSwitch exempt = content.findViewById(R.id.web_password_exempt);
        enabled.setChecked(state == null ? settings.enabled() : state.getBoolean("enabled", settings.enabled()));
        exempt.setChecked(state == null ? settings.exemptRemembered() : state.getBoolean("exempt", settings.exemptRemembered()));
        View options = content.findViewById(R.id.web_password_options);
        options.setVisibility(enabled.isChecked() ? View.VISIBLE : View.GONE);
        enabled.setOnCheckedChangeListener((button, checked) -> options.setVisibility(checked ? View.VISIBLE : View.GONE));
        content.findViewById(R.id.web_password_ssl_hint).setVisibility(new Utils(requireContext()).loadSetting(Constants.SSL) ? View.GONE : View.VISIBLE);
        hours = state == null ? settings.hours() : state.getInt("session_hours", settings.hours());
        MaterialAutoCompleteTextView duration = content.findViewById(R.id.web_password_duration);
        String[] labels = getResources().getStringArray(R.array.web_password_durations);
        duration.setAdapter(new ArrayAdapter<>(dialogContext, android.R.layout.simple_dropdown_item_1line, labels));
        for (int index=0; index<WebPasswordSettings.HOURS.length; index++) {
            if (WebPasswordSettings.HOURS[index] == hours) duration.setText(labels[index], false);
        }
        duration.setOnItemClickListener((parent, view, index, id) -> hours = WebPasswordSettings.HOURS[index]);
        ScrollView scroll = new ScrollView(dialogContext) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                Rect visible = new Rect(); getWindowVisibleDisplayFrame(visible);
                int available = visible.height() > 0 ? visible.height() : getResources().getDisplayMetrics().heightPixels;
                int limit = Math.min(Math.round(420 * getResources().getDisplayMetrics().density), (int)(available * .60f));
                if (View.MeasureSpec.getMode(heightSpec) != View.MeasureSpec.UNSPECIFIED) limit = Math.min(limit, View.MeasureSpec.getSize(heightSpec));
                super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(limit, View.MeasureSpec.AT_MOST));
            }
        };
        scroll.addView(content);
        return builder.setTitle(R.string.web_password_title).setView(scroll)
                .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.web_password_save, null).create();
    }

    @Override public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog)requireDialog();
        if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> save(dialog));
    }

    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        state.putInt("session_hours", hours);
        if (content != null) {
            state.putBoolean("enabled", ((MaterialSwitch)content.findViewById(R.id.web_password_enabled)).isChecked());
            state.putBoolean("exempt", ((MaterialSwitch)content.findViewById(R.id.web_password_exempt)).isChecked());
        }
        super.onSaveInstanceState(state);
    }

    private void save(AlertDialog dialog) {
        if (saving) return;
        TextInputEditText input = content.findViewById(R.id.web_password_input);
        TextInputLayout field = content.findViewById(R.id.web_password_input_layout);
        String value = input.getText() == null ? "" : input.getText().toString();
        boolean enabled = ((MaterialSwitch)content.findViewById(R.id.web_password_enabled)).isChecked();
        boolean exempt = ((MaterialSwitch)content.findViewById(R.id.web_password_exempt)).isChecked();
        if (enabled && ((!value.isEmpty() && value.length() < 8) || (value.isEmpty() && !settings.configured()))) {
            field.setError(getString(R.string.web_password_error)); input.requestFocus(); return;
        }
        field.setError(null);
        char[] password = enabled ? value.toCharArray() : new char[0];
        int selectedHours = hours;
        saving = true; setCancelable(false);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
        input.setText("");
        android.content.Context appContext = requireContext().getApplicationContext();
        new Thread(() -> {
            boolean saved;
            try { settings.save(enabled, password.length == 0 ? null : WebSecurity.hashPassword(password), selectedHours, exempt); saved = true; }
            catch (RuntimeException error) { saved = false; }
            finally { java.util.Arrays.fill(password, '\0'); }
            final boolean success = saved;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                Toast.makeText(appContext, success ? R.string.web_password_saved : R.string.web_password_failure, Toast.LENGTH_SHORT).show();
                if (!isAdded() || !dialog.isShowing()) return;
                if (success) dismissAllowingStateLoss();
                else {
                    saving = false; setCancelable(true);
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                }
            });
        }, "ShareX-Password").start();
    }

    @Override public void onDestroyView() {
        if (content != null) ((TextInputEditText)content.findViewById(R.id.web_password_input)).setText("");
        content = null;
        super.onDestroyView();
    }
}
