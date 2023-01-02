package com.akansh.fileserversuit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class PointsOverlayLayout extends View {
    PointF[] points;
    private Paint paint;

    public PointsOverlayLayout(Context context) {
        super(context);
        init();
    }

    public PointsOverlayLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PointsOverlayLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(Color.parseColor("#ed3237"));
        paint.setStyle(Paint.Style.FILL);
    }

    public void setPoints(PointF[] points) {
        this.points = points;
        invalidate();
    }

    @Override public void draw(Canvas canvas) {
        super.draw(canvas);
        if (points != null) {
            for (PointF pointF : points) {
                canvas.drawCircle(pointF.x, pointF.y, 10, paint);
            }
        }
    }
}
