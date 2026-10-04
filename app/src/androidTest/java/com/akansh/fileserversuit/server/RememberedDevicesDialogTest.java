package com.akansh.fileserversuit.server;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.akansh.fileserversuit.R;
import com.akansh.fileserversuit.common.Constants;
import com.akansh.fileserversuit.ui.MainActivity;
import com.akansh.fileserversuit.ui.RememberedDevicesDialog;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class RememberedDevicesDialogTest {
    @Test public void listRemovalClearAndEmptyStateStayInSync() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                String database = "devices-dialog-test-" + UUID.randomUUID() + ".db";
                try (DeviceManager manager = new DeviceManager(activity, database)) {
                    manager.addDevice("first", Constants.DEVICE_TYPE_PERMANENT, "Firefox on Linux");
                    manager.addDevice("second", Constants.DEVICE_TYPE_PERMANENT, "Chrome on Windows");
                    manager.addDevice("temporary", Constants.DEVICE_TYPE_TEMP, "Safari on iPhone");
                    AtomicInteger changes = new AtomicInteger();
                    AlertDialog dialog = RememberedDevicesDialog.show(activity, manager, changes::incrementAndGet);
                    try {
                        LinearLayout list = dialog.findViewById(R.id.remembered_devices_list);
                        assertEquals(2, list.getChildCount());
                        LinearLayout first = (LinearLayout) list.getChildAt(0);
                        LinearLayout labels = (LinearLayout) first.getChildAt(0);
                        assertEquals("Chrome on Windows", ((TextView) labels.getChildAt(0)).getText().toString());
                        assertTrue(((TextView) labels.getChildAt(2)).getText().toString().startsWith("Last connected"));
                        first.getChildAt(1).performClick();
                        assertEquals(1, list.getChildCount());
                        assertFalse(manager.isDeviceExist("second"));
                        assertEquals(1, changes.get());
                        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
                        assertEquals(0, list.getChildCount());
                        assertEquals(View.VISIBLE, dialog.findViewById(R.id.remembered_devices_empty).getVisibility());
                        assertFalse(dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled());
                        assertEquals(2, changes.get());
                        assertTrue(manager.isDeviceExist("temporary"));
                    } finally { dialog.dismiss(); }
                    for (int index = 0; index < 40; index++) manager.addDevice("many-" + index, Constants.DEVICE_TYPE_PERMANENT, "Browser " + index);
                    AlertDialog longList = RememberedDevicesDialog.show(activity, manager, changes::incrementAndGet);
                    try {
                        LinearLayout rows = longList.findViewById(R.id.remembered_devices_list);
                        assertEquals(40, rows.getChildCount());
                        View scroll = longList.findViewById(R.id.remembered_devices_scroll);
                        assertTrue(scroll.getLayoutParams().height <= 240 * activity.getResources().getDisplayMetrics().density);
                        assertTrue(scroll.getLayoutParams().height <= activity.getResources().getDisplayMetrics().heightPixels * 0.35f);
                        assertTrue(longList.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled());
                        assertTrue(longList.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
                    } finally { longList.dismiss(); }
                } finally { activity.deleteDatabase(database); }
            });
        }
    }
}
