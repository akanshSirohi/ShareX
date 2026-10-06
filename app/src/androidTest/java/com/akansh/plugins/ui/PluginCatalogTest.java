package com.akansh.plugins.ui;

import android.os.Looper;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.akansh.sharex.R;
import com.akansh.sharex.common.Constants;
import com.akansh.sharex.common.Utils;
import com.akansh.plugins.PluginsManager;
import com.akansh.plugins.StorePluginsAdapter;
import com.akansh.plugins.InstalledPluginsAdapter;
import com.akansh.plugins.common.Plugin;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.allOf;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PluginCatalogTest {
    @Test
    public void pluginCardsUseParentThemeAndStoreTabRendersAfterRecreation() throws Exception {
        AtomicReference<File> catalogFile = new AtomicReference<>();
        AtomicReference<byte[]> previousCatalog = new AtomicReference<>();
        try (ActivityScenario<PluginsActivity> scenario = ActivityScenario.launch(PluginsActivity.class)) {
            scenario.onActivity(activity -> {
                File catalog = new File(activity.getPluginsManager().getPlugins_dir(), Constants.APPS_CONFIG);
                catalogFile.set(catalog);
                try {
                    previousCatalog.set(catalog.exists() ? Files.readAllBytes(catalog.toPath()) : null);
                    Files.write(catalog.toPath(), CATALOG.getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) { throw new AssertionError(e); }
                // Reproduce the original crash: adapters receive an application context.
                // Their cards must still inflate with the themed RecyclerView's context.
                RecyclerView parent = new RecyclerView(activity);
                parent.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(activity));
                ArrayList<Plugin> plugins = new ArrayList<>();
                plugins.add(new Plugin("test", "Test Plugin", "sharex.test.plugin", "Test", "Test", "1.0", 1));
                StorePluginsAdapter store = new StorePluginsAdapter(activity.getApplicationContext(), plugins);
                StorePluginsAdapter.ItemViewHolder holder = store.onCreateViewHolder(parent, 0);
                store.onBindViewHolder(holder, 0);
                assertNotNull(holder.plugin_install_btn.getIcon());
                plugins.get(0).setPlugin_installed(true);
                store.onBindViewHolder(holder, 0);
                assertFalse(holder.plugin_install_btn.isEnabled());
                InstalledPluginsAdapter installed = new InstalledPluginsAdapter(activity.getApplicationContext(), plugins);
                installed.onBindViewHolder(installed.onCreateViewHolder(parent, 0), 0);
            });
            onView(allOf(withText("Store"), isDisplayed())).perform(click());
            onView(withId(R.id.storePluginsList)).check((view, error) -> {
                if (error != null) throw error;
                assertTrue("Store cards should be laid out", ((RecyclerView) view).getChildCount() > 0);
            });
            scenario.recreate();
            onView(withId(R.id.storePluginsList)).check((view, error) -> {
                if (error != null) throw error;
                assertTrue(((RecyclerView) view).getChildCount() > 0);
            });
        } finally {
            if (catalogFile.get() != null) restore(catalogFile.get(), previousCatalog.get());
        }
    }

    private static final String CATALOG = "[{\"name\":\"Test Plugin\","
            + "\"package\":\"sharex.test.plugin\",\"description\":\"Test\","
            + "\"author\":\"Test\",\"version\":\"v3.0.0\",\"versionCode\":3}]";

    @Test
    public void coldStoreRefreshesWhenCatalogArrivesAndCacheCallbackRunsOnMainThread() throws Exception {
        AtomicReference<File> catalogFile = new AtomicReference<>();
        AtomicReference<File> timestampFile = new AtomicReference<>();
        AtomicReference<byte[]> previousCatalog = new AtomicReference<>();
        AtomicReference<byte[]> previousTimestamp = new AtomicReference<>();
        AtomicBoolean backedUp = new AtomicBoolean();
        CountDownLatch callback = new CountDownLatch(1);
        AtomicBoolean callbackSucceeded = new AtomicBoolean();
        AtomicBoolean callbackOnMainThread = new AtomicBoolean();

        try (ActivityScenario<PluginsActivity> scenario = ActivityScenario.launch(PluginsActivity.class)) {
            scenario.onActivity(activity -> {
                PluginsManager manager = new PluginsManager(activity, activity, new Utils(activity));
                File catalog = new File(manager.getPlugins_dir(), Constants.APPS_CONFIG);
                File timestamp = new File(manager.getPlugins_dir(), "last_fetch_timestamp.txt");
                catalogFile.set(catalog);
                timestampFile.set(timestamp);
                try {
                    previousCatalog.set(catalog.exists() ? Files.readAllBytes(catalog.toPath()) : null);
                    previousTimestamp.set(timestamp.exists() ? Files.readAllBytes(timestamp.toPath()) : null);
                    backedUp.set(true);
                    Files.deleteIfExists(catalog.toPath());

                    PluginsStore store = new PluginsStore(activity, activity, manager);
                    // Completion can arrive before the Store view exists.
                    store.updatePluginStore();
                    assertTrue(store.getPluginsListFromJson(catalog).isEmpty());
                    activity.getSupportFragmentManager().beginTransaction()
                            .add(android.R.id.content, store, "catalog-test").commitNow();
                    RecyclerView list = store.requireView().findViewById(R.id.storePluginsList);
                    assertEquals(0, list.getAdapter().getItemCount());

                    Files.write(catalog.toPath(), CATALOG.getBytes(StandardCharsets.UTF_8));
                    store.updatePluginStore();
                    assertEquals(1, list.getAdapter().getItemCount());

                    Files.write(catalog.toPath(), "invalid json".getBytes(StandardCharsets.UTF_8));
                    store.updatePluginStore();
                    assertEquals(0, list.getAdapter().getItemCount());

                    Files.write(catalog.toPath(), CATALOG.getBytes(StandardCharsets.UTF_8));
                    Files.write(timestamp.toPath(), String.valueOf(System.currentTimeMillis() / 1000L)
                            .getBytes(StandardCharsets.UTF_8));
                    manager.fetchPluginAppsFile(false, success -> {
                        callbackSucceeded.set(success);
                        callbackOnMainThread.set(Looper.myLooper() == Looper.getMainLooper());
                        store.updatePluginStore();
                        callback.countDown();
                    });
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
            });
            assertTrue("Catalog completion callback was not delivered", callback.await(5, TimeUnit.SECONDS));
            assertTrue(callbackSucceeded.get());
            assertTrue(callbackOnMainThread.get());
            scenario.onActivity(activity -> {
                PluginsStore store = (PluginsStore) activity.getSupportFragmentManager()
                        .findFragmentByTag("catalog-test");
                RecyclerView list = store.requireView().findViewById(R.id.storePluginsList);
                assertEquals(1, list.getAdapter().getItemCount());
            });
        } finally {
            if (backedUp.get()) {
                restore(catalogFile.get(), previousCatalog.get());
                restore(timestampFile.get(), previousTimestamp.get());
            }
        }
    }

    private static void restore(File file, byte[] contents) throws Exception {
        if (contents == null) {
            Files.deleteIfExists(file.toPath());
        } else {
            Files.write(file.toPath(), contents);
        }
    }
}
