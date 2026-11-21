package com.android.launcher3.minusone;

import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.MotionEvent;
import android.view.SurfaceControlViewHost.SurfacePackage;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.RequiresApi;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.LauncherState;
import com.android.systemui.plugins.shared.LauncherOverlayManager;

import java.io.PrintWriter;
import java.util.UUID;

/** Owns the local gesture and the embedded surface for one Launcher window. */
@RequiresApi(30)
public final class MinusOneOverlay implements LauncherOverlayManager,
        LauncherOverlayManager.LauncherOverlayTouchProxy, SurfaceHolder.Callback,
        SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String PREFERENCE = "pref_minus_one";
    private final Launcher launcher;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable retry = this::connect;
    private SurfaceView surface;
    private Connection connection;
    private LauncherOverlayCallbacks callbacks;
    private ValueAnimator animator;
    private boolean started;
    private boolean openingGesture;
    private boolean closingGesture;
    private float progress;
    private float openingStart;
    private float closingStart;
    private float flingVelocity;

    public MinusOneOverlay(Launcher launcher) {
        this.launcher = launcher;
        prefs = LauncherPrefs.getPrefs(launcher);
        prefs.registerOnSharedPreferenceChangeListener(this);
        launcher.getWorkspace().setMinusOneEnabled(enabled());
    }

    private boolean enabled() {
        return prefs.getBoolean(PREFERENCE, false);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        if (key == null || PREFERENCE.equals(key)) {
            launcher.getWorkspace().setMinusOneEnabled(enabled());
            updateSurface();
        }
    }

    @Override
    public void onActivityStarted() {
        started = true;
        updateSurface();
    }

    @Override
    public void onActivityStopped() {
        started = false;
        updateSurface();
    }

    @Override
    public void onActivityDestroyed() {
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        onActivityStopped();
    }

    private void updateSurface() {
        if (started && enabled()) {
            if (surface != null) return;
            surface = new SurfaceView(launcher);
            surface.setZOrderOnTop(true); // Embedded views need an input-receiving surface.
            // Keep producing host frames after DragLayer fades out. Android 11 applies
            // SurfaceView position changes when the corresponding host frame is presented.
            surface.setBackgroundColor(OverviewPage.BACKGROUND);
            surface.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            surface.getHolder().addCallback(this);
            surface.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> positionSurface());
            surface.setTranslationX(-direction() * launcher.getResources().getDisplayMetrics().widthPixels);
            launcher.getRootView().addView(surface, 0);
        } else if (surface != null) {
            disconnect();
            surface.getHolder().removeCallback(this);
            launcher.getRootView().removeView(surface);
            surface = null;
        }
    }

    @Override public void surfaceCreated(SurfaceHolder holder) { }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (connection == null) connect();
        else connection.requestPage();
    }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        disconnect();
    }

    private void connect() {
        if (surface == null || !started || !enabled() || connection != null
                || !surface.getHolder().getSurface().isValid() || surface.getHostToken() == null) return;
        Connection client = new Connection();
        // A separate binding per surface keeps old resize/release messages out of new sessions.
        Intent intent = new Intent(launcher, MinusOneService.class)
                .setIdentifier(UUID.randomUUID().toString());
        if (launcher.bindService(intent, client, Context.BIND_AUTO_CREATE)) {
            connection = client;
        } else {
            handler.postDelayed(retry, 1000);
        }
    }

    private void disconnect() {
        handler.removeCallbacks(retry);
        clearPage();
        if (connection != null) {
            launcher.unbindService(connection);
            connection = null;
        }
    }

    private void clearPage() {
        hideOverlay(0);
        launcher.setLauncherOverlay(null);
        callbacks = null;
    }

    private final class Connection implements ServiceConnection {
        Messenger service;
        final Messenger replies = new Messenger(new Handler(Looper.getMainLooper(), this::receive));

        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (connection != this) return;
            service = new Messenger(binder);
            requestPage();
        }

        void requestPage() {
            if (service == null || surface.getWidth() == 0 || surface.getHeight() == 0) return;
            Message msg = Message.obtain(null, MinusOneService.LAYOUT, surface.getWidth(), surface.getHeight());
            msg.getData().putBinder("host", surface.getHostToken());
            msg.getData().putInt("display", surface.getDisplay().getDisplayId());
            msg.replyTo = replies;
            try {
                service.send(msg);
            } catch (RemoteException e) {
                onBindingDied(null);
            }
        }

        boolean receive(Message msg) {
            boolean current = connection == this && service != null && surface != null;
            if (msg.what == MinusOneService.READY) {
                SurfacePackage page = msg.getData().getParcelable("page");
                if (current && page != null) {
                    surface.setChildSurfacePackage(page); // Transfers ownership to SurfaceView.
                    launcher.setLauncherOverlay(MinusOneOverlay.this);
                } else if (page != null) {
                    page.release();
                }
            } else if (current) {
                switch (msg.what) {
                    case MinusOneService.BEGIN_CLOSE:
                        if (progress > 0) {
                            stopAnimation();
                            closingGesture = true;
                            closingStart = progress;
                        }
                        break;
                    case MinusOneService.DRAG_CLOSE:
                        if (closingGesture) setProgress(closingStart - Float.intBitsToFloat(msg.arg1));
                        break;
                    case MinusOneService.END_CLOSE:
                        if (closingGesture) {
                            closingGesture = false;
                            settle(msg.arg2 != 0 || shouldOpen(Float.intBitsToFloat(msg.arg1)), 220);
                        }
                        break;
                    case MinusOneService.DISMISS:
                        hideOverlay(true);
                        break;
                }
            }
            return true;
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            if (connection != this) return;
            service = null;
            clearPage(); // Android reconnects a live binding after the service process exits.
        }

        @Override public void onBindingDied(ComponentName name) {
            if (connection != this) return;
            disconnect();
            handler.postDelayed(retry, 500);
        }
    }

    private int direction() {
        return launcher.getRootView().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? -1 : 1;
    }

    private void positionSurface() {
        if (surface != null) {
            surface.setTranslationX(direction() * (progress - 1) * launcher.getRootView().getWidth());
        }
    }

    @Override public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "MinusOne enabled=" + enabled() + " ready=" + (callbacks != null)
                + " progress=" + progress + " offset=" + (surface == null ? 0 : surface.getTranslationX())
                + " page=" + launcher.getWorkspace().getCurrentPage()
                + " pageCount=" + launcher.getWorkspace().getPageCount());
    }

    private void setProgress(float value) {
        progress = Math.max(0, Math.min(value, 1));
        positionSurface();
        if (surface != null) {
            surface.setImportantForAccessibility(progress == 1 ? View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        if (callbacks != null) callbacks.onOverlayScrollChanged(progress);
    }

    @Override public void setOverlayCallbacks(LauncherOverlayCallbacks callbacks) {
        this.callbacks = callbacks;
    }

    @Override public void onFlingVelocity(float velocity) {
        flingVelocity = velocity * direction();
    }

    @Override public void onOverlayMotionEvent(MotionEvent event, float distance) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                openingGesture = launcher.isInState(LauncherState.NORMAL)
                        && !launcher.getDragController().isDragging()
                        && AbstractFloatingView.getTopOpenView(launcher) == null;
                if (openingGesture) {
                    stopAnimation();
                    openingStart = progress;
                    flingVelocity = 0;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (openingGesture) setProgress(openingStart + distance);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (openingGesture) {
                    openingGesture = false;
                    settle(event.getActionMasked() != MotionEvent.ACTION_CANCEL && shouldOpen(flingVelocity), 220);
                }
                break;
        }
    }

    private boolean shouldOpen(float velocity) {
        float threshold = 600 * launcher.getResources().getDisplayMetrics().density;
        return Math.abs(velocity) > threshold ? velocity > 0 : progress >= 0.5f;
    }

    @Override public void hideOverlay(int duration) {
        openingGesture = false;
        closingGesture = false;
        settle(false, duration);
    }

    private void stopAnimation() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    private void settle(boolean open, int duration) {
        stopAnimation();
        float target = open ? 1 : 0;
        if (duration <= 0 || progress == target) {
            setProgress(target);
            return;
        }
        animator = ValueAnimator.ofFloat(progress, target);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.setDuration(duration);
        animator.addUpdateListener(a -> setProgress((float) a.getAnimatedValue()));
        animator.start();
    }
}
