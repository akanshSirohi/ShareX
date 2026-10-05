package com.akansh.fileserversuit.ui;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.akansh.fileserversuit.R;
import com.akansh.fileserversuit.common.Constants;
import com.akansh.fileserversuit.common.Utils;
import android.widget.TextView;

public class HomeFragment extends Fragment {
    public HomeFragment() { super(R.layout.main_view); }

    @Override public void onViewCreated(@NonNull View view, Bundle state) {
        super.onViewCreated(view, state);
        boolean privateMode = new Utils(requireContext()).loadSetting(Constants.PRIVATE_MODE);
        view.findViewById(R.id.file_selection_panel).setVisibility(privateMode ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.choose_folder_btn).setVisibility(privateMode ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.manage_files_btn).setVisibility(View.GONE);
        ((TextView) view.findViewById(R.id.mode_description)).setText(privateMode
                ? R.string.private_mode_explanation : R.string.folder_mode_explanation);
        ((MainActivity) requireActivity()).onSharingPageCreated(MainPagerAdapter.HOME, view);
    }
}
