package com.example.jawapowerwidget;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/**
 * Service ini menjaga widget tetap ter-update setiap 20 detik SELAMA layar
 * menyala dan tidak dalam keadaan terkunci. Update langsung dipicu begitu
 * layar dinyalakan (atau dibuka kuncinya), dan berhenti total saat layar
 * mati atau terkunci, supaya tidak boros baterai/kuota saat HP tidak dipakai.
 */
public class JawaPowerUpdateService extends Service {

    private static final String CHANNEL_ID = "jawapower_update_channel";
    private static final int NOTIF_ID = 1001;
    private static final long UPDATE_INTERVAL_MS = 20000; // 20 detik

    private Handler handler;
    private Runnable updateRunnable;
    private BroadcastReceiver screenReceiver;
    private boolean loopRunning = false;

    public static void start(Context context) {
        Intent intent = new Intent(context, JawaPowerUpdateService.class);
        context.startForegroundService(intent);
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, JawaPowerUpdateService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());

        updateRunnable = new Runnable() {
            @Override
            public void run() {
                triggerWidgetUpdate();
                if (loopRunning) {
                    handler.postDelayed(this, UPDATE_INTERVAL_MS);
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    // layar mati -> berhenti total
                    stopLoop();
                } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    // layar baru nyala -> update langsung kalau tidak terkunci
                    if (!isDeviceLocked()) {
                        startLoop();
                    }
                } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                    // kunci layar baru dibuka -> update langsung
                    startLoop();
                }
            }
        };
        registerReceiver(screenReceiver, filter);

        // Cek kondisi saat service pertama kali dibuat
        if (isScreenOn() && !isDeviceLocked()) {
            startLoop();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopLoop();
        if (screenReceiver != null) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startLoop() {
        if (loopRunning) return;
        loopRunning = true;
        handler.removeCallbacks(updateRunnable);
        handler.post(updateRunnable); // update langsung, lalu lanjut tiap 20 detik
    }

    private void stopLoop() {
        loopRunning = false;
        handler.removeCallbacks(updateRunnable);
    }

    private void triggerWidgetUpdate() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        int[] ids = manager.getAppWidgetIds(new ComponentName(this, JawaPowerWidgetProvider.class));
        if (ids != null && ids.length > 0) {
            JawaPowerWidgetProvider.refreshWidgets(this, manager, ids);
        }
    }

    private boolean isScreenOn() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isInteractive();
    }

    private boolean isDeviceLocked() {
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        return km != null && km.isKeyguardLocked();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Jawa Power Update", NotificationManager.IMPORTANCE_MIN);
        channel.setShowBadge(false);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Jawa Power")
                .setContentText("Memantau data langsung")
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_MIN)
                .build();
    }
}
