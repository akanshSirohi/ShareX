package com.akansh.sharex.ui;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AlertDialog;
import com.akansh.sharex.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class IntroAndThemeTest {
    @Test public void everySlideInflatesWithTheActivityThemeAndRestoresPosition() {
        try (ActivityScenario<IntroActivity> scenario = ActivityScenario.launch(IntroActivity.class)) {
            scenario.onActivity(activity -> {
                int[] layouts = {R.layout.slider1,R.layout.slider2,R.layout.slider3,R.layout.slider4,R.layout.slider5,R.layout.slider6};
                MyPagerAdapter adapter = new MyPagerAdapter(layouts, activity.getApplicationContext());
                FrameLayout container = new FrameLayout(activity);
                for (int index = 0; index < layouts.length; index++) {
                    View slide = (View)adapter.instantiateItem(container, index);
                    assertFalse(((TextView)slide.findViewById(R.id.intro_page_title)).getText().toString().isEmpty());
                    adapter.destroyItem(container, index, slide);
                }
                ((ViewPager)activity.findViewById(R.id.viewPager)).setCurrentItem(4, false);
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals(4, ((ViewPager)activity.findViewById(R.id.viewPager)).getCurrentItem());
                assertEquals("5 of 6", ((TextView)activity.findViewById(R.id.intro_progress)).getText().toString());
            });
        }
    }
    @Test public void themeSelectionAppliesOnlyWhenConfirmed() {
        try (ActivityScenario<IntroActivity> scenario = ActivityScenario.launch(IntroActivity.class)) {
            scenario.onActivity(activity -> {
                AtomicInteger selected = new AtomicInteger(-1);
                AlertDialog dialog = ThemePickerDialog.show(activity, 0, selected::set);
                LinearLayout list = dialog.findViewById(R.id.theme_picker_list);
                assertEquals(7, list.getChildCount());
                list.getChildAt(4).performClick();
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
                assertEquals(-1, selected.get());
                dialog = ThemePickerDialog.show(activity, 0, selected::set);
                ((LinearLayout)dialog.findViewById(R.id.theme_picker_list)).getChildAt(4).performClick();
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                assertEquals(4, selected.get());
            });
        }
    }
}
