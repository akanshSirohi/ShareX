package com.akansh.fileserversuit.ui;

import android.content.Intent;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.akansh.fileserversuit.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PermissionsActivityTest {
    @Test public void setupExplainsBothStepsAndSurvivesRecreation() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), PermissionsActivity.class)
                .putExtra(PermissionsActivity.ONBOARDING, true);
        try (ActivityScenario<PermissionsActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(this::assertSetup);
            scenario.recreate();
            scenario.onActivity(this::assertSetup);
        }
    }

    private void assertSetup(PermissionsActivity activity) {
        assertEquals(activity.getString(R.string.permissions_setup_title),
                ((TextView)activity.findViewById(R.id.permissions_title)).getText().toString());
        assertEquals(PermissionsActivity.hasStorageAccess(activity), activity.findViewById(R.id.permissions_finish).isEnabled());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.permissions_later).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.permission_camera_card).getVisibility());
        assertEquals(PermissionsActivity.hasStorageAccess(activity) ? activity.getString(R.string.permissions_granted) : activity.getString(R.string.permissions_missing),
                ((TextView)activity.findViewById(R.id.permission_storage_status)).getText().toString());
    }

    @Test public void settingsIncludesOptionalCameraAndAlwaysAllowsClosing() {
        try (ActivityScenario<PermissionsActivity> scenario = ActivityScenario.launch(PermissionsActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals(View.VISIBLE, activity.findViewById(R.id.permission_camera_card).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.permissions_later).getVisibility());
                assertTrue(activity.findViewById(R.id.permissions_finish).isEnabled());
                assertEquals(activity.getString(R.string.permissions_done), ((TextView)activity.findViewById(R.id.permissions_finish)).getText().toString());
            });
        }
    }
}
