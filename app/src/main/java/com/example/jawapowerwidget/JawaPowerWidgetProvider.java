package com.example.jawapowerwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JawaPowerWidgetProvider extends AppWidgetProvider {

    // URL sumber data (bisa diganti jika berubah)
    private static final String DATA_URL =
            "https://api.bzpublish.com/clients/2/jp_live_data/?language=1";

    private static final String ACTION_REFRESH =
            "com.example.jawapowerwidget.ACTION_REFRESH";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        refreshWidgets(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int[] ids = manager.getAppWidgetIds(
                    new ComponentName(context, JawaPowerWidgetProvider.class));
            refreshWidgets(context, manager, ids);
        }
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        // Widget pertama kali dipasang -> nyalakan service pemantau layar
        JawaPowerUpdateService.start(context);
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        // Widget terakhir dihapus -> matikan service
        JawaPowerUpdateService.stop(context);
    }

    /** Dipanggil dari provider maupun dari JawaPowerUpdateService untuk memicu refresh data. */
    static void refreshWidgets(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        if (appWidgetIds == null) return;
        for (int id : appWidgetIds) {
            updateWidget(context, manager, id);
        }
    }

    private static void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

        Intent refreshIntent = new Intent(context, JawaPowerWidgetProvider.class);
        refreshIntent.setAction(ACTION_REFRESH);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                widgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);
        manager.updateAppWidget(widgetId, views);

        executor.execute(() -> fetchAndUpdate(context, manager, widgetId, pendingIntent));
    }

    private static void fetchAndUpdate(Context context, AppWidgetManager manager, int widgetId, PendingIntent pendingIntent) {
        String unit5 = "-";
        String unit6 = "-";
        String timestamp = "";

        try {
            URL url = new URL(DATA_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            conn.disconnect();

            JSONObject json = new JSONObject(sb.toString());
            unit5 = json.optString("unit5", "-");
            unit6 = json.optString("unit6", "-");
            timestamp = json.optString("timestamp", "");
        } catch (Exception e) {
            unit5 = "Error";
            unit6 = "gagal ambil data";
            timestamp = e.getMessage() != null ? e.getMessage() : "";
        }

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
        views.setTextViewText(R.id.widget_unit5, "Unit 50: " + unit5);
        views.setTextViewText(R.id.widget_unit6, "Unit 60: " + unit6);
        views.setTextViewText(R.id.widget_time, timestamp);
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);
        manager.updateAppWidget(widgetId, views);
    }
}
