package com.akansh.fileserversuit;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.akansh.t_history.HistoryDBManager;
import com.akansh.t_history.HistoryItem;
import com.akansh.t_history.TransferHistoryAdapter;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.ArrayList;

public class TransferHistory extends AppCompatActivity {

    RecyclerView history_list;

    TransferHistoryAdapter transferHistoryAdapter;
    HistoryDBManager historyDBManager;
    Utils utils;
    private int datasetLenth=0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer_history);
//        Toolbar toolbar=findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);
//        getSupportActionBar().setTitle("Transfer History");
        historyDBManager=new HistoryDBManager(this);
        history_list = findViewById(R.id.history_list);
        ArrayList<HistoryItem> historyItems=historyDBManager.getHistory();
        datasetLenth = historyItems.size();
        if(datasetLenth>0) {
            showSnackbar("Swipe right to delete item...");
        }
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
                showUndoSnackbar(historyItem,pos);
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
                    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
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
                        }
                    };
                    try {
                        AlertDialog dialog = new AlertDialog.Builder(TransferHistory.this).setMessage("This will delete this file from storage too, and this action can't be undone!\n\nAre you sure to delete this file?").setPositiveButton("Yes", dialogClickListener).setNegativeButton("No", dialogClickListener).show();
                        TextView textView = dialog.findViewById(android.R.id.message);
                        TextView textView2 = dialog.findViewById(android.R.id.button1);
                        TextView textView3 = dialog.findViewById(android.R.id.button2);
                        Typeface face = Typeface.createFromAsset(getAssets(), "fonts/product_sans.ttf");
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
            if(transferHistoryAdapter.getItemCount()==0) {
                showSnackbar("No history found!");
            }else{
                historyDBManager.clearHistory();
                checkEmptyList();
                showSnackbar("Transfer history cleared!");
            }
        });

        history_list.setAdapter(transferHistoryAdapter);
        init();
    }

    @Override
    protected void onResume() {
        if(historyDBManager.getItemsCount()!=datasetLenth) {
            transferHistoryAdapter.updateDataset(historyDBManager.getHistory());
        }
        checkEmptyList();
        super.onResume();
    }

    private void init() {
        checkEmptyList();
    }

    public void checkEmptyList() {
        ConstraintLayout constraintLayout=findViewById(R.id.blank_screen);
        if(historyDBManager.getItemsCount()==0) {
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
        snackbar.setBackgroundTint(Color.parseColor("#000a12"));
//        TextView tv = (snackbar.getView()).findViewById(com.google.android.material.R.id.snackbar_text);
//        Typeface font = Typeface.createFromAsset(this.getAssets(), "fonts/product_sans.ttf");
//        tv.setTypeface(font);
        snackbar.show();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void showUndoSnackbar(final HistoryItem historyItem, final int position) {
        ConstraintLayout constraintLayout=findViewById(R.id.transfer_root);
        Snackbar snackbar = Snackbar.make(constraintLayout, "Item removed from history!", Snackbar.LENGTH_LONG);
        snackbar.setAction("Undo", v -> {
            historyDBManager.restoreHistory(historyItem);
            transferHistoryAdapter.updateDataset(historyDBManager.getHistory());
            history_list.scrollToPosition(position);
            transferHistoryAdapter.notifyDataSetChanged();
        });
        snackbar.setBackgroundTint(Color.parseColor("#000a12"));
//        TextView tv = (snackbar.getView()).findViewById(com.google.android.material.R.id.snackbar_text);
//        TextView action = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_action);
//        Typeface font = Typeface.createFromAsset(this.getAssets(), "fonts/product_sans.ttf");
//        tv.setTypeface(font);
//        action.setTypeface(font,Typeface.BOLD);
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
        i.setDataAndType(uri, type);
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