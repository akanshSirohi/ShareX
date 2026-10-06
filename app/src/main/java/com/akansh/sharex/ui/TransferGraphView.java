package com.akansh.sharex.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.animation.ValueAnimator;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import com.akansh.sharex.R;

/** A rolling 40-sample chart. Values are bytes per second, sampled from actual transfers. */
public final class TransferGraphView extends View {
    private final double[] sent = new double[40], received = new double[40];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path area = new Path();
    private final float density, textDensity;
    private final DashPathEffect gridDash, receiveDash;
    private ValueAnimator highlight;
    private float highlightAmount;
    private int count;

    public TransferGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        textDensity = getResources().getDisplayMetrics().scaledDensity;
        paint.setTypeface(ResourcesCompat.getFont(context, R.font.google_sans));
        gridDash = new DashPathEffect(new float[]{2 * density, 4 * density}, 0);
        receiveDash = new DashPathEffect(new float[]{5 * density, 4 * density}, 0);
    }

    public void clearSamples() {
        java.util.Arrays.fill(sent, 0);
        java.util.Arrays.fill(received, 0);
        count = 0;
        cancelHighlight();
        invalidate();
    }

    public void addSample(double sending, double receiving) {
        System.arraycopy(sent, 1, sent, 0, sent.length - 1);
        System.arraycopy(received, 1, received, 0, received.length - 1);
        sent[sent.length - 1] = Double.isFinite(sending) ? Math.max(0, sending) : 0;
        received[received.length - 1] = Double.isFinite(receiving) ? Math.max(0, receiving) : 0;
        count = Math.min(count + 1, sent.length);
        cancelHighlight();
        if (isShown() && (sending > 0 || receiving > 0) && ValueAnimator.areAnimatorsEnabled()) {
            highlight = ValueAnimator.ofFloat(1, 0);
            highlight.setDuration(450);
            highlight.addUpdateListener(animation -> {
                highlightAmount = (float) animation.getAnimatedValue();
                invalidate();
            });
            highlight.start();
        }
        invalidate();
    }

    private void cancelHighlight() {
        if (highlight != null) { highlight.cancel(); highlight = null; }
        highlightAmount = 0;
    }

    @Override protected void onDetachedFromWindow() { cancelHighlight(); super.onDetachedFromWindow(); }
    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (!isShown()) cancelHighlight();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        double peak = 0;
        for (int i = 0; i < sent.length; i++) peak = Math.max(peak, Math.max(sent[i], received[i]));
        double scale = 1024 * Math.pow(2, Math.ceil(Math.log(Math.max(1024, peak) / 1024) / Math.log(2)));
        int muted = ContextCompat.getColor(getContext(), R.color.txt_color_secondary);
        paint.setShader(null); paint.setPathEffect(null); paint.setAlpha(255);
        paint.setStyle(Paint.Style.FILL); paint.setTextSize(10 * textDensity); paint.setColor(muted);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("LAST 40 SECONDS", 0, 14 * density, paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("PEAK " + rate(peak) + "/s", getWidth(), 14 * density, paint);
        float left = Math.max(42 * density, paint.measureText(rate(scale)) + 10 * density);
        float right = getWidth() - 6 * density, top = 36 * density, bottom = getHeight() - 26 * density;
        if (right <= left || bottom <= top) return;
        int grid = ContextCompat.getColor(getContext(), R.color.outline_soft);
        for (int i = 0; i <= 2; i++) {
            float y = top + (bottom - top) * i / 2;
            paint.setColor(grid); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(density);
            paint.setPathEffect(gridDash);
            canvas.drawLine(left, y, right, y, paint);
            paint.setPathEffect(null); paint.setStyle(Paint.Style.FILL); paint.setColor(muted);
            canvas.drawText(i == 2 ? "0" : rate(scale * (2 - i) / 2), left - 8 * density,
                    y - (paint.ascent() + paint.descent()) / 2, paint);
        }
        paint.setStyle(Paint.Style.STROKE); paint.setColor(grid); paint.setAlpha(100); paint.setPathEffect(gridDash);
        for (int i = 0; i <= 4; i++) {
            float x = left + (right - left) * i / 4;
            canvas.drawLine(x, top, x, bottom, paint);
        }
        paint.setAlpha(255); paint.setPathEffect(null); paint.setStyle(Paint.Style.FILL); paint.setColor(muted);
        paint.setTextAlign(Paint.Align.LEFT); canvas.drawText("40s ago", left, getHeight() - 5 * density, paint);
        paint.setTextAlign(Paint.Align.CENTER); canvas.drawText("20s", (left + right) / 2, getHeight() - 5 * density, paint);
        paint.setTextAlign(Paint.Align.RIGHT); canvas.drawText("Now", right, getHeight() - 5 * density, paint);
        if (peak == 0) {
            paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(12 * textDensity);
            canvas.drawText("Waiting for a transfer", (left + right) / 2, (top + bottom) / 2, paint);
            return;
        }
        int save = canvas.save();
        canvas.clipRect(left - 2 * density, top - 5 * density, right + 6 * density, bottom + 2 * density);
        drawSeries(canvas, received, scale, left, right, top, bottom,
                ContextCompat.getColor(getContext(), R.color.graph_receive), true);
        drawSeries(canvas, sent, scale, left, right, top, bottom,
                ContextCompat.getColor(getContext(), R.color.accent_blue), false);
        canvas.restoreToCount(save);
    }

    private void drawSeries(Canvas canvas, double[] samples, double scale, float left, float right,
                            float top, float bottom, int color, boolean dashed) {
        int first = samples.length - count;
        if (count == 0) return;
        path.reset();
        float firstX = left + (right - left) * first / (samples.length - 1);
        float lastY = bottom;
        for (int i = first; i < samples.length; i++) {
            float x = left + (right - left) * i / (samples.length - 1);
            float y = bottom - (float) (samples[i] / scale) * (bottom - top);
            if (i == first) path.moveTo(x, y); else path.lineTo(x, y);
            lastY = y;
        }
        area.set(path); area.lineTo(right, bottom); area.lineTo(firstX, bottom); area.close();
        paint.setStyle(Paint.Style.FILL); paint.setPathEffect(null);
        paint.setShader(new LinearGradient(0, top, 0, bottom, (color & 0x00FFFFFF) | 0x40000000,
                color & 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawPath(area, paint);
        paint.setShader(null); paint.setStyle(Paint.Style.STROKE);
        paint.setColor(color);
        paint.setStrokeWidth(2.2f * density); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setPathEffect(dashed ? receiveDash : null);
        canvas.drawPath(path, paint);
        paint.setPathEffect(null); paint.setStyle(Paint.Style.FILL);
        if (samples[samples.length - 1] > 0) {
            paint.setAlpha((int) (45 + highlightAmount * 40));
            canvas.drawCircle(right, lastY, (6 + highlightAmount * 3) * density, paint);
            paint.setAlpha(255); canvas.drawCircle(right, lastY, 3 * density, paint);
        }
    }

    private String rate(double bytes) {
        if (bytes >= 1024 * 1024 * 1024) return String.format(java.util.Locale.getDefault(), "%.1f GiB", bytes / (1024d * 1024 * 1024));
        if (bytes >= 1024 * 1024) return String.format(java.util.Locale.getDefault(), "%.1f MiB", bytes / (1024d * 1024));
        if (bytes >= 1024) return String.format(java.util.Locale.getDefault(), "%.0f KiB", bytes / 1024d);
        return String.format(java.util.Locale.getDefault(), "%.0f B", bytes);
    }
}
