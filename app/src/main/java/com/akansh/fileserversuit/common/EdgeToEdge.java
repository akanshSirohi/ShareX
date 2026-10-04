package com.akansh.fileserversuit.common;

import android.app.Activity;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

/** Keeps system bars visible and consumes their insets once, before child navigation views. */
public final class EdgeToEdge {
    private EdgeToEdge() { }

    public static void apply(Activity activity, View root) {
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        boolean light = (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
        WindowCompat.getInsetsController(activity.getWindow(), root).show(WindowInsetsCompat.Type.systemBars());
        WindowCompat.getInsetsController(activity.getWindow(), root).setAppearanceLightStatusBars(light);
        WindowCompat.getInsetsController(activity.getWindow(), root).setAppearanceLightNavigationBars(light);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            if (view instanceof DrawerLayout) {
                // DrawerLayout does not honor root padding for its children's positions.
                ViewGroup drawer = (ViewGroup) view;
                for (int i = 0; i < drawer.getChildCount(); i++) {
                    drawer.getChildAt(i).setPadding(bars.left, bars.top, bars.right, bars.bottom);
                }
            } else {
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            }
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
