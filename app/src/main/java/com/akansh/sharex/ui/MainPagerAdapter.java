package com.akansh.sharex.ui;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import com.akansh.sharex.R;
import com.akansh.sharex.transfer_history.TransfersFragment;

public final class MainPagerAdapter extends FragmentStateAdapter {
    public static final int HOME = 0;
    public static final int TRANSFERS = 1;
    public static final int SETTINGS = 2;

    public MainPagerAdapter(FragmentActivity activity) { super(activity); }

    @NonNull @Override public Fragment createFragment(int position) {
        switch (position) {
            case HOME: return new HomeFragment();
            case TRANSFERS: return new TransfersFragment();
            case SETTINGS: return new SettingsFragment();
            default: throw new IllegalArgumentException("Unknown page: " + position);
        }
    }

    @Override public int getItemCount() { return 3; }

    public static int positionForDestination(int destination) {
        if (destination == R.id.trans_hist) return TRANSFERS;
        if (destination == R.id.settings) return SETTINGS;
        return HOME;
    }

    public static int destinationForPosition(int position) {
        if (position == TRANSFERS) return R.id.trans_hist;
        if (position == SETTINGS) return R.id.settings;
        return R.id.home;
    }
}
