package com.akansh.fileserversuit.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.content.res.ResourcesCompat;
import com.akansh.fileserversuit.R;

/** Animated service status, distinct from the byte-based transfer progress indicator. */
public final class SharingStatusView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final Path shield = new Path();
    private ValueAnimator orbit;
    private boolean running;
    private boolean privateMode;
    private float phase;

    public SharingStatusView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setTypeface(ResourcesCompat.getFont(context, R.font.google_sans));
        setContentDescription("Sharing disabled");
    }

    public void setRunning(boolean enabled) {
        running = enabled;
        updateContentDescription();
        updateAnimation(); invalidate();
    }

    public void setPrivateMode(boolean enabled) {
        if (privateMode == enabled) return;
        privateMode = enabled;
        updateContentDescription();
        invalidate();
    }

    private void updateContentDescription() {
        setContentDescription(running
                ? (privateMode ? "Private sharing enabled" : "Sharing enabled")
                : "Sharing disabled");
    }

    private void updateAnimation() {
        if (!running || !isShown() || getWindowVisibility() != VISIBLE || !ValueAnimator.areAnimatorsEnabled()) {
            if (orbit != null) { orbit.cancel(); orbit = null; }
            return;
        }
        if (orbit != null) return;
        orbit = ValueAnimator.ofFloat(0, 360);
        orbit.setDuration(3200);
        orbit.setInterpolator(new android.view.animation.LinearInterpolator());
        orbit.setRepeatCount(ValueAnimator.INFINITE);
        orbit.addUpdateListener(animation -> { phase = (float) animation.getAnimatedValue(); invalidate(); });
        orbit.start();
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); updateAnimation(); }
    @Override protected void onDetachedFromWindow() {
        if (orbit != null) { orbit.cancel(); orbit = null; }
        super.onDetachedFromWindow();
    }
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); updateAnimation(); }
    @Override protected void onVisibilityChanged(View view, int visibility) { super.onVisibilityChanged(view, visibility); updateAnimation(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight()), cx = getWidth() / 2f, cy = getHeight() / 2f;
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(size * 0.025f);
        paint.setColor(running ? 0x304ADE80 : 0x40676770);
        canvas.drawCircle(cx, cy, size * 0.43f, paint);
        if (!running) {
            paint.setStyle(Paint.Style.FILL); paint.setColor(0xFFD0D0D5);
            paint.setTextSize(size * 0.18f); paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("OFF", cx, cy - (paint.ascent() + paint.descent()) / 2, paint);
            return;
        }
        bounds.set(cx - size * 0.43f, cy - size * 0.43f, cx + size * 0.43f, cy + size * 0.43f);
        paint.setStrokeWidth(size * 0.045f); paint.setStrokeCap(Paint.Cap.ROUND);
        int[] colors = {0xFF4ADE80, 0xFF85E8BC, 0xFFD1FAE5};
        for (int i = 0; i < 3; i++) {
            paint.setColor(colors[i]); canvas.drawArc(bounds, phase + i * 120, 82, false, paint);
        }
        paint.setStrokeCap(Paint.Cap.BUTT); paint.setStrokeWidth(size * 0.025f); paint.setColor(0xFFE7FFF1);
        if (privateMode) {
            shield.reset();
            shield.moveTo(cx, cy - size * 0.23f);
            shield.quadTo(cx + size * 0.10f, cy - size * 0.17f, cx + size * 0.20f, cy - size * 0.16f);
            shield.lineTo(cx + size * 0.20f, cy - size * 0.02f);
            shield.cubicTo(cx + size * 0.20f, cy + size * 0.12f,
                    cx + size * 0.10f, cy + size * 0.20f, cx, cy + size * 0.25f);
            shield.cubicTo(cx - size * 0.10f, cy + size * 0.20f,
                    cx - size * 0.20f, cy + size * 0.12f, cx - size * 0.20f, cy - size * 0.02f);
            shield.lineTo(cx - size * 0.20f, cy - size * 0.16f);
            shield.quadTo(cx - size * 0.10f, cy - size * 0.17f, cx, cy - size * 0.23f);
            shield.close();
            canvas.drawPath(shield, paint);
            return;
        }
        for (int row = 0; row < 2; row++) {
            float top = cy - size * 0.19f + row * size * 0.21f;
            bounds.set(cx - size * 0.21f, top, cx + size * 0.21f, top + size * 0.16f);
            canvas.drawRoundRect(bounds, size * 0.035f, size * 0.035f, paint);
            paint.setStyle(Paint.Style.FILL); canvas.drawCircle(cx + size * 0.13f, top + size * 0.08f, size * 0.02f, paint);
            paint.setStyle(Paint.Style.STROKE);
        }
    }
}
