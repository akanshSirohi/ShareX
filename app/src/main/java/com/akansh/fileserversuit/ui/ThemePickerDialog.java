package com.akansh.fileserversuit.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.akansh.fileserversuit.R;
import com.akansh.fileserversuit.server.ThemesData;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.function.IntConsumer;

public final class ThemePickerDialog {
    private ThemePickerDialog() {}
    public static AlertDialog show(Context context, int current, IntConsumer apply) {
        ThemesData themes = new ThemesData();
        int[] selection = {themes.normalizeIndex(current)};
        String[][] palettes = {{"#fafafa","#171717","#d4d4d4"},{"#f4eee6","#b4896c","#37302a"},{"#ffffff","#212121","#de5148"},{"#000000","#00e400","#323232"},{"#fafafa","#7c3aed","#141521"},{"#fbfcf4","#bbef51","#101326"},{"#faf9f5","#c96442","#403e3c"}};
        View content = LayoutInflater.from(context).inflate(R.layout.dialog_theme_picker, null);
        View scroll = content.findViewById(R.id.theme_picker_scroll);
        scroll.getLayoutParams().height = Math.min(dp(context, 320), (int)(context.getResources().getDisplayMetrics().heightPixels * .45f));
        LinearLayout list = content.findViewById(R.id.theme_picker_list);
        MaterialRadioButton[] buttons = new MaterialRadioButton[themes.getDisplayList().length];
        MaterialCardView[] cards = new MaterialCardView[buttons.length];
        for (int index = 0; index < buttons.length; index++) {
            final int choice = index;
            MaterialCardView card = new MaterialCardView(context);
            cards[index] = card;
            card.setRadius(dp(context, 14));
            card.setCardElevation(0);
            card.setCardBackgroundColor(context.getColor(R.color.surface_container));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = dp(context, 8);
            list.addView(card, params);
            LinearLayout row = new LinearLayout(context);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dp(context, 12), dp(context, 10), dp(context, 8), dp(context, 10));
            card.addView(row, new android.widget.FrameLayout.LayoutParams(-1, -2));
            LinearLayout labels = new LinearLayout(context);
            labels.setOrientation(LinearLayout.VERTICAL);
            TextView name = new TextView(context);
            name.setText(themes.getDisplayItem(index));
            name.setTextSize(15);
            name.setTextColor(context.getColor(R.color.txt_color));
            labels.addView(name);
            LinearLayout chips = new LinearLayout(context);
            chips.setPadding(0, dp(context, 8), 0, 0);
            chips.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            for (String color : palettes[index]) {
                View chip = new View(context);
                GradientDrawable background = new GradientDrawable();
                background.setColor(Color.parseColor(color));
                background.setCornerRadius(dp(context, 5));
                background.setStroke(dp(context, 1), context.getColor(R.color.outline_soft));
                chip.setBackground(background);
                LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(context, 28), dp(context, 16));
                size.rightMargin = dp(context, 5);
                chips.addView(chip, size);
            }
            labels.addView(chips);
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            MaterialRadioButton button = new MaterialRadioButton(context);
            buttons[index] = button;
            button.setContentDescription(themes.getDisplayItem(index));
            row.addView(button);
            View.OnClickListener choose = view -> {
                selection[0] = choice;
                for (int item = 0; item < buttons.length; item++) {
                    buttons[item].setChecked(item == choice);
                    cards[item].setStrokeWidth(dp(context, item == choice ? 2 : 1));
                    cards[item].setStrokeColor(context.getColor(item == choice ? R.color.accent_blue : R.color.outline_soft));
                }
            };
            card.setOnClickListener(choose);
            button.setOnClickListener(choose);
            card.setStrokeWidth(dp(context, index == selection[0] ? 2 : 1));
            card.setStrokeColor(context.getColor(index == selection[0] ? R.color.accent_blue : R.color.outline_soft));
            button.setChecked(index == selection[0]);
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(context).setTitle(R.string.theme_picker_title).setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.theme_picker_apply, (ignored, which) -> apply.accept(selection[0])).create();
        dialog.show();
        return dialog;
    }
    private static int dp(Context context, int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
