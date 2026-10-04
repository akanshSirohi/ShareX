package com.akansh.fileserversuit.transfer_history;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.akansh.fileserversuit.BuildConfig;
import com.akansh.fileserversuit.R;
import com.akansh.fileserversuit.common.Constants;
import com.akansh.fileserversuit.common.EdgeToEdge;
import com.akansh.fileserversuit.common.SwipeDeleteCallback;
import com.akansh.fileserversuit.common.Utils;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.navigation.NavigationBarView;

import java.io.File;
import java.util.ArrayList;

public class TransferHistoryActivity extends AppCompatActivity {

    RecyclerView history_list;

    TransferHistoryAdapter transferHistoryAdapter;
    HistoryDBManager historyDBManager;
    Utils utils;
    private int datasetLenth=0;
    private int directionFilter = -1;
    private String typeFilter = "all";
    private final android.os.Handler totalsHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable totalsTicker = new Runnable() {
        @Override public void run() { updateTotals(); totalsHandler.postDelayed(this, 1000); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer_history);
        EdgeToEdge.apply(this, findViewById(R.id.transfer_root));
        historyDBManager=new HistoryDBManager(this);
        history_list = findViewById(R.id.history_list);
        ArrayList<HistoryItem> historyItems=historyDBManager.getHistory();
        datasetLenth = historyItems.size();
        transferHistoryAdapter=new TransferHistoryAdapter(this,historyItems);
        history_list.setLayoutManager(new LinearLayoutManager(this));
        utils=new Utils(this);
        SwipeDeleteCallback swipeDeleteCallback=new SwipeDeleteCallback(this) {
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int pos=viewHolder.getAdapterPosition();
                HistoryItem historyItem=transferHistoryAdapter.getItem(pos);
                String path=historyItem.getPath();
                transferHistoryAdapter.removeItem(pos);
                historyDBManager.deleteHistory(path);
                showUndoSnackbar(historyItem);
                checkEmptyList();
            }
        };

        ItemTouchHelper itemTouchhelper = new ItemTouchHelper(swipeDeleteCallback);
        itemTouchhelper.attachToRecyclerView(history_list);

