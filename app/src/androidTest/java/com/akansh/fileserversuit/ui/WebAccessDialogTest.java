package com.akansh.fileserversuit.ui;

import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.akansh.fileserversuit.R;
import com.akansh.fileserversuit.common.Constants;
import com.google.android.material.checkbox.MaterialCheckBox;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class WebAccessDialogTest {
    @Test
    public void decisionsAndScopeMatchCurrentSharingSettings() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                AtomicInteger decision = new AtomicInteger(99);
                androidx.appcompat.app.AlertDialog dialog = WebAccessDialog.show(activity, false, false, decision::set);
                assertEquals(activity.getString(R.string.web_access_scope_full), ((TextView) dialog.findViewById(R.id.web_access_scope)).getText().toString());
                assertFalse(((MaterialCheckBox) dialog.findViewById(R.id.web_access_remember)).isChecked());
                dialog.findViewById(R.id.web_access_allow).performClick();
                assertEquals(Constants.DEVICE_TYPE_TEMP, decision.get());
                assertFalse(dialog.isShowing());

                dialog = WebAccessDialog.show(activity, false, true, decision::set);
                assertEquals(activity.getString(R.string.web_access_scope_restricted), ((TextView) dialog.findViewById(R.id.web_access_scope)).getText().toString());
                ((MaterialCheckBox) dialog.findViewById(R.id.web_access_remember)).setChecked(true);
                dialog.findViewById(R.id.web_access_allow).performClick();
                assertEquals(Constants.DEVICE_TYPE_PERMANENT, decision.get());

                dialog = WebAccessDialog.show(activity, true, true, decision::set);
                assertEquals(activity.getString(R.string.web_access_scope_private), ((TextView) dialog.findViewById(R.id.web_access_scope)).getText().toString());
                ((MaterialCheckBox) dialog.findViewById(R.id.web_access_remember)).setChecked(true);
                dialog.findViewById(R.id.web_access_deny).performClick();
                assertEquals(Constants.DEVICE_TYPE_DENIED, decision.get());
            });
        }
    }
}
