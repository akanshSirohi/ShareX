package com.akansh.fileserversuit.ui;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.IdlingResource;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.viewpager2.widget.ViewPager2;
import com.akansh.fileserversuit.R;
import com.akansh.fileserversuit.common.Constants;
import com.akansh.fileserversuit.common.Utils;
import com.akansh.fileserversuit.server.WebInterfaceSetup;
import com.akansh.fileserversuit.transfer_history.HistoryDBManager;
import com.akansh.fileserversuit.transfer_history.TransfersFragment;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.navigation.NavigationBarView;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MainNavigationTest {
    @Test public void navigationTapsSwitchImmediatelyWithoutPagerAnimation() {
        java.util.List<Integer> scrollStates = new java.util.ArrayList<>();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
             PagerIdle idle = new PagerIdle(scenario)) {
            assertPage(scenario, MainPagerAdapter.HOME);
            scenario.onActivity(activity -> {
                ViewPager2 pager = activity.findViewById(R.id.main_pager);
                pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                    @Override public void onPageScrollStateChanged(int state) { scrollStates.add(state); }
                });
                NavigationBarView navigation = activity.findViewById(R.id.bottom_nav);
                for (int destination : new int[]{R.id.settings, R.id.home, R.id.trans_hist, R.id.settings}) {
                    navigation.setSelectedItemId(destination);
                    assertEquals(MainPagerAdapter.positionForDestination(destination), pager.getCurrentItem());
                    assertEquals(ViewPager2.SCROLL_STATE_IDLE, pager.getScrollState());
                }
            });
            assertPage(scenario, MainPagerAdapter.SETTINGS);
            assertFalse("Navigation taps must not animate through adjacent pages",
                    scrollStates.contains(ViewPager2.SCROLL_STATE_SETTLING));
        }
    }

    @Test public void swipesAndNavigationShareOneActivityAndKeepTransferRecords() {
        String name = "Navigation " + UUID.randomUUID() + ".txt";
        String path = ApplicationProvider.getApplicationContext().getFilesDir() + "/" + name;
        try (HistoryDBManager history = new HistoryDBManager(ApplicationProvider.getApplicationContext())) {
            history.addTransferHistory(Constants.ITEM_TYPE_SENT, name, "1 B", "2026-10-05", "12:00", "text/plain", path);
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
                 PagerIdle idle = new PagerIdle(scenario)) {
                assertPage(scenario, MainPagerAdapter.HOME);
                onView(withId(R.id.main_pager)).perform(swipeLeft());
                assertPage(scenario, MainPagerAdapter.TRANSFERS);
                onView(allOf(withId(R.id.click_panel), hasDescendant(withText(name)))).perform(swipeLeft());
                assertPage(scenario, MainPagerAdapter.SETTINGS);
                onView(withId(R.id.main_pager)).perform(swipeRight());
                assertPage(scenario, MainPagerAdapter.TRANSFERS);
                onView(allOf(withId(R.id.click_panel), hasDescendant(withText(name)))).perform(swipeRight());
                assertPage(scenario, MainPagerAdapter.HOME);
                onView(allOf(withId(R.id.settings), isDisplayed())).perform(click());
                assertPage(scenario, MainPagerAdapter.SETTINGS);
                onView(allOf(withId(R.id.trans_hist), isDisplayed())).perform(click());
                assertPage(scenario, MainPagerAdapter.TRANSFERS);
                scenario.onActivity(activity -> {
                    assertTrue(activity.getSupportFragmentManager().getFragments().stream().anyMatch(fragment -> fragment instanceof HomeFragment));
                    assertTrue(activity.getSupportFragmentManager().getFragments().stream().anyMatch(fragment -> fragment instanceof TransfersFragment));
                    assertTrue(activity.getSupportFragmentManager().getFragments().stream().anyMatch(fragment -> fragment instanceof SettingsFragment));
                });
                assertTrue(history.getHistory().stream().anyMatch(item -> item.getPath().equals(path)));
            } finally { history.deleteHistory(path); }
        }
    }

    @Test public void transferFiltersAndDestinationSurviveSwipesAndRecreation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
             PagerIdle idle = new PagerIdle(scenario)) {
            onView(allOf(withId(R.id.trans_hist), isDisplayed())).perform(click());
            assertPage(scenario, MainPagerAdapter.TRANSFERS);
            onView(withId(R.id.history_received)).perform(click());
            onView(withId(R.id.type_image)).perform(click());
            onView(withId(R.id.main_pager)).perform(swipeLeft());
            assertPage(scenario, MainPagerAdapter.SETTINGS);
            onView(withId(R.id.main_pager)).perform(swipeRight());
            assertPage(scenario, MainPagerAdapter.TRANSFERS);
            scenario.recreate();
            idle.attach(scenario);
            assertPage(scenario, MainPagerAdapter.TRANSFERS);
            scenario.onActivity(activity -> {
                assertEquals(R.id.history_received, ((MaterialButtonToggleGroup) activity.findViewById(R.id.history_direction)).getCheckedButtonId());
                assertEquals(R.id.type_image, ((ChipGroup) activity.findViewById(R.id.history_types)).getCheckedChipId());
                activity.getOnBackPressedDispatcher().onBackPressed();
            });
            assertPage(scenario, MainPagerAdapter.HOME);
        }
    }

    @Test public void settingsIntentAndQRCodeBackReturnToHome() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), MainActivity.class).putExtra("open_settings", true);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(intent);
             PagerIdle idle = new PagerIdle(scenario)) {
            assertPage(scenario, MainPagerAdapter.SETTINGS);
            scenario.onActivity(activity -> activity.getOnBackPressedDispatcher().onBackPressed());
            assertPage(scenario, MainPagerAdapter.HOME);
            scenario.onActivity(activity -> {
                activity.toggleQRView();
                assertEquals(View.VISIBLE, activity.findViewById(R.id.qr_view).getVisibility());
                assertEquals(View.INVISIBLE, activity.findViewById(R.id.main_pager).getVisibility());
                activity.getOnBackPressedDispatcher().onBackPressed();
                assertEquals(View.GONE, activity.findViewById(R.id.qr_view).getVisibility());
            });
            assertPage(scenario, MainPagerAdapter.HOME);
        }
    }

    private static void assertPage(ActivityScenario<MainActivity> scenario, int position) {
        onView(withId(R.id.main_pager)).check(matches(isDisplayed()));
        scenario.onActivity(activity -> {
            assertEquals(position, ((ViewPager2) activity.findViewById(R.id.main_pager)).getCurrentItem());
            assertEquals(MainPagerAdapter.destinationForPosition(position), ((NavigationBarView) activity.findViewById(R.id.bottom_nav)).getSelectedItemId());
            assertFalse(activity.isFinishing());
        });
    }

    /** Waits for fragment attachment, first-run content extraction, and page animations. */
    private static final class PagerIdle implements IdlingResource, AutoCloseable {
        private MainActivity activity;
        private ResourceCallback callback;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable check = () -> {
            if (isIdleNow() && callback != null) callback.onTransitionToIdle();
        };

        PagerIdle(ActivityScenario<MainActivity> scenario) {
            attach(scenario);
            IdlingRegistry.getInstance().register(this);
        }

        void attach(ActivityScenario<MainActivity> scenario) { scenario.onActivity(value -> activity = value); }
        @Override public String getName() { return "Main fragment pager"; }
        @Override public boolean isIdleNow() {
            boolean ready = !activity.isDestroyed()
                    && activity.findViewById(R.id.serverBtn) != null
                    && activity.findViewById(R.id.settings_web_password) != null
                    && ((ViewPager2) activity.findViewById(R.id.main_pager)).getScrollState() == ViewPager2.SCROLL_STATE_IDLE
                    && new WebInterfaceSetup(activity.getPackageName(), activity, activity, new Utils(activity)).isInstalled();
            if (!ready) {
                handler.removeCallbacks(check);
                handler.postDelayed(check, 32);
            }
            return ready;
        }
        @Override public void registerIdleTransitionCallback(ResourceCallback callback) { this.callback = callback; }
        @Override public void close() {
            IdlingRegistry.getInstance().unregister(this);
            handler.removeCallbacks(check);
        }
    }
}
