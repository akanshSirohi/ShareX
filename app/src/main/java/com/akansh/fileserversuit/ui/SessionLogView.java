package com.akansh.fileserversuit.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.akansh.fileserversuit.R;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/** Bounded native timeline for session events. No technical data is inferred or simulated. */
public final class SessionLogView extends RecyclerView {
    private final ArrayList<Event> events = new ArrayList<>();
    private final EventAdapter eventAdapter = new EventAdapter();
    private final SimpleDateFormat timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private final Typeface typeface;

    public SessionLogView(Context context, AttributeSet attrs) {
        super(context, attrs);
        typeface = ResourcesCompat.getFont(context, R.font.google_sans);
        setLayoutManager(new LinearLayoutManager(context));
        setAdapter(eventAdapter);
        setNestedScrollingEnabled(true);
        setClipToPadding(false);
        setPadding(dp(16), dp(12), dp(16), dp(12));
    }

    public void addEvent(String message) {
        if (message == null || message.trim().isEmpty()) return;
        String lower = message.toLowerCase(Locale.ROOT);
        String kind = lower.contains("received") || lower.contains("upload") ? "RECEIVE"
                : lower.contains("sending") || lower.contains("download") ? "SEND"
                : lower.contains("device") || lower.contains("connect") ? "CONNECT" : "SYSTEM";
        if (events.size() == 150) { events.remove(0); eventAdapter.notifyItemRemoved(0); }
        events.add(new Event(timestamp.format(new Date()), kind, message));
        eventAdapter.notifyItemInserted(events.size() - 1);
        scrollToPosition(events.size() - 1);
        setContentDescription(events.size() + " session events. Latest: " + message);
    }

    public void clearEvents() {
        int count = events.size(); events.clear(); eventAdapter.notifyItemRangeRemoved(0, count);
        addEvent("ShareX is ready.");
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private final class EventAdapter extends Adapter<EventHolder> {
        @NonNull @Override public EventHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(10), 0, dp(10));
            row.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            View marker = new View(getContext());
            GradientDrawable dot = new GradientDrawable(); dot.setShape(GradientDrawable.OVAL); dot.setColor(getContext().getColor(R.color.accent_blue));
            marker.setBackground(dot);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(6), dp(6));
            dotParams.topMargin = dp(6); dotParams.rightMargin = dp(12); row.addView(marker, dotParams);
            LinearLayout column = new LinearLayout(getContext()); column.setOrientation(LinearLayout.VERTICAL);
            LinearLayout meta = new LinearLayout(getContext()); meta.setGravity(Gravity.CENTER_VERTICAL);
            TextView time = new TextView(getContext()); time.setTypeface(typeface); time.setTextSize(11); time.setTextColor(getContext().getColor(R.color.txt_color_secondary));
            meta.addView(time, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView kind = new TextView(getContext()); kind.setTypeface(typeface, Typeface.BOLD); kind.setTextSize(10); kind.setTextColor(getContext().getColor(R.color.accent_blue));
            kind.setLetterSpacing(0.08f); meta.addView(kind);
            column.addView(meta);
            TextView message = new TextView(getContext()); message.setTypeface(typeface); message.setTextSize(14); message.setTextColor(getContext().getColor(R.color.txt_color));
            LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            messageParams.topMargin = dp(4); column.addView(message, messageParams);
            row.addView(column, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            return new EventHolder(row, time, kind, message, dot);
        }

        @Override public void onBindViewHolder(@NonNull EventHolder holder, int position) {
            Event event = events.get(position);
            holder.time.setText(event.time); holder.kind.setText(event.kind); holder.message.setText(event.message);
            int color = event.kind.equals("RECEIVE") ? getContext().getColor(R.color.receive_accent) : getContext().getColor(R.color.accent_blue);
            holder.kind.setTextColor(color); holder.marker.setColor(color);
        }
        @Override public int getItemCount() { return events.size(); }
    }

    private static final class EventHolder extends ViewHolder {
        final TextView time, kind, message;
        final GradientDrawable marker;
        EventHolder(View row, TextView time, TextView kind, TextView message, GradientDrawable marker) {
            super(row); this.time = time; this.kind = kind; this.message = message; this.marker = marker;
        }
    }
    private static final class Event {
        final String time, kind, message;
        Event(String time, String kind, String message) { this.time = time; this.kind = kind; this.message = message; }
    }
}
