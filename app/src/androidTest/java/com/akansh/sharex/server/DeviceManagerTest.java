package com.akansh.sharex.server;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.akansh.sharex.common.Constants;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.UUID;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DeviceManagerTest {
    @Test public void migrationPreservesRememberedBrowsersAndBackfillsNames() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String database = "devices-test-" + UUID.randomUUID() + ".db";
        try {
            try (SQLiteDatabase old = context.openOrCreateDatabase(database, Context.MODE_PRIVATE, null)) {
                old.execSQL("CREATE TABLE D_LIST (ID INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, DEVICE_ID TEXT NOT NULL, DEVICE_TYPE INT NOT NULL)");
                old.execSQL("INSERT INTO D_LIST(DEVICE_ID,DEVICE_TYPE) VALUES ('legacy',1),('legacy',1),('other',1),('denied',-1)");
                old.setVersion(1);
            }
            try (DeviceManager manager = new DeviceManager(context, database)) {
                assertEquals(2, manager.getRemDevices());
                assertTrue(manager.isDeviceExist("legacy"));
                assertTrue(manager.isDeviceDenied("denied"));
                assertEquals(0, manager.getRememberedDevices().stream().filter(d -> d.id.equals("legacy")).findFirst().get().lastConnected);
                manager.recordConnection("legacy", "Chrome on Windows");
                assertTrue(manager.getRememberedDevices().stream().filter(d -> d.id.equals("legacy")).findFirst().get().lastConnected > 0);
                manager.updateMissingName("legacy", "Browser");
                assertEquals("Chrome on Windows", manager.getRememberedDevices().stream().filter(d -> d.id.equals("legacy")).findFirst().get().name);
                assertTrue(manager.addDevice("legacy", Constants.DEVICE_TYPE_PERMANENT));
                assertEquals(2, manager.getRemDevices());
            }
        } finally { context.deleteDatabase(database); }
    }

    @Test public void versionTwoMigrationKeepsNamesAndAddsConnectionDate() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String database = "devices-v2-test-" + UUID.randomUUID() + ".db";
        try {
            try (SQLiteDatabase old = context.openOrCreateDatabase(database, Context.MODE_PRIVATE, null)) {
                old.execSQL("CREATE TABLE D_LIST (ID INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, DEVICE_ID TEXT NOT NULL UNIQUE, DEVICE_TYPE INT NOT NULL, DEVICE_NAME TEXT NOT NULL DEFAULT '')");
                old.execSQL("INSERT INTO D_LIST(DEVICE_ID, DEVICE_TYPE, DEVICE_NAME) VALUES ('existing',1,'Chrome on Windows')");
                old.setVersion(2);
            }
            try (DeviceManager manager = new DeviceManager(context, database)) {
                assertEquals(1, manager.getRemDevices());
                assertEquals("Chrome on Windows", manager.getRememberedDevices().get(0).name);
                assertEquals(0, manager.getRememberedDevices().get(0).lastConnected);
                manager.recordConnection("existing", "Browser");
                assertTrue(manager.getRememberedDevices().get(0).lastConnected > 0);
                assertEquals("Chrome on Windows", manager.getRememberedDevices().get(0).name);
            }
        } finally { context.deleteDatabase(database); }
    }

    @Test public void removalAndClearAllOnlyAffectRememberedBrowsers() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String database = "devices-test-" + UUID.randomUUID() + ".db";
        try (DeviceManager manager = new DeviceManager(context, database)) {
            manager.addDevice("one", Constants.DEVICE_TYPE_PERMANENT, "Firefox on Linux");
            manager.addDevice("two", Constants.DEVICE_TYPE_PERMANENT, "Chrome on Windows");
            manager.addDevice("temporary", Constants.DEVICE_TYPE_TEMP, "Safari on iPhone");
            manager.addDevice("denied", Constants.DEVICE_TYPE_DENIED, "Browser");
            assertEquals(2, manager.getRememberedDevices().size());
            assertFalse(manager.isDeviceExist("' OR 1=1 --"));
            assertTrue(manager.forgetDevice("one"));
            assertFalse(manager.isDeviceExist("one"));
            assertTrue(manager.isDeviceExist("two"));
            assertFalse(manager.forgetDevice("temporary"));
            manager.clearRemembered();
            assertEquals(0, manager.getRemDevices());
            assertTrue(manager.isDeviceExist("temporary"));
            assertTrue(manager.isDeviceDenied("denied"));
            manager.clearTmp();
            assertFalse(manager.isDeviceExist("temporary"));
            assertFalse(manager.isDeviceDenied("denied"));
        } finally { context.deleteDatabase(database); }
    }
}
