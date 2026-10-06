package com.akansh.sharex.transfer_history;

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

import com.akansh.sharex.common.Constants;
import com.akansh.sharex.R;





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
        public TextView textView_filename,textView_path,textView_type,textView_dt_stamp, direction;
        public ImageView imageView_icon, directionIcon;
        public ImageButton imageButton_del,imageButton_share;
        public ConstraintLayout click_panel;

        public HistHolder(View view) {
            super(view);
            textView_filename = view.findViewById(R.id.hitem_filename);
            textView_path = view.findViewById(R.id.hitem_path);
            textView_type = view.findViewById(R.id.hitem_type);
            direction = view.findViewById(R.id.hitem_direction);
            directionIcon = view.findViewById(R.id.hitem_direction_icon);
            textView_dt_stamp = view.findViewById(R.id.hitem_dt_stamp);
            imageView_icon = view.findViewById(R.id.hitem_icon);
            imageButton_del = view.findViewById(R.id.hitem_del_btn);
            imageButton_share = view.findViewById(R.id.hitem_share_btn);
            click_panel = view.findViewById(R.id.click_panel);
            click_panel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(listener!=null && getAdapterPosition()!=RecyclerView.NO_POSITION) {
                        listener.onClickItem(v,getAdapterPosition());
                    }
                }
            });
            imageButton_del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(listener!=null && getAdapterPosition()!=RecyclerView.NO_POSITION) listener.onDeleteItem(v,getAdapterPosition());
                }
            });
            imageButton_share.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(listener!=null && getAdapterPosition()!=RecyclerView.NO_POSITION) listener.onShareItem(v,getAdapterPosition());
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
        String category = category(historyItem);
        int glyph;
        String label;
        switch (category) {
            case "image": glyph = R.drawable.ic_archive_image; label = "Image"; break;
            case "video": glyph = R.drawable.ic_archive_video; label = "Video"; break;
            case "audio": glyph = R.drawable.ic_archive_audio; label = "Audio"; break;
            case "document": glyph = R.drawable.ic_archive_document; label = "Document"; break;
            default: glyph = R.drawable.ic_archive_file; label = "File";
        }
        boolean receiving = historyItem.getItem_type() == Constants.ITEM_TYPE_RECEIVED;
        int color = ctx.getColor(receiving ? R.color.receive_accent : R.color.accent_blue);
        holder.imageView_icon.setImageResource(glyph);
        holder.imageView_icon.setColorFilter(color);
        holder.directionIcon.setImageResource(receiving ? R.drawable.ic_archive_received : R.drawable.ic_archive_sent);
        holder.directionIcon.setColorFilter(color);
        holder.directionIcon.setContentDescription(receiving ? "Received" : "Sent");
        holder.direction.setText(receiving ? "RECEIVED" : "SENT");
        holder.direction.setTextColor(color);
        holder.textView_type.setText(label + " · " + historyItem.getSize());
        holder.textView_dt_stamp.setText(historyItem.getDate() + "\n" + historyItem.getTime());
        holder.imageButton_share.setContentDescription("Share " + historyItem.getFile_name());
        holder.imageButton_del.setContentDescription("Remove transfer record or delete " + historyItem.getFile_name());
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

    public static String category(HistoryItem item) {
        String mime = item.getType() == null ? "" : item.getType().toLowerCase(java.util.Locale.ROOT);
        String name = item.getFile_name() == null ? "" : item.getFile_name().toLowerCase(java.util.Locale.ROOT);
        if (mime.startsWith("image/") || name.matches(".*\\.(png|jpe?g|gif|webp|heic|avif|bmp|svg)$")) return "image";
        if (mime.startsWith("video/") || name.matches(".*\\.(mp4|mkv|webm|mov|avi|3gp)$")) return "video";
        if (mime.startsWith("audio/") || name.matches(".*\\.(mp3|wav|flac|ogg|m4a|aac|opus)$")) return "audio";
        if (mime.startsWith("text/") || mime.contains("pdf") || mime.contains("document") || mime.contains("sheet")
                || mime.contains("presentation") || name.matches(".*\\.(pdf|txt|docx?|xlsx?|pptx?|csv|rtf|md|odt|ods|odp)$")) return "document";
        return "other";
    }

    public void setHistoryActionListener(HistoryActionListener listener) {
        this.listener = listener;
    }

    public void updateDataset(ArrayList<HistoryItem> hItems) {
        historyItems.clear();
        historyItems.addAll(hItems);
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
