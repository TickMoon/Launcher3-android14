package com.android.launcher3.minusone;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import com.android.launcher3.R;

/** Interactive content owned entirely by the remote process. */
final class OverviewPage extends ScrollView {
    static final int BACKGROUND = 0xfff3f5f1;
    private final Messenger launcher;
    private final int touchSlop;
    private VelocityTracker velocity;
    private float downX, downY;
    private boolean closing, vertical;
    private int taps;

    OverviewPage(Context context, Messenger launcher) {
        super(context);
        this.launcher = launcher;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setFillViewport(true);
        setBackgroundColor(BACKGROUND);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(36), dp(24), dp(28));
        addView(content);

        TextView title = new TextView(context);
        title.setText(R.string.minus_one_title);
        title.setTextSize(34);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(0xff18392e);
        content.addView(title);

        TextClock date = new TextClock(context);
        String pattern = DateFormat.getBestDateTimePattern(
                getResources().getConfiguration().getLocales().get(0), "MMMMdEEEE");
        date.setFormat12Hour(pattern);
        date.setFormat24Hour(pattern);
        date.setTextSize(18);
        date.setTextColor(0xff496659);
        content.addView(date);

        Button counter = new Button(context);
        counter.setText(getResources().getString(R.string.minus_one_count, taps));
        counter.setTextColor(0xffffffff);
        counter.setAllCaps(false);
        counter.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff245541));
        counter.setOnClickListener(v ->
                counter.setText(getResources().getString(R.string.minus_one_count, ++taps)));
        addRow(content, counter, 24);

        for (String idea : getResources().getStringArray(R.array.minus_one_ideas)) {
            TextView card = new TextView(context);
            card.setText(idea);
            card.setTextSize(18);
            card.setTextColor(0xff243c32);
            card.setGravity(android.view.Gravity.CENTER_VERTICAL);
            card.setMinHeight(dp(96));
            card.setPadding(dp(20), dp(18), dp(20), dp(18));
            GradientDrawable background = new GradientDrawable();
            background.setColor(0xffffffff);
            background.setCornerRadius(dp(18));
            card.setBackground(background);
            addRow(content, card, 12);
        }
    }

    private void addRow(LinearLayout content, View view, int margin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(margin);
        content.addView(view, lp);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void send(int action, float value, boolean cancel) {
        try {
            launcher.send(Message.obtain(null, action, Float.floatToIntBits(value), cancel ? 1 : 0));
        } catch (RemoteException ignored) {
            // The binding and this view are released when the launcher window goes away.
        }
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            recycleVelocity();
            velocity = VelocityTracker.obtain();
            downX = event.getRawX();
            downY = event.getRawY();
            closing = false;
            vertical = false;
        }
        if (velocity == null) return super.dispatchTouchEvent(event);
        // Track in display coordinates while Launcher translates the embedded surface.
        MotionEvent screenEvent = MotionEvent.obtain(event);
        screenEvent.offsetLocation(event.getRawX() - event.getX(), event.getRawY() - event.getY());
        velocity.addMovement(screenEvent);
        screenEvent.recycle();
        int direction = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? -1 : 1;
        float dx = (downX - event.getRawX()) * direction;
        float dy = Math.abs(event.getRawY() - downY);
        if (action == MotionEvent.ACTION_MOVE && !closing && !vertical) {
            if (dy > touchSlop && dy >= Math.abs(dx)) vertical = true;
            if (dx > touchSlop && dx > dy) {
                closing = true;
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);
                cancel.recycle();
                send(MinusOneService.BEGIN_CLOSE, 0, false);
            }
        }
        if (closing && action == MotionEvent.ACTION_MOVE) {
            send(MinusOneService.DRAG_CLOSE, dx / Math.max(1, getWidth()), false);
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (closing) {
                velocity.computeCurrentVelocity(1000);
                send(MinusOneService.END_CLOSE, direction * velocity.getXVelocity(),
                        action == MotionEvent.ACTION_CANCEL);
            }
            recycleVelocity();
        }
        return closing || super.dispatchTouchEvent(event);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                send(MinusOneService.DISMISS, 0, false);
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void recycleVelocity() {
        if (velocity != null) velocity.recycle();
        velocity = null;
    }

    @Override protected void onDetachedFromWindow() {
        recycleVelocity();
        super.onDetachedFromWindow();
    }
}
