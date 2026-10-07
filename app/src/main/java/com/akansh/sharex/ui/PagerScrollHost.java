package com.akansh.sharex.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/** Lets a horizontal filter strip scroll before handing an edge gesture to the page pager. */
public class PagerScrollHost extends FrameLayout {
    private final int touchSlop;
    private float startX, startY;

    public PagerScrollHost(Context context, AttributeSet attributes) {
        super(context, attributes);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if (getChildCount() == 0) return super.onInterceptTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            startX = event.getX();
            startY = event.getY();
            getParent().requestDisallowInterceptTouchEvent(true);
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float dx = event.getX() - startX;
            float dy = event.getY() - startY;
            if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                View child = getChildAt(0);
                boolean scrollingStrip = Math.abs(dx) > Math.abs(dy)
                        && child.canScrollHorizontally(dx > 0 ? -1 : 1);
                getParent().requestDisallowInterceptTouchEvent(scrollingStrip);
            }
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        return super.onInterceptTouchEvent(event);
    }
}
