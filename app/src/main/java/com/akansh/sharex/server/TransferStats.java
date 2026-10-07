package com.akansh.sharex.server;

import android.os.SystemClock;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.LinkedHashMap;
import java.util.Map;

/** Counts file payload bytes observed by the HTTP pipeline, excluding pages and headers. */
public final class TransferStats {
    private static final Map<Long, Transfer> active = new LinkedHashMap<>();
    private static long nextId, sent, received, completedAt;
    private static long lifetimeSent, lifetimeReceived, lastPersisted;
    private static SharedPreferences totals;

    private TransferStats() { }

    public static synchronized void initialize(Context context) {
        if (totals != null) return;
        totals = context.getApplicationContext().getSharedPreferences("transfer_totals", Context.MODE_PRIVATE);
        lifetimeSent = totals.getLong("sent_bytes", 0);
        lifetimeReceived = totals.getLong("received_bytes", 0);
    }

    private static void persist() {
        if (totals == null) return;
        totals.edit().putLong("sent_bytes", lifetimeSent).putLong("received_bytes", lifetimeReceived).apply();
        lastPersisted = SystemClock.elapsedRealtime();
    }

    public static synchronized long[] lifetimeTotals() {
        return new long[]{lifetimeSent, lifetimeReceived};
    }

    public static synchronized void flush() { persist(); }

    public static synchronized long begin(boolean receiving, long total, String name) {
        long id = ++nextId;
        active.put(id, new Transfer(receiving, total, name));
        return id;
    }

    public static synchronized void progress(long id, long bytes) {
        Transfer transfer = active.get(id);
        if (transfer == null) return;
        long bounded = transfer.total > 0 ? Math.min(bytes, transfer.total) : bytes;
        long delta = Math.max(0, bounded - transfer.bytes);
        transfer.bytes += delta;
        if (transfer.receiving) { received += delta; lifetimeReceived += delta; }
        else { sent += delta; lifetimeSent += delta; }
        if (delta > 0 && SystemClock.elapsedRealtime() - lastPersisted >= 1000) persist();
    }

    public static synchronized void finish(long id, boolean success) {
        Transfer transfer = active.get(id);
        if (transfer == null) return;
        if (success && transfer.total > 0) progress(id, transfer.total);
        active.remove(id);
        persist();
        if (success) completedAt = SystemClock.elapsedRealtime();
    }

    public static synchronized Snapshot snapshot() {
        long bytes = 0, total = 0;
        boolean unknown = false;
        String name = "";
        for (Transfer transfer : active.values()) {
            bytes += transfer.bytes;
            total += transfer.total;
            unknown |= transfer.total <= 0;
            if (name.isEmpty()) name = (transfer.receiving ? "Receiving " : "Sending ") + transfer.name;
        }
        return new Snapshot(sent, received, active.size(), bytes, total, unknown, name, completedAt);
    }

    public static synchronized void reset() {
        persist();
        active.clear(); sent = 0; received = 0; completedAt = 0;
    }

    private static final class Transfer {
        final boolean receiving;
        final long total;
        final String name;
        long bytes;
        Transfer(boolean receiving, long total, String name) {
            this.receiving = receiving; this.total = Math.max(0, total); this.name = name;
        }
    }

    public static final class Snapshot {
        public final long sent, received, bytes, total, completedAt;
        public final int activeCount;
        public final boolean unknownTotal;
        public final String name;
        Snapshot(long sent, long received, int activeCount, long bytes, long total,
                 boolean unknownTotal, String name, long completedAt) {
            this.sent = sent; this.received = received; this.activeCount = activeCount;
            this.bytes = bytes; this.total = total; this.unknownTotal = unknownTotal;
            this.name = name; this.completedAt = completedAt;
        }
    }
}
