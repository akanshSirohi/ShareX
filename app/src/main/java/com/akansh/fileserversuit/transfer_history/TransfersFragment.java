package com.akansh.fileserversuit.transfer_history;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.akansh.fileserversuit.BuildConfig;
import com.akansh.fileserversuit.R;
import com.akansh.fileserversuit.common.Constants;
import com.akansh.fileserversuit.common.Utils;
import com.akansh.fileserversuit.server.TransferStats;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.snackbar.Snackbar;
import java.io.File;
import java.util.ArrayList;

/** Transfer records share the main pager's navigation and system bar insets. */
public class TransfersFragment extends Fragment {
    private View pageView;
    private RecyclerView historyList;
    private TransferHistoryAdapter adapter;
    private HistoryDBManager history;
    private Utils utils;
    private int directionFilter = -1;
    private String typeFilter = "all";
    private int recordCount;
    private Snackbar snackbar;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTicker = new Runnable() {
        @Override public void run() {
            if (pageView == null) return;
            if (history.getItemsCount() != recordCount) refreshHistory();
            else updateTotals();
            handler.postDelayed(this, 1000);
        }
    };

    public TransfersFragment() { super(R.layout.fragment_transfers); }

    @Override public void onViewCreated(@NonNull View view, Bundle state) {
        super.onViewCreated(view, state);
        pageView = view;
        history = new HistoryDBManager(requireContext());
        utils = new Utils(requireContext());
        historyList = view.findViewById(R.id.history_list);
        adapter = new TransferHistoryAdapter(requireContext(), new ArrayList<>());
        historyList.setLayoutManager(new LinearLayoutManager(requireContext()));
        historyList.setAdapter(adapter);
        adapter.setHistoryActionListener(new TransferHistoryAdapter.HistoryActionListener() {
            @Override public void onClickItem(View clicked, int position) { openFile(adapter.getItem(position)); }
            @Override public void onShareItem(View clicked, int position) { shareFile(adapter.getItem(position)); }
            @Override public void onDeleteItem(View clicked, int position) { showRemovalOptions(adapter.getItem(position)); }
        });
        view.findViewById(R.id.btn_clear_hist).setOnClickListener(clicked -> {
            if (history.getItemsCount() == 0) { showSnackbar("No history found!"); return; }
            new AlertDialog.Builder(requireContext())
                    .setTitle("Clear transfer history?")
                    .setMessage("This removes transfer records. Files on your device stay in place.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Clear", (dialog, which) -> {
                        history.clearHistory();
                        refreshHistory();
                        showSnackbar("Transfer history cleared");
                    }).show();
        });
        MaterialButtonToggleGroup direction = view.findViewById(R.id.history_direction);
        ChipGroup types = view.findViewById(R.id.history_types);
        direction.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) return;
            directionFilter = id == R.id.history_sent ? Constants.ITEM_TYPE_SENT
                    : id == R.id.history_received ? Constants.ITEM_TYPE_RECEIVED : -1;
            refreshHistory();
        });
        types.setOnCheckedStateChangeListener((group, ids) -> {
            int id = ids.isEmpty() ? R.id.type_all : ids.get(0);
            typeFilter = id == R.id.type_image ? "image" : id == R.id.type_video ? "video"
                    : id == R.id.type_audio ? "audio" : id == R.id.type_document ? "document"
                    : id == R.id.type_other ? "other" : "all";
            refreshHistory();
        });
        if (state != null) {
            direction.check(state.getInt("direction_id", R.id.history_all));
            types.check(state.getInt("type_id", R.id.type_all));
        }
        refreshHistory();
    }

    @Override public void onResume() {
        super.onResume();
        refreshHistory();
        handler.removeCallbacks(refreshTicker);
        handler.post(refreshTicker);
    }

    @Override public void onPause() {
        handler.removeCallbacks(refreshTicker);
        if (snackbar != null) snackbar.dismiss();
        super.onPause();
    }

    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        if (pageView == null) return;
        state.putInt("direction_id", ((MaterialButtonToggleGroup) pageView.findViewById(R.id.history_direction)).getCheckedButtonId());
        state.putInt("type_id", ((ChipGroup) pageView.findViewById(R.id.history_types)).getCheckedChipId());
    }

    @Override public void onDestroyView() {
        handler.removeCallbacks(refreshTicker);
        if (snackbar != null) snackbar.dismiss();
        historyList.setAdapter(null);
        history.close();
        snackbar = null;
        adapter = null;
        historyList = null;
        pageView = null;
        super.onDestroyView();
    }

    private void refreshHistory() {
        if (pageView == null) return;
        ArrayList<HistoryItem> all = history.getHistory();
        recordCount = all.size();
        ArrayList<HistoryItem> filtered = new ArrayList<>();
        for (HistoryItem item : all) {
            if ((directionFilter == -1 || item.getItem_type() == directionFilter)
                    && (typeFilter.equals("all") || typeFilter.equals(TransferHistoryAdapter.category(item)))) filtered.add(item);
        }
        adapter.updateDataset(filtered);
        ((TextView) pageView.findViewById(R.id.history_record_count)).setText(filtered.size() + " of " + all.size() + " transfers");
        ((TextView) pageView.findViewById(R.id.history_empty_title)).setText(all.isEmpty() ? "No transfers yet" : "No matching transfers");
        pageView.findViewById(R.id.blank_screen).setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        historyList.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
        updateTotals();
    }

    private void updateTotals() {
        long[] totals = TransferStats.lifetimeTotals();
        ((TextView) pageView.findViewById(R.id.history_lifetime_totals)).setText("Sent "
                + android.text.format.Formatter.formatShortFileSize(requireContext(), totals[0]) + " · Received "
                + android.text.format.Formatter.formatShortFileSize(requireContext(), totals[1]));
    }

    private void showRemovalOptions(HistoryItem item) {
        File file = new File(item.getPath());
        AlertDialog.Builder options = new AlertDialog.Builder(requireContext())
                .setTitle("Remove transfer?")
                .setMessage("Remove this transfer record from history. The file stays on your device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove record", (dialog, which) -> {
                    history.deleteHistory(item.getPath());
                    refreshHistory();
                    Snackbar undo = makeSnackbar("Transfer record removed");
                    undo.setAction("Undo", clicked -> {
                        history.restoreHistory(item);
                        refreshHistory();
                    });
                    undo.show();
                });
        if (file.isFile()) options.setNeutralButton("Delete file", (dialog, which) ->
                new AlertDialog.Builder(requireContext())
                        .setTitle("Delete file?")
                        .setMessage("This permanently deletes the file from your device and removes its transfer record.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (confirmation, button) -> {
                            if (!file.delete()) { showSnackbar("Couldn't delete this file"); return; }
                            history.deleteHistory(item.getPath());
                            refreshHistory();
                        }).show());
        options.show();
    }

    private Snackbar makeSnackbar(String message) {
        if (snackbar != null) snackbar.dismiss();
        snackbar = Snackbar.make(requireActivity().findViewById(R.id.root_container), message, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(requireContext().getColor(R.color.surface_container));
        snackbar.setTextColor(requireContext().getColor(R.color.txt_color));
        NavigationBarView navigation = requireActivity().findViewById(R.id.bottom_nav);
        if (!(navigation instanceof com.google.android.material.navigationrail.NavigationRailView)) snackbar.setAnchorView(navigation);
        return snackbar;
    }

    private void showSnackbar(String message) { makeSnackbar(message).show(); }

    private void openFile(HistoryItem item) {
        File file = new File(item.getPath());
        if (!file.isFile()) { showSnackbar("File does not exist or cannot be opened"); return; }
        try {
            Uri uri = FileProvider.getUriForFile(requireContext(), BuildConfig.APPLICATION_ID + ".provider", file);
            startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, utils.getMimeType(file))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (RuntimeException error) { showSnackbar("Can't open this file!"); }
    }

    private void shareFile(HistoryItem item) {
        File file = new File(item.getPath());
        if (!file.isFile()) { showSnackbar("File does not exist or cannot be shared"); return; }
        try {
            Uri uri = FileProvider.getUriForFile(requireContext(), BuildConfig.APPLICATION_ID + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_SEND).setType(utils.getMimeType(file))
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(android.content.ClipData.newRawUri("Shared file", uri));
            startActivity(Intent.createChooser(intent, "Sharing file..."));
        } catch (RuntimeException error) { showSnackbar("Can't share this file!"); }
    }
}
