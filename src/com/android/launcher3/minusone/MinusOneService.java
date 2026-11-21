package com.android.launcher3.minusone;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceControlViewHost.SurfacePackage;

import androidx.annotation.RequiresApi;

import java.util.HashMap;

/** Each binding owns its remote view tree until the host window disconnects. */
@RequiresApi(30)
public final class MinusOneService extends Service {
    static final int LAYOUT = 1, READY = 2, BEGIN_CLOSE = 3, DRAG_CLOSE = 4,
            END_CLOSE = 5, DISMISS = 6;
    private final HashMap<String, Session> sessions = new HashMap<>();

    @Override public IBinder onBind(Intent intent) {
        Session session = new Session();
        sessions.put(intent.getIdentifier(), session);
        return new Messenger(new Handler(Looper.getMainLooper(), session::layout)).getBinder();
    }

    @Override public boolean onUnbind(Intent intent) {
        Session session = sessions.remove(intent.getIdentifier());
        if (session != null) session.release();
        return false;
    }

    @Override public void onDestroy() {
        for (Session session : sessions.values()) session.release();
        sessions.clear();
        super.onDestroy();
    }

    private final class Session {
        private SurfaceControlViewHost host;
        private boolean released;

        boolean layout(Message request) {
            if (released || request.what != LAYOUT || request.arg1 <= 0 || request.arg2 <= 0) return true;
            if (host != null) {
                host.relayout(request.arg1, request.arg2);
                return true;
            }
            Bundle data = request.getData();
            Display display = getSystemService(DisplayManager.class).getDisplay(data.getInt("display"));
            if (display == null || data.getBinder("host") == null) return true;
            // The Launcher token already identifies the embedding window. Creating a separate
            // TYPE_APPLICATION window context requires privileged access on Android 11.
            Context window = createDisplayContext(display);
            Context themed = new ContextThemeWrapper(window, android.R.style.Theme_Material_Light_NoActionBar);
            host = new SurfaceControlViewHost(window, display, data.getBinder("host"));
            host.setView(new OverviewPage(themed, request.replyTo), request.arg1, request.arg2);
            SurfacePackage page = host.getSurfacePackage();
            Message response = Message.obtain(null, READY);
            response.getData().putParcelable("page", page);
            try {
                request.replyTo.send(response);
            } catch (RemoteException e) {
                Log.d("MinusOneService", "Launcher window disconnected");
                release();
            } finally {
                if (page != null) page.release();
            }
            return true;
        }

        void release() {
            released = true;
            if (host != null) host.release();
            host = null;
        }
    }
}
