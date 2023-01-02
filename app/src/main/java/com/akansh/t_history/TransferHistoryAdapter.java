package com.akansh.t_history;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.akansh.fileserversuit.Constants;
import com.akansh.fileserversuit.R;





import java.util.ArrayList;

public class TransferHistoryAdapter extends RecyclerView.Adapter<TransferHistoryAdapter.HistHolder> {

    HistoryActionListener listener;
    Context ctx;
    LayoutInflater inflater;
    ArrayList<HistoryItem> historyItems;

    public interface HistoryActionListener {
        void onClickItem(View v, int position);
        void onDeleteItem(View v,int position);
        void onShareItem(View v,int position);
    }

    public TransferHistoryAdapter(Context ctx, ArrayList<HistoryItem> hItems) {
        inflater= LayoutInflater.from(ctx);
        this.ctx = ctx;
        this.historyItems = hItems;
    }

    public class HistHolder extends RecyclerView.ViewHolder {
        public TextView textView_filename,textView_path,textView_type,textView_size,textView_dt_stamp;
        public ImageView imageView_icon;
        public ImageButton imageButton_del,imageButton_share;
        public ConstraintLayout click_panel;

        public HistHolder(View view) {
            super(view);
            textView_filename = view.findViewById(R.id.hitem_filename);
            textView_path = view.findViewById(R.id.hitem_path);
            textView_type = view.findViewById(R.id.hitem_type);
            textView_size = view.findViewById(R.id.hitem_size);
            textView_dt_stamp = view.findViewById(R.id.hitem_dt_stamp);
            imageView_icon = view.findViewById(R.id.hitem_icon);
            imageButton_del = view.findViewById(R.id.hitem_del_btn);
            imageButton_share = view.findViewById(R.id.hitem_share_btn);
            click_panel = view.findViewById(R.id.click_panel);
            click_panel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(listener!=null) {
                        listener.onClickItem(v,getAdapterPosition());
                    }
                }
            });
            imageButton_del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onDeleteItem(v,getAdapterPosition());
                }
            });
            imageButton_share.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onShareItem(v,getAdapterPosition());
                }
            });
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull HistHolder holder, int position) {
        HistoryItem historyItem=this.historyItems.get(position);
        holder.textView_filename.setText(historyItem.getFile_name());
        holder.textView_path.setText(historyItem.getPath());
        holder.textView_size.setText("Size: "+historyItem.getSize());
        holder.textView_type.setText("Type: "+historyItem.getType());
        holder.textView_dt_stamp.setText(historyItem.getDate()+" | "+historyItem.getTime());
        if(historyItem.getItem_type()== Constants.ITEM_TYPE_RECEIVED) {
            holder.imageView_icon.setImageResource(R.drawable.ic_receive);
        }else{
            holder.imageView_icon.setImageResource(R.drawable.ic_send);
        }
    }

    @NonNull
    @Override
    public HistHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v=inflater.inflate(R.layout.trans_hist_item,parent,false);
        return new HistHolder(v);
    }

    @Override
    public int getItemCount() {
        return historyItems.size();
    }

    public void setHistoryActionListener(HistoryActionListener listener) {
        this.listener = listener;
    }

    public void updateDataset(ArrayList<HistoryItem> hItems) {
        historyItems = hItems;
        notifyDataSetChanged();
    }

    public void removeItem(int position) {
        historyItems.remove(position);
        notifyDataSetChanged();
    }

    public HistoryItem getItem(int position) {
        return historyItems.get(position);
    }

    public void addItem(HistoryItem hitem) {
        historyItems.add(historyItems.size(), hitem);
        notifyDataSetChanged();
    }

    public void removeAllItems() {
        historyItems.clear();
        notifyDataSetChanged();
    }
}
