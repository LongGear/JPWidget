package com.example.jawapowerwidget;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * Menyalakan kembali JawaPowerUpdateService setelah HP restart,
 * tapi hanya kalau widget-nya memang masih terpasang di home screen.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, JawaPowerWidgetProvider.class));
        if (ids != null && ids.length > 0) {
            JawaPowerUpdateService.start(context);
        }
    }
}