        transferHistoryAdapter.setHistoryActionListener(new TransferHistoryAdapter.HistoryActionListener() {
            @Override
            public void onClickItem(View v, int position) {
                try {
                    HistoryItem historyItem = transferHistoryAdapter.getItem(position);
                    File f = new File(historyItem.getPath());
                    if (f.isFile()) {
                        if(f.exists()) {
                            openFile(f.getAbsolutePath(), utils.getMimeType(f));
                        }else{
                            showSnackbar("File does not exist!");
                        }
                    }else{
                        showSnackbar("Folders or apps cannot be opened!");
                    }
                }catch (Exception e) {
                    showSnackbar("Can't open this file!");
                }
            }

            @Override
            public void onDeleteItem(View v, int position) {
                final File f = new File(transferHistoryAdapter.getItem(position).getPath());
                if(f.isFile() && f.exists()) {
                    DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
                        switch (which) {
                            case DialogInterface.BUTTON_POSITIVE:
                                String path = transferHistoryAdapter.getItem(position).getPath();
                                transferHistoryAdapter.removeItem(position);
                                historyDBManager.deleteHistory(path);
                                f.delete();
                                checkEmptyList();
                                break;

                            case DialogInterface.BUTTON_NEGATIVE:
                                //No button clicked
                                break;
                        }
                    };
                    try {
                        AlertDialog dialog = new AlertDialog.Builder(TransferHistoryActivity.this).setMessage("This will delete this file from storage too, and this action can't be undone!\n\nAre you sure to delete this file?").setPositiveButton("Yes", dialogClickListener).setNegativeButton("No", dialogClickListener).show();
                        TextView textView = dialog.findViewById(android.R.id.message);
                        TextView textView2 = dialog.findViewById(android.R.id.button1);
                        TextView textView3 = dialog.findViewById(android.R.id.button2);
                        Typeface face = Typeface.createFromAsset(getAssets(), "fonts/google_sans.ttf");
                        textView.setTypeface(face);
                        textView2.setTypeface(face,Typeface.BOLD);
                        textView3.setTypeface(face,Typeface.BOLD);
                    }catch (Exception e) {
                        Log.d(Constants.LOG_TAG, "Dialog Error: " + e);
                    }
                }else{
                    String path = transferHistoryAdapter.getItem(position).getPath();
                    transferHistoryAdapter.removeItem(position);
                    historyDBManager.deleteHistory(path);
                    checkEmptyList();
                }
            }

            @Override
            public void onShareItem(View v, int position) {
                try {
                    Uri uri;
                    File file = new File(transferHistoryAdapter.getItem(position).getPath());
                    if(file.isFile()) {
                        if(file.exists()) {
                            shareFile(file.getAbsolutePath(),utils.getMimeType(file));
                        }else{
                            showSnackbar("File does not exist!");
                        }
                    }else{
                        showSnackbar("Folders or apps cannot be shared!");
                    }
                }catch (Exception e) {
                    Log.d(Constants.LOG_TAG,e.toString());
                    showSnackbar("Can't share this file!");
                }
            }
        });

        ImageButton btn_clear_hist = findViewById(R.id.btn_clear_hist);
        btn_clear_hist.setOnClickListener(v -> {
            if(historyDBManager.getItemsCount()==0) {
                showSnackbar("No history found!");
            }else{
                new AlertDialog.Builder(this)
                        .setTitle("Clear transfer history?")
                        .setMessage("This removes transfer records. Files on your device stay in place.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Clear", (dialog, which) -> {
                            historyDBManager.clearHistory();
                            transferHistoryAdapter.updateDataset(historyDBManager.getHistory());
                            checkEmptyList();
                            showSnackbar("Transfer history cleared");
                        })
                        .show();
            }
        });

        history_list.setAdapter(transferHistoryAdapter);
        com.google.android.material.button.MaterialButtonToggleGroup direction = findViewById(R.id.history_direction);
        com.google.android.material.chip.ChipGroup types = findViewById(R.id.history_types);
        direction.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) return;
            directionFilter = id == R.id.history_sent ? Constants.ITEM_TYPE_SENT
                    : id == R.id.history_received ? Constants.ITEM_TYPE_RECEIVED : -1;
            checkEmptyList();
        });
        types.setOnCheckedStateChangeListener((group, ids) -> {
            int id = ids.isEmpty() ? R.id.type_all : ids.get(0);
            typeFilter = id == R.id.type_image ? "image" : id == R.id.type_video ? "video"
                    : id == R.id.type_audio ? "audio" : id == R.id.type_document ? "document"
                    : id == R.id.type_other ? "other" : "all";
            checkEmptyList();
        });
        if (savedInstanceState != null) {
            direction.check(savedInstanceState.getInt("direction_id", R.id.history_all));
            types.check(savedInstanceState.getInt("type_id", R.id.type_all));
        }
        NavigationBarView bottomNavigation = findViewById(R.id.bottom_nav);
        bottomNavigation.setSelectedItemId(R.id.trans_hist);
        bottomNavigation.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.trans_hist) return true;
            Intent intent = new Intent(TransferHistoryActivity.this, com.akansh.fileserversuit.ui.MainActivity.class);
            if (item.getItemId() == R.id.settings) intent.putExtra("open_settings", true);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
            return true;
        });
        init();
    }

    @Override
    protected void onResume() {
        checkEmptyList();
        totalsHandler.post(totalsTicker);
        super.onResume();
    }

    @Override protected void onPause() {
        totalsHandler.removeCallbacks(totalsTicker);
        super.onPause();
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("direction_id", ((com.google.android.material.button.MaterialButtonToggleGroup) findViewById(R.id.history_direction)).getCheckedButtonId());
        state.putInt("type_id", ((com.google.android.material.chip.ChipGroup) findViewById(R.id.history_types)).getCheckedChipId());
    }

    private void updateTotals() {
        long[] totals = com.akansh.fileserversuit.server.TransferStats.lifetimeTotals();
        ((TextView) findViewById(R.id.history_lifetime_totals)).setText("Sent "
                + android.text.format.Formatter.formatShortFileSize(this, totals[0]) + " · Received "
                + android.text.format.Formatter.formatShortFileSize(this, totals[1]));
    }

    private void init() {
        checkEmptyList();
    }

    public void checkEmptyList() {
        ArrayList<HistoryItem> all = historyDBManager.getHistory();
        ArrayList<HistoryItem> filtered = new ArrayList<>();
        for (HistoryItem item : all) {
            if ((directionFilter == -1 || item.getItem_type() == directionFilter)
                    && (typeFilter.equals("all") || typeFilter.equals(TransferHistoryAdapter.category(item)))) filtered.add(item);
        }
        transferHistoryAdapter.updateDataset(filtered);
        ((TextView) findViewById(R.id.history_record_count)).setText(filtered.size() + " of " + all.size()
                + " transfers · Swipe right to remove a record");
        ((TextView) findViewById(R.id.history_empty_title)).setText(all.isEmpty() ? "No transfers yet" : "No matching transfers");
        updateTotals();
        ConstraintLayout constraintLayout=findViewById(R.id.blank_screen);
        if(filtered.isEmpty()) {
            constraintLayout.setVisibility(View.VISIBLE);
            history_list.setVisibility(View.GONE);
        }else{
            constraintLayout.setVisibility(View.GONE);
            history_list.setVisibility(View.VISIBLE);
        }
    }

    public void showSnackbar(String msg) {
        ConstraintLayout constraintLayout=findViewById(R.id.transfer_root);
        Snackbar snackbar = Snackbar.make(constraintLayout, msg, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(getColor(R.color.surface_container));
        snackbar.setTextColor(getColor(R.color.txt_color));
        snackbar.show();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void showUndoSnackbar(final HistoryItem historyItem) {
        ConstraintLayout constraintLayout=findViewById(R.id.transfer_root);
        Snackbar snackbar = Snackbar.make(constraintLayout, "Item removed from history!", Snackbar.LENGTH_LONG);
        snackbar.setAction("Undo", v -> {
            historyDBManager.restoreHistory(historyItem);
            transferHistoryAdapter.updateDataset(historyDBManager.getHistory());
            checkEmptyList();
        });
        snackbar.setBackgroundTint(getColor(R.color.surface_container));
        snackbar.setTextColor(getColor(R.color.txt_color));
        snackbar.show();
    }

    public void shareFile(String path,String type) {
        File file=new File(path);
        Uri uri;
        Intent i = new Intent(Intent.ACTION_SEND);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", file);
            i.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            uri = Uri.fromFile(file);
        }
        i.setType(type);
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.setClipData(android.content.ClipData.newRawUri("Shared file", uri));
        startActivity(Intent.createChooser(i,"Sharing file..."));
    }

    public void openFile(String path,String type) {
        File file=new File(path);
        Uri uri;
        Intent i = new Intent(Intent.ACTION_VIEW);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", file);
            i.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            uri = Uri.fromFile(file);
        }
        i.setDataAndType(uri, type);
        startActivity(i);
    }
}
