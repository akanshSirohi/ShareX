package com.akansh.sharex.ui;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.akansh.sharex.R;

public class SettingsFragment extends Fragment {
    public SettingsFragment() { super(R.layout.settings_view); }

    @Override public void onViewCreated(@NonNull View view, Bundle state) {
        super.onViewCreated(view, state);
        ((MainActivity) requireActivity()).onSharingPageCreated(MainPagerAdapter.SETTINGS, view);
    }
}
